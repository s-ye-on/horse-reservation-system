# ADR-024: 정규 시간표 운영 종료와 미래 TimeSlot 정원 동기화

## 상태

Accepted, 2026-09-15

기존 TEMPLATE TimeSlot rollout 결정 수정, 2026-09-19

ADR-015의 "이미 materialize된 TimeSlot 정원은 Template 변경으로 갱신하지 않는다"는 결정을
부분 대체한다. materialized occurrence, 독립 마감 원인, 설정 version과 예약 비자동 취소 결정은
계속 ADR-015를 따른다.

## 배경

관리자에게 정규 시간표를 더 이상 운영하지 않는 기능은 필요하지만
`RegularScheduleTemplate`, 과거 `TimeSlotCapacity`, Reservation과 감사 이력은 FK와 운영 사실을
보존해야 한다. 기존 `active=false`와 `templateInactiveClosed` 동기화는 Template 행과 미래
occurrence를 삭제하지 않고 신규 예약만 차단할 수 있다.

같은 `(dayOfWeek, startTime)`은 DB에서 유일하며 inactive Template도 이 key를 점유한다. 계절성
재운영 때 새 행을 INSERT하면 제약과 충돌하고 과거 occurrence의 Template 연결도 분리된다.

또한 Horse는 약 3개월의 occurrence를 미리 만든다. 기존처럼 Template 정원 변경을 새 occurrence에만
적용하면 materialization horizon이 관리자 설정의 적용 시점을 결정한다. 반대로 모든 미래 occurrence를
일괄 덮어쓰면 특정 날짜에 관리자가 직접 적용한 정원 보정을 잃을 수 있다. 현재 모델에는 Template
기본값과 개별 보정을 구별하는 provenance가 없다.

## 결정

### 사용자-facing lifecycle

관리자에게 `비활성화`와 별도 `삭제`를 함께 제공하지 않고 `삭제` 하나를 제공한다. 사용자 의미는
"이 정규 시간표를 앞으로 더 이상 운영하지 않는다"이다.

내부 lifecycle은 기존 `active`만 사용한다.

- `active=true`: 현재 운영 중인 정규 시간표
- `active=false`: 현재 운영하지 않으며 사용자 관점에서 삭제된 정규 시간표

Template을 물리 삭제하거나 `DELETED`, `ARCHIVED`, `PAUSED` 같은 새 영속 상태를 추가하지 않는다.

### 삭제와 남은 예약

삭제 Command는 미래 점유 Reservation이 있어도 Template을 `active=false`로 바꾸고 설정 동기화를
시작한다. 동기화는 미래 `source=TEMPLATE` occurrence에 `templateInactiveClosed=true`를 적용하고
추가 occurrence 생성을 중단한다. 신규 Reservation Command는 설정 `SYNCING`과 닫힌 TimeSlot을
각각 검증해 신규 유입을 차단한다.

삭제는 기존 Reservation 상태, 쿠폰 hold와 결제 상태를 변경하지 않는다. 관리자가 회원에게 안내한 뒤
기존 관리자 Reservation 취소 흐름으로 개별 정리한다. 정리 대상은 별도 상태 목록이 아니라
`ReservationStatus.occupyingStatuses()`에 속하며 아직 시작하지 않은 해당 Template occurrence의
Reservation이다.

inactive Template에 미래 점유 Reservation이 있으면 관리자 화면의 `예약 정리 필요` 영역에 남은 건수와
기존 Reservation 관리 화면으로 가는 동선을 제공한다. 건수가 0이면 일반 운영 화면에서 숨기며 장기
운영 종료 목록은 만들지 않는다. 이 표시는 Template이나 Reservation에 새 상태를 저장하지 않고 현재
사실에서 파생한다.

### 같은 시간대 재운영

관리자가 inactive Template과 같은 `(dayOfWeek, startTime)`으로 정규 시간표를 생성하면 기존 ID를
재사용한다. 현재 입력한 종료 시각과 전체·원형·RidingClass별 정원으로 Template을 갱신하고
`active=true`로 전환한 뒤 같은 설정 version에서 미래 occurrence를 동기화한다. 사용자에게 내부
재사용이나 재활성화를 노출하지 않는다.

미래 점유 Reservation이 남아 있어도 재운영을 허용한다. 재운영은 Reservation 복구가 아니므로
`PENDING_ADMIN_APPROVAL`, `PENDING_PAYMENT`, `CONFIRMED`, `COMPLETED`, `CANCELLED`, `NO_SHOW`를
포함한 모든 기존 Reservation 상태를 변경하지 않는다. Template의 생성·수정·운영 종료·재운영은 기존
`ScheduleAuditLog`에 actor, reason, 이전·이후 상태와 설정 version을 구분 가능한 action으로 남긴다.

### 미래 Template occurrence 동기화

과거 occurrence와 과거 운영 사실은 변경하지 않는다. `Asia/Seoul`의 현재 시각을 기준으로 수업이 아직
시작하지 않은 `source=TEMPLATE` occurrence만 현재 Template의 운영 상태와 설정을 따른다.

`TimeSlotCapacity`에 slot-level `capacityOverridden` provenance를 추가한다.

- `false`: Template 정원을 따르며 미래 정원 동기화 대상
- `true`: 관리자가 해당 occurrence의 정원을 직접 관리했으며 Template 정원 동기화에서 제외

새 TEMPLATE occurrence는 `false`, MANUAL occurrence는 `true`로 생성한다. 관리자 개별 TimeSlot
정원 변경은 전체·원형·RidingClass별 정원을 한 Command에서 교체하고 `true`로 전환한다. Template
동기화 자체는 이 값을 `true`로 바꾸지 않는다. 현재 요구에서는 필드별 override나 별도 override
이력 state machine을 만들지 않는다.

Template 수정과 같은 key 재운영은 동일한 미래 동기화 규칙을 사용한다.

1. 대상 미래 TEMPLATE occurrence와 점유 Reservation을 잠금 순서에 따라 읽는다.
2. `capacityOverridden=false`인 모든 대상에 새 전체·원형·RidingClass별 정원을 적용할 수 있는지
   `TimeSlotCapacity.changeCapacity()`의 점유 하한으로 사전 검증한다.
3. 한 occurrence라도 전체·원형·클래스별 점유가 새 정원을 초과하면 Template 변경 전체를 거부한다.
4. 모두 유효할 때 Template 변경과 `SYNCING` 진입을 커밋하고 날짜별 짧은 트랜잭션에서 대상
   occurrence를 반영한다.

ADR-015의 날짜별 짧은 동기화 트랜잭션과 동일 version 재시도를 유지한다. 물리적으로 한 장기
트랜잭션에 묶지는 않지만 `SYNCING` 동안 신규 진입을 fail-closed로 차단하고 모든 날짜가 같은
`ScheduleDate.appliedConfigVersion`이 된 뒤에만 `ACTIVE`로 전환해 외부에는 완결된 설정만 노출한다.
TimeSlot 자체에 별도 설정 version을 추가하지 않는다. 점유 충돌 occurrence만 건너뛰거나 Template만
활성 상태로 노출하지 않는다. 충돌 응답은 영향을 받는 수업, 현재 점유와 요청 정원을 관리자가 확인할
수 있게 한다.

### 마감 원인 보존

운영 종료는 미래 TEMPLATE occurrence의 `templateInactiveClosed`만 설정하고, 재운영은 이 원인만
해제한다. Template 수정과 재운영은 `adminClosed`, `recurringHolidayClosed`, 날짜
`CLOSING/CLOSED`와 그 밖의 Template 독립 마감 원인을 변경하거나 우회하지 않는다. 따라서 한 원인이
해제되어도 다른 원인이 남은 occurrence는 계속 닫혀 있다. MANUAL occurrence는 Template 동기화
대상이 아니다.

### 기존 데이터 rollout

기존 행에는 개별 정원 보정 provenance를 복원할 감사 원장이 없다. 다만 본격적인 실운영 전 배포 DB를
read-only로 확인한 결과 미래 TEMPLATE TimeSlot은 270개, Template은 20개였으며 운영자가 보존해야 할
개별 TimeSlot 정원 보정 이력은 없었다. 확인된 capacity mismatch 5건은 모두 전체·원형 정원이 같고
`class_capacity_json`만 현재 Template과 달랐으며, 관리자 개별 보정 증거 없이 기존 비전파 정책으로
설명할 수 있다.

따라서 M34-03 rollout은 다음 계약을 따른다.

- 기존 `source=TEMPLATE` TimeSlot은 `capacityOverridden=false`로 backfill한다.
- 새로 materialize되는 TEMPLATE TimeSlot도 `false`로 생성한다.
- 관리자가 특정 TimeSlot 정원을 직접 변경한 시점에만 해당 occurrence를 `true`로 전환한다.
- Template synchronization 자체는 `capacityOverridden`을 `true`로 바꾸지 않는다.
- MANUAL TimeSlot은 이 TEMPLATE backfill 대상이 아니며 기존 `capacityOverridden=true` 계약을 따른다.
- M34-03 migration은 provenance만 이관하고 기존 정원 값을 변경하지 않는다. 실제 미래 정원 전파와
  점유 하한의 원자적 검증은 M34-04가 담당한다.

기존 TEMPLATE 행을 모두 `true`로 이관하면 확인된 270개 미래 occurrence가 동기화에서 제외되어
materialization horizon을 제품 동작에서 제거하려는 이 ADR의 목적과 충돌한다. 이 배포 단계의 확인된
데이터를 근거로 기존 TEMPLATE 행을 Template 상속 상태로 명시한다.

## 선행 구현 결함

다음 두 항목은 운영 종료·재운영 기능 전에 해결한다.

1. ADR-015는 `SYNCING` 중 회원 예약 가능 TimeSlot 조회를 차단하지만 현재
   `MemberAvailableTimeSlotsService`는 `ScheduleConfigGuard`를 검사하지 않는다. Reservation Command의
   기존 차단과 별개로 조회도 같은 `503/SCHEDULE_CONFIG_SYNC_IN_PROGRESS` 계약을 따라야 한다.
2. 현재 `ScheduleTemplateImpactRepository`의 Template 연결 OR 조건은 날짜 범위를 우회할 수 있다.
   `예약 정리 필요`의 건수·목록은 미래 범위, 해당 Template, `occupyingStatuses()`를 모두 만족하는
   Reservation만 반환하는 전용 read model을 사용해야 한다.

## 검토한 대안

### 기존 비활성화 UX를 그대로 노출

기술 동작은 재사용할 수 있지만 삭제와 비활성화의 차이를 관리자에게 설명해야 하고 같은 key 재생성
의도를 해결하지 못해 거부한다.

### 별도 soft-delete 상태

현재 운영 여부와 남은 예약은 `active`와 Reservation 사실에서 결정할 수 있다. 새 lifecycle 상태와
전이 조합을 추가할 필요가 없어 거부한다.

### Hard Delete

TimeSlot FK, Reservation·감사 이력과 계절 재운영 연결을 훼손하므로 거부한다.

### 모든 미래 occurrence 정원 덮어쓰기

provenance와 점유 하한 검증 없이 migration에서 정원 값을 직접 덮어쓰면 개별 날짜 정원과 점유
불변식을 잃으므로 거부한다. 기존 TEMPLATE 행을 `false`로 backfill하는 M34-03은 정원 값을 변경하지
않고, 후속 M34-04 동기화만 검증된 비 override 미래 occurrence에 정원을 전파한다.

### 기존 TEMPLATE occurrence를 모두 override로 보존

확인된 배포 데이터에는 보존할 개별 보정 이력이 없고, 모든 기존 TEMPLATE occurrence를 `true`로
이관하면 미래 270개 슬롯이 Template synchronization에서 제외된다. 내부 materialization 시점이 설정
적용 시점을 결정하는 기존 문제를 유지하므로 거부한다.

### 충돌 occurrence만 제외한 부분 적용

Template 값과 미래 occurrence가 숨은 방식으로 달라지므로 거부한다. 점유 하한 충돌은 변경 전체를
거부한다.

### Template 운영 시작일·종료일

계절 운영 기간은 별도 요구이며 현재 삭제·재운영에 필요하지 않아 제외한다.

## 결과

- 사용자-facing 삭제는 기존 inactive lifecycle과 동기화·감사 구조를 재사용한다.
- 과거 TimeSlot, Reservation, 쿠폰·결제와 감사 사실은 변경하지 않는다.
- 같은 key 재운영은 기존 Template ID와 FK 연속성을 보존한다.
- 미래 Template occurrence는 개별 정원 보정을 제외하고 현재 Template 설정을 즉시 따른다.
- 점유 하한, 설정 version, 독립 마감 원인과 fail-closed 예약 경계를 유지한다.
