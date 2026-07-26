# ADR-015: 정규 시간표와 날짜 운영 상태 기반 materialized occurrence

## 상태

확정, R09 날짜 휴무 workflow 및 R10 개별 휴강 workflow 구현

## 맥락

현재 `TimeSlotCapacity`는 관리자가 직접 만든 날짜·시작 시각 행과 `is_closed`만 보유한다.
미래 3개월 TimeSlot을 매번 수동으로 만들 수 없고, 정규 시간표와 정기 휴일, 날짜별
`OPEN/CLOSING/CLOSED`를 서로 다른 책임으로 관리해야 한다. 예약과 정원은 기존의 구체
TimeSlot 행을 잠금 기준으로 사용하므로 요청 시 가상 occurrence만 계산하는 구조는 현재
예약 경계와 맞지 않는다.

## 검토한 대안

### 요일별 Template 행

`day_of_week`와 `start_time`마다 한 행을 둔다. 한 요일만 비활성화하거나 정원을 바꾸기
쉽고 `(day_of_week, start_time)` UNIQUE와 관계형 조회를 그대로 사용할 수 있다. 일곱
요일과 일곱 시작 시각을 모두 사용해도 최대 49행이다.

### 복수 요일을 가진 Template

요일 bit mask, JSON 또는 별도 연결 테이블이 필요하다. 같은 시간 설정을 공유할 수 있지만
요일별 변경, 감사와 UNIQUE가 복잡해지고 현재 규모에서 줄어드는 행 수의 이점이 작다.

### 가상 occurrence

Template과 휴일을 조회 시점마다 계산하고 TimeSlot을 만들지 않는다. 저장 행은 줄지만
현재 정원, 예약, 변경, 감사와 비관적 잠금의 기준을 전부 다시 설계해야 한다.

## 결정

요일별 `RegularScheduleTemplate`과 materialized `TimeSlotCapacity` occurrence를 사용한다.
정기 휴일과 독립적으로 월요일 Template도 존재하며 날짜 `OPEN`은 월요일 정기 휴일을
우회해 해당 요일 Template 전체를 동기화한다.

### RegularScheduleTemplate

```text
id
day_of_week
start_time
end_time
total_capacity
round_arena_capacity
class_capacity_json
active
version
created_by
updated_by
created_at
updated_at
UNIQUE(day_of_week, start_time)
```

별도 CapacityProfile은 두지 않는다. 모든 정규 수업은 45분이며 `end_time > start_time`과
45분 차이를 DB와 도메인에서 검증한다. Template은 물리 삭제하지 않고 비활성화한다.

### RecurringHolidayRule

```text
id
day_of_week
effective_from
effective_to nullable
reason
active
version
created_by
updated_by
created_at
updated_at
```

같은 요일의 적용 기간이 겹치는 활성 규칙은 허용하지 않는다. 기본 월요일 규칙을
등록하되 정규 Template 자체를 삭제하거나 변경하지 않는다.

### ScheduleDate

```text
id
schedule_date unique
status: NORMAL | OPEN | CLOSING | CLOSED
resume_status nullable: NORMAL | OPEN
reason nullable
changed_by nullable
version
created_at
updated_at
```

`Asia/Seoul` 기준 오늘부터 `today.plusMonths(3)`까지 양 끝 날짜를 포함해 실제
`NORMAL` 행을 생성한다. 매일 동기화가 새 마지막 날짜를 추가한다. 행이 없음을 정상
상태로 해석하지 않으며 정합성이 필요한 Command는 같은 행을 `FOR UPDATE`로 잠근다.

과거 ScheduleDate와 회원·날짜 직렬화 행은 현재 범위에서 삭제하지 않는다. 행 수가 작고
예약·TimeSlot·감사 이력의 보존 기간이 아직 없으므로 독립 cleanup을 두지 않는다. 전체
데이터 보존 정책이 정해진 뒤 archive 또는 cleanup ADR을 별도로 작성한다.

### ScheduleConfigGuard

Template과 정기 휴일 변경을 예약 Command와 직렬화하기 위해 singleton 설정 guard를 둔다.

검토한 방식은 다음과 같다.

| 방식 | 장점 | 문제 | 결정 |
|---|---|---|---|
| 설정 변경과 3개월 전체 동기화를 한 트랜잭션으로 처리 | 외부에서 완전한 원자 변경으로 보임 | 최대 92개 ScheduleDate와 TimeSlot을 장시간 잠그고 실패 복구 단위가 너무 큼 | 제외 |
| ScheduleDate마다 PENDING/ACTIVE 상태를 관리 | 날짜별 가용성을 유지할 수 있음 | 같은 설정 version이 일부 날짜에만 적용되는 중간 상태와 API 의미가 복잡함 | 제외 |
| singleton 설정 guard와 version을 사용 | 설정 변경과 예약을 짧은 잠금으로 직렬화하고 날짜별 재시도 가능 | 동기화 중 신규 유입을 일시 차단함 | 채택 |

```text
id: 1
status: ACTIVE | SYNCING
active_version
pending_version nullable
sync_started_at nullable
sync_started_by nullable
last_completed_at nullable
last_failed_at nullable
last_failure_code nullable
last_failure_summary nullable
version
```

예약 생성, 해당 날짜로의 예약 변경, 입금 만료 복구, 관리자 수동 예약과 수동 TimeSlot
생성은 guard를 `FOR SHARE`로 잠그고 `ACTIVE`를 확인한 뒤 ScheduleDate를 잠근다.
Template 또는 정기 휴일 변경은 guard를 `FOR UPDATE`로 잠그고 `ACTIVE`일 때만 설정을
변경하며, `pending_version = active_version + 1`, `status = SYNCING`을 같은
트랜잭션에 커밋한다.

날짜별 동기화는 요청받은 pending version과 현재 guard를 확인하고 ScheduleDate를 잠근
뒤 occurrence를 반영한다. 적용한 ScheduleDate에 `applied_config_version`을 기록한다.
전체 horizon이 pending version으로 동기화된 것을 확인한 뒤 guard를 `ACTIVE`로
전환한다. 동기화 실패 시 `SYNCING`을 유지해 신규 유입을 fail-closed로 차단하고 동일
version 재실행만 허용한다. 이전 version worker는 version 불일치로 중단한다.
`SYNCING` 중 추가 Template·정기 휴일 변경은 허용하지 않는다.

설정 변경과 동기화를 한 장기 트랜잭션으로 묶지 않는다. 설정 변경 직전 예약이 guard의
공유 잠금을 먼저 얻으면 기존 설정의 정상 예약으로 커밋되고 설정 변경이 그 뒤에
진행된다. 설정 변경이 배타 잠금을 먼저 얻으면 이후 신규 유입은 `SYNCING`을 확인해
거부되므로 설정 커밋과 occurrence 반영 사이에 예약 공백이 생기지 않는다.

예약 반려, 자동 입금 만료, 자동 승인 만료와 휴무 정리 전용 취소처럼 기존 활성 예약을
안전한 종료 상태로 이동시키는 Command는 설정 `ACTIVE` 여부를 요구하지 않는다. 이
Command들은 ScheduleDate와 이후 잠금 순서를 지키되 설정 동기화 복구를 막지 않는다.

### SYNCING API 경계

`SYNCING`은 시간표 설정을 사용하는 신규 진입만 일시 차단한다.

| 기능 | SYNCING |
|---|---|
| 회원 예약 가능 TimeSlot 조회 | 차단 |
| 회원 신규 예약 | 차단 |
| 예약 변경 | 차단 |
| 관리자 수동 예약 | 차단 |
| 수동 TimeSlot 생성 | 차단 |
| 기존 예약 목록·상세 조회 | 허용 |
| 쿠폰 내역 조회 | 허용 |
| 휴무 정리 대상 예약 목록 | 허용 |
| 휴무 정리 전용 취소 | 허용 |
| 예약 반려 | 허용 |
| 자동 입금·승인 만료 | 허용 |
| 설정 동기화 상태·진행률 조회 | 허용 |
| 동일 pending version 재시도 | 허용 |

차단 응답은 `503 Service Unavailable`,
`SCHEDULE_CONFIG_SYNC_IN_PROGRESS`,
`시간표를 갱신하고 있습니다. 잠시 후 다시 시도해 주세요.`를 사용한다. Schedule은
TimeSlot 하나보다 넓은 독립 도메인이므로 기존 prefix 규칙에 `SCHEDULE_*`를 동일한
방식으로 추가한다. 안정적인 예상 완료 시간을 계산할 수 있을 때만 `Retry-After`를
제공하며 고정 추정값은 보내지 않는다.

### 장애 복구

애플리케이션 시작 복구와 주기 복구 Scheduler는 미완료 `SYNCING`을 탐지해 현재
`pending_version`만 재실행한다. `ScheduleDate.applied_config_version`이 이미 일치하는
날짜는 멱등 성공으로 건너뛰고, horizon의 모든 날짜가 일치할 때만 guard를 `ACTIVE`로
전환한다. 경과 시간이 길다는 이유로 강제 `ACTIVE`로 바꾸지 않는다.

관리자는 상태 API에서 active/pending version, 대상 날짜 수, 적용 완료 수, 시작 시각,
마지막 실패 코드와 안전한 실패 요약을 조회하고 같은 pending version을 수동 재시도할 수
있다. 실패와 설정 가능한 장기 지속 기준 초과는 pending version과 진행률을 포함한
구조화 로그로 남기며 개인정보와 stack trace는 API에 노출하지 않는다.

같은 요일에 기간이 겹치는 활성 RecurringHolidayRule은 허용하지 않는다. 등록·변경
Command는 ScheduleConfigGuard의 배타 잠금을 획득한 뒤 다음 겹침 조건을 검사한다.

```text
existing.effectiveFrom <= candidate.effectiveTo
AND candidate.effectiveFrom <= existing.effectiveTo
```

종료일 NULL은 무기한으로 처리한다. MySQL은 일반 기간 겹침을 UNIQUE로 표현할 수 없으므로
guard 직렬화와 Application 검사로 보장한다.

### TimeSlotCapacity occurrence

R01은 기존 행에 `end_time`, `source`만 먼저 추가하고 기존 행을 `MANUAL`로 이관한다.
R02에서 다음 운영 필드를 추가한다.

```text
template_id nullable
admin_closed
recurring_holiday_closed
template_inactive_closed
```

기존 `(lesson_date, start_time)` UNIQUE는 유지한다. 수동 TimeSlot도 45분이며 자정을
넘을 수 없다. 같은 날짜·시각에 수동 행이 이미 있으면 동기화는 이를 occurrence가 존재하는
것으로 간주하고 덮어쓰거나 TEMPLATE 행으로 바꾸지 않는다.

단일 `closure_origin`은 동시에 존재하는 여러 마감 원인을 보존할 수 없으므로 사용하지
않는다. 고정된 세 원인을 독립 boolean으로 저장한다. R01에서는 기존 mutable
`is_closed`와 개별 마감·재개 API를 변경하지 않고 다음 두 방식을 R02 입력으로 비교한다.

| 방식 | 장점 | 위험 |
|---|---|---|
| 세 원인의 OR인 generated column | DB 불변식의 단일 SSOT | 기존 mutable JPA 매핑과 상태 변경 메서드의 변경 범위가 커질 수 있음 |
| mutable `is_closed`와 OR CHECK | 기존 JPA 변경 범위가 작음 | 모든 쓰기 경로가 파생값을 함께 갱신해야 함 |

현재 쓰기 경로는 `TimeSlotCapacity.closed`, `changeClosedStatus`,
`AdminTimeSlotService.changeClosedStatus`, 관리자 PATCH API와 직접 SQL 테스트
fixture다. 원인 컬럼이 없는 R01에서 generated column을 도입하면 기존 PATCH의 쓰기
대상이 없어지므로 안전하지 않다.

R02에서는 세 원인 컬럼을 쓰기 SSOT로 두고 `is_closed`를 generated column으로 계산하는
방식을 우선한다. DB 불변식을 한 곳에서 보장할 수 있고 운영 쓰기 경로가 위 목록으로
한정되어 있어 변경 범위가 통제 가능하기 때문이다. 단, JPA가 generated 값을 같은
트랜잭션 응답에 안정적으로 다시 읽지 못하거나 기존 API 응답 계약을 유지할 수 없으면
mutable+CHECK를 대안으로 선택한다. 어느 방식이든 모든 쓰기 경로와 migration 계약
테스트로
`is_closed = admin_closed OR recurring_holiday_closed OR template_inactive_closed`를
검증한다. 기존 닫힌 TimeSlot은 `admin_closed = true`, 나머지 원인은 false로 이관한다.

R02 구현에서는 generated stored column을 최종 선택했다. JPA는 `is_closed`를 읽기 전용으로
매핑하고, 도메인의 `isClosed()`는 세 원인의 OR을 즉시 계산한다. 따라서 같은 트랜잭션에서
관리자 PATCH 응답을 만들 때 Hibernate refresh에 의존하지 않으며, DB에서 다시 읽은 값도
같은 generated 식으로 일치한다. 기존 `changeClosedStatus`는 호환 진입점으로 남기되
`admin_closed`만 변경한다. 직접 SQL fixture도 generated column이 아니라 원인 컬럼을
쓴다.

R01의 `end_time`은 단계적 이관을 위해 DB에서 nullable로 추가하지만 사전 점검을 통과한
기존 행은 모두 backfill하고 애플리케이션 신규 쓰기는 항상 45분 종료 시각을 저장한다.
R02는 `end_time`을 최종 NOT NULL로 강화하고, raw SQL과 외부 이관 도구가 값을 생략해도
시작 시각에서 45분을 계산하는 MySQL default expression을 둔다. 명시 입력과 기본 입력
모두 45분·당일 종료 CHECK를 통과해야 하므로 default가 제약을 우회하지 않는다.

### R02 migration과 복구 경계

R02는 V17~V24를 다음처럼 분리한다.

1. V17 Java migration이 모든 기존 TimeSlot·Reservation 구간과 R01 metadata를 읽기
   전용으로 검사한 뒤 `end_time` NOT NULL과 45분 CHECK를 강화한다.
2. V18~V23이 Template, 정기 휴일, Config guard, ScheduleDate, member-day guard와
   일정 감사 로그를 각각 추가한다.
3. V24가 TimeSlot의 template 참조와 세 마감 원인을 추가·backfill한 뒤
   `is_closed`를 generated column으로 교체한다.

MySQL DDL은 트랜잭션 rollback 대상이 아니므로 V17의 두 ALTER 또는 V24의 여러 ALTER
중간에 장애가 나면 일부 변경이 남을 수 있다. 이 경우 Flyway를 무조건 재실행하지 않고
`information_schema`와 `flyway_schema_history`를 대조해 적용 지점을 확인한 뒤, 누락
DDL을 forward-complete하거나 명시적으로 원복하고 `flyway repair`한다. migration을
도메인별 버전으로 분리해 이 수동 복구 범위를 줄였지만 완전히 제거하지는 못한다.

Template의 정원은 새 occurrence 생성 시점에만 기본값으로 복사한다. 기존 occurrence의
수동 정원 보정은 자동 동기화가 덮어쓰지 않는다. Template 변경을 기존 미래 TimeSlot에
반영하려면 영향 미리보기와 명시적 동기화 Command가 필요하며, 활성 예약 수보다 작게
정원을 자동 축소하지 않는다.

자동 occurrence는 운영 동기화에서 물리 삭제하지 않는다. 자동 동기화는
`admin_closed`를 변경하지 않으며 자신이 소유한 정기 휴일·Template 플래그만 변경한다.
정기 휴일과 Template 비활성화 플래그는 `source = TEMPLATE` 행에만 적용한다.
`source = MANUAL` 행은 날짜 `CLOSING/CLOSED` 또는 관리자 휴강 외의 설정 동기화로
닫거나 재개하지 않는다.
따라서 현재 범위에서는 별도 ScheduleOccurrenceExclusion을 두지 않는다.

### TimeSlot 마감 원인 상태 전이

| 현재 원인 | 사건 | 이후 원인 | `is_closed` |
|---|---|---|---|
| `ADMIN` | 정기 휴일 적용 | `ADMIN + RECURRING_HOLIDAY` | true |
| `ADMIN + RECURRING_HOLIDAY` | 정기 휴일 해제 | `ADMIN` | true |
| `RECURRING_HOLIDAY` | Template 비활성화 | `RECURRING_HOLIDAY + TEMPLATE_INACTIVE` | true |
| `RECURRING_HOLIDAY + TEMPLATE_INACTIVE` | Template 재활성화 | `RECURRING_HOLIDAY` | true |
| `ADMIN + RECURRING_HOLIDAY` | 날짜 OPEN | `ADMIN` | true |
| `RECURRING_HOLIDAY` | 날짜 OPEN | 원인 없음 | false |
| 둘 이상의 원인 | 한 원인만 해제 | 남은 원인 유지 | true |
| `ADMIN + 다른 원인` | 관리자 재개 | 다른 원인 유지 | true |
| `ADMIN` | 관리자 재개 | 원인 없음 | false |

날짜 OPEN은 해당 날짜의 `recurring_holiday_closed`만 해제한다. `admin_closed`와
`template_inactive_closed`는 우선순위가 높아 OPEN이 해제하지 않는다. OPEN을 NORMAL로
돌리고 정기 휴일이 적용되면 동기화가 `recurring_holiday_closed`를 다시 설정한다.

### 우선순위와 동기화

운영 판정 순서는 다음과 같다.

1. 날짜 `CLOSING` 또는 `CLOSED`
2. 개별 TimeSlot `is_closed`
3. 날짜 `OPEN`
4. 정기 휴일
5. 정규 Template

동기화 Job은 누락 occurrence를 보충하는 역할만 한다. 조회와 Command는 요청 시점의
ScheduleConfigGuard, ScheduleDate, 휴일과 TimeSlot 상태를 다시 검증한다. 동기화는
날짜별 짧은 트랜잭션으로 실행하고 UNIQUE 충돌 시 기존 행을 다시 읽어 멱등 결과로
처리한다.

정기 휴일 또는 Template 비활성화로 TEMPLATE TimeSlot을 닫아도 기존 활성 예약은 자동
취소하지 않는다. occurrence와 설정 version 반영이 끝나면 guard는 `ACTIVE`로 복귀한다.
남은 활성 예약은 닫힌 TEMPLATE TimeSlot과 활성 Reservation에서 파생하는 별도 휴무 정리
Read Model로 건수·목록·진행 상태를 제공한다. 이 정리 상태 때문에 전역 `SYNCING`을
유지하지 않는다. 정리는 휴무 전용 취소로 쿠폰 예약 `stable/RETURN`, 1회 결제 예약
`stable/NONE`을 적용한다.

### 날짜 휴무와 개별 휴강

미래 날짜의 활성 예약이 있으면 `CLOSING`으로 전환하고 신규 유입을 막는다. 휴무 정리
전용 취소 Command는 쿠폰 예약에 `stable/RETURN`, 1회 결제 예약에 `stable/NONE`을
강제한다. 활성 예약이 0건일 때만 `CLOSED`로 확정한다.

`CLOSING`에서는 예약 승인, 입금 확인, 입금 만료 복구, 해당 날짜로의 예약 변경,
관리자 수동 예약과 회원 직접 취소를 차단한다. 휴무 정리 전용 관리자 취소, 예약 반려,
자동 입금 만료와 자동 승인 만료는 허용한다.

| Command | CLOSING |
|---|---|
| 예약 승인 | 차단 |
| 입금 확인 | 차단 |
| 입금 만료 복구 | 차단 |
| 해당 날짜로 예약 변경 | 차단 |
| 관리자 수동 예약 | 차단 |
| 회원 직접 취소 | 차단 |
| 휴무 정리 전용 관리자 취소 | 허용 |
| 예약 반려 | 허용 |
| 자동 입금 만료 | 허용 |
| 자동 승인 만료 | 허용 |

R09 구현은 날짜 휴무 Command의 잠금 순서를 다음처럼 고정한다.

| Command | 실제 잠금 순서 |
|---|---|
| CLOSING 시작·CLOSED 확정·CLOSING 취소 | `ScheduleDate FOR UPDATE → 활성 Reservation ID 오름차순 조회` |
| 휴무 정리 전용 취소 | `ScheduleDate FOR UPDATE → member-day guard → Reservation FOR UPDATE → 원래 Coupon FOR UPDATE → 감사 로그 append` |
| 예약 승인·입금 확인 | `ScheduleDate FOR UPDATE → Reservation FOR UPDATE` |
| 입금 만료 복구 | `ScheduleConfigGuard FOR SHARE → ScheduleDate FOR UPDATE → member-day guard → TimeSlot FOR UPDATE → overlap/점유 Reservation → 대상 Reservation` |
| 예약 변경 | `ScheduleConfigGuard FOR SHARE → ScheduleDate 날짜순 → member-day guard 날짜순 → TimeSlot ID순 → overlap/점유 Reservation → 대상 Reservation → Coupon` |
| 수동 TimeSlot 생성 | `ScheduleConfigGuard FOR SHARE → ScheduleDate FOR UPDATE → TimeSlot UNIQUE 확인·INSERT` |
| 반려·자동 만료 | `ScheduleDate FOR UPDATE → member-day guard → Reservation FOR UPDATE → Coupon FOR UPDATE(쿠폰 예약)` |

`CLOSING` 시작은 날짜 행을 먼저 잠근 뒤 활성 예약을 읽으므로 신규 예약 Command가 같은
날짜 잠금을 먼저 얻었다면 해당 예약을 영향 목록에 포함하고, 휴무 Command가 먼저 얻었다면
이후 신규 유입을 차단한다. CLOSED 확정은 같은 날짜 잠금 안에서 활성 상태
`PENDING_ADMIN_APPROVAL`, `PENDING_PAYMENT`, `CONFIRMED`를 다시 집계한다.

휴무 정리 전용 취소만 CLOSING에서 허용하고 일반 관리자 취소는 차단한다. 전용 취소는
예약이 이미 종료 상태면 추가 Coupon 반환과 감사 로그를 만들지 않아 같은 요청의 반복에
멱등적이다. Coupon 반환 실패는 같은 트랜잭션의 Reservation 상태와 두 감사 로그를 모두
rollback한다.

당일 휴장은 아직 시작하지 않은 개별 TimeSlot에 명시적 `TimeSlotClosure`를 시작한다.
`admin_closed = true`는 신규 유입을 차단하는 현재 운영 상태이고, Closure는 고객별 정리
workflow 이력이다. 시작 시점의 활성 Reservation을 `TimeSlotClosureImpact`로 고정하며
기존 예약은 자동 취소하지 않는다.

```text
TimeSlotClosure
  status = IN_PROGRESS | COMPLETED | WITHDRAWN
  startedBy/startedAt/reason
  completedBy/completedAt
  withdrawnBy/withdrawnAt
  version

TimeSlotClosureImpact
  closureId
  reservationId
  reservationStatusAtStart
  createdAt
  UNIQUE(closureId, reservationId)
```

동일 TimeSlot의 활성 Closure는 generated guard와 UNIQUE로 최대 하나만 허용한다.
Impact membership은 append-only 고정 분모이며 Reservation이 비활성화되거나 원래
TimeSlot에서 이동하면 해결된 것으로 계산한다. 완료 직전에는 모든 Impact 해결과 현재
TimeSlot 활성 예약 0건을 모두 다시 검사한다.

휴강 시작과 각 예약 취소는 별도 짧은 트랜잭션이다. 취소는
`ScheduleDate → member-day guard → TimeSlot → TimeSlotClosure → Reservation → Coupon
→ append-only audit` 순서를 따르고 여러 행은 ID 오름차순으로 잠근다. 날짜 휴무와 개별
휴강이 같은 Reservation을 정리하면 Reservation 잠금 후 최초 상태 변경만 Coupon 반환과
감사를 기록한다.

휴강 중 승인·입금 확인·복구·일반 변경·일반 관리자 취소·완료·노쇼와 모든 신규 유입을
차단한다. 휴강 전용 취소, 휴강 책임을 적용하는 회원 취소, 반려와 자동 만료는 허용한다.
전용 취소는 쿠폰 예약 `stable/RETURN`, 1회 결제 예약 `stable/NONE`을 강제한다.

해결된 Impact가 하나도 없을 때만 `IN_PROGRESS → WITHDRAWN`을 허용한다. COMPLETED 이후
재개는 수업 시작 전이고 날짜 상태가 허용할 때만 가능하다. 철회와 재개는
`admin_closed = false`만 적용하며 다른 마감 원인과 날짜 상태를 변경하지 않고 기존
예약도 복구하지 않는다.

기존 `PATCH /api/admin/timeslots/{id}`는 호환 진입점으로 유지하되 close는 Closure 시작,
reopen은 상태에 따라 철회 또는 완료 후 재개에 위임한다. 직접 필드 쓰기 경로는 두지
않는다. 전용 조회·예약별 취소·완료 API가 제공되는 R11 전에는 R10과 R11을 함께
배포하거나 기존 관리자 진입점을 제한해야 한다.

## 결과

- Reservation과 정원은 계속 실제 TimeSlot occurrence를 사용한다.
- 월요일 OPEN과 반복 휴일 해제가 Template 삭제 없이 가능하다.
- 설정 변경과 예약 Command가 ScheduleConfigGuard를, 자동 생성·수동 추가·휴무 전환과
  예약 Command가 ScheduleDate를 공통 직렬화 기준으로 사용한다.
- 마감 원인을 독립적으로 저장해 하나의 원인 해제가 다른 원인의 휴강을 재개하지 않는다.
- 알림, 자동 일괄 취소와 occurrence 물리 정리는 범위에서 제외한다.

## 검증

- 오늘부터 3개월 후까지 ScheduleDate와 정규 occurrence가 양 끝 날짜를 포함해 생성된다.
- 반복·동시 동기화와 다음 날 보충에서 중복이 없다.
- 설정 변경 커밋과 날짜별 동기화 사이에 회원 예약과 관리자 수동 예약이 성공하지 않는다.
- SYNCING 중 차단 대상은 통일된 503을 받고 기존 예약·쿠폰·휴무 정리 조회와 안전한 종료
  Command는 계속 성공한다.
- 동시 설정 변경은 하나만 SYNCING으로 진입하고 이전 version worker는 반영하지 않는다.
- 일부 날짜 반영 뒤 프로세스가 중단돼도 재시작 시 같은 pending version으로 이어서
  완료하고 임의 ACTIVE 전환을 하지 않는다.
- 같은 요일의 겹치는 정기 휴일 동시 등록은 하나만 성공한다.
- 월요일 정기 휴일에는 자동 생성하지 않고 OPEN에는 전체 정규 occurrence를 생성한다.
- 정기 휴일·Template 비활성화 동기화는 TEMPLATE 행만 닫고 MANUAL 행과 ADMIN 마감
  원인을 변경하거나 삭제하지 않는다.
- 설정 동기화 완료 후 기존 활성 예약이 남아 있어도 ACTIVE로 복귀하고 별도 휴무 정리
  목록에서 추적한다.
- CLOSING과 개별 휴강이 신규 유입을 먼저 차단하고 전용 취소·쿠폰 반환·감사를 보존한다.
