# ADR-023: 완료 예약과 materialized TimeSlot 기반 관리자 운영 조회

## 상태

Accepted, 2026-08-25

## 배경

MVP 3.2까지 Horse는 Reservation 완료, RidingClass, materialized TimeSlot, 회원과 예약 상태를
보존한다. 관리자는 이 사실을 월간 운영 실적과 주간 시간표로 보고 싶지만 현재 관리자 API는
페이지 기반 예약 목록, 일별 상태 요약과 현재 시각 이후 TimeSlot 목록만 제공한다.

Member의 누적 기승 횟수는 progression baseline, 특수 승인 인정분과 M32-07 집계 오류 보정의
영향을 받는다. 이 값은 특정 월에 Horse 예약으로 실제 진행된 수업 횟수를 복원하는 사실 원장이
아니다. 반대로 Reservation은 회원, RidingClass, `lessonDate`와 현재 상태를 보존하고 완료된 실제
수업은 `COMPLETED`로 확정된다.

TimeSlot은 특정 RidingClass 하나에 속하지 않는다. 하나의 `TimeSlotCapacity`가 전체 정원과 모든
RidingClass별 capacity를 가지며 실제 예약이 RidingClass를 선택한다. 따라서 예약이 없는 TimeSlot에
임의의 수업 클래스를 붙일 수 없다.

## 결정

### 월간 운영 실적

월간 기승 현황의 사실 SSOT는 다음 조건을 모두 만족하는 Reservation이다.

- `status == COMPLETED`
- `lessonDate`가 조회 월의 `Asia/Seoul` 달력 범위에 포함됨
- 조회 종류가 `ALL`이거나 Reservation의 RidingClass 분류와 일치함

조회 전용 종류 `ALL`, `GENERAL`, `DRESSAGE`, `JUMPING`은 영속 상태가 아니다.
`GENERAL`은 `RidingClass.isGeneral()`, 나머지는 기존 `DRESSAGE`, `JUMPING` 값으로 결정한다.
세 종류는 현재 catalog의 배타적·전체 분할이며 `ALL`은 세 집합의 합이다.

응답은 조회 월과 종류, 총 완료 횟수, 최고 완료 횟수와 그 횟수를 가진 모든 회원을 반환한다.
동률 회원을 임의의 ID 정렬로 한 명만 숨기지 않는다. 대상이 없으면 총 횟수는 0이고 최고 회원
목록은 비어 있다. 이름은 조회 시점의 Member 표시 이름을 사용하며 별도 과거 이름 snapshot은
도입하지 않는다.

baseline, progressionValue, specialApprovalProgressionCredit, promotion hold, Member 누적 기승 횟수,
M32-07 correction과 Horse 운영 이전 경력은 월간 운영 실적에 포함하지 않는다. correction에
실제 수업일을 추가하거나 특정 월에 귀속하지 않는다.

### 주간 운영 캘린더

주간 조회는 요청 기준일이 속한 월요일부터 일요일까지의 실제 materialized TimeSlot을 root로
사용한다. 예약 존재 여부로 TimeSlot을 필터링하지 않으므로 0건인 슬롯도 반환한다.

각 TimeSlot에는 기존 식별자와 시간을 제공하고 같은 `lessonDate`와 `startTime`을 가진
캘린더 표시 대상 Reservation을 묶는다. 표시 대상은 다음 의미 규칙으로 결정한다.

`calendar-visible reservation = capacity-occupying reservation OR COMPLETED reservation`

정원 점유는 `occupiesCapacity()`와 `occupyingStatuses()`로 노출되는 `ReservationStatus`의 기존 점유
계약에서 파생한다. 현재 결과는 `PENDING_ADMIN_APPROVAL`, `PENDING_PAYMENT`, `CONFIRMED`이며 실제
수업이 진행된 `COMPLETED`를 추가한다. 별도의 캘린더 상태 목록이나 영속 상태를 만들지 않는다.

Reservation 행에는 회원 식별·표시 이름, RidingClass와 원래 ReservationStatus를 제공한다.
승인대기·입금대기를 확정으로 합치거나 새 운영 상태를 만들지 않는다. `PAYMENT_EXPIRED`,
`APPROVAL_EXPIRED`, `REJECTED`, `CANCELLED`, `NO_SHOW`는 현재 정원을 점유하지 않고 완료 수업도
아니므로 기본 주간 캘린더에서 제외한다. 이력 탐색은 기존 관리자 예약 조회와 해당 전이에 대해
존재하는 감사 조회의 책임으로 남긴다.

표시 대상 예약 행이 없을 때는 제외 상태의 과거 Reservation이 존재해도 `예약 없음`으로 표현한다.

빈 TimeSlot은 특정 RidingClass 수업으로 단정하지 않고 RidingClass는 Reservation별로만 표시한다.

주간 API는 한 요청으로 7일 범위를 반환한다. 날짜별·TimeSlot별 HTTP 호출이나 슬롯별 Member 조회를
하지 않는다. TimeSlot과 Reservation/Member를 bounded query로 일괄 조회하고 결정적인 날짜·시각·ID
순서로 조립한다.

### 조회와 성능

두 기능은 source entity를 바꾸지 않는 read model이다. 월간 통계는 DB의 조건·그룹 집계를 사용하고
전체 Reservation을 애플리케이션 메모리로 읽지 않는다. 기존 `idx_reservations_lesson_status`를 먼저
사용해 query plan을 검증하며 회원·RidingClass grouping에 추가 index가 실제로 필요하다는 근거가
있을 때만 후속 migration을 추가한다.

주간 조회는 `time_slot_capacities`의 날짜 범위와 Reservation의 날짜 범위를 각각 한정한다. 화면은
Backend가 계산한 결과를 표시하며 종류 분류, 통계 집계 또는 예약 상태 정책을 복제하지 않는다.

## 검토한 대안

### Member 누적 횟수와 M32-07 correction을 월별 통계에 사용

거부한다. 누적 횟수와 correction에는 실제 수업 월과 특수 기승 종류를 정확히 복원할 정보가 없고
progression 목적과 운영 실적 목적이 다르다.

### 월별 mutable counter 또는 snapshot table

거부한다. 현재 Reservation 사실에서 결정적으로 집계할 수 있으며 쓰기 경로와 정합성 책임을 하나 더
만드는 것은 현재 규모와 요구에 비해 과하다.

### 기존 페이지 API를 Frontend에서 반복 호출해 집계·조립

거부한다. 최대 Page 크기, 날짜·슬롯별 다중 요청, 빈 TimeSlot 누락과 Frontend 도메인 규칙 복제가
발생한다.

### 확정 및 완료 Reservation만 표시

거부한다. `PENDING_ADMIN_APPROVAL`과 `PENDING_PAYMENT`도 현재 정원을 점유하므로 숨기면 관리자가
추가 예약 가능 좌석을 잘못 판단할 수 있다.

### 모든 ReservationStatus를 표시

거부한다. 취소·반려·만료·노쇼처럼 현재 정원을 점유하지 않고 완료 수업도 아닌 terminal 이력까지
표시하면 운영 가용성 화면이 전체 예약 이력 화면으로 확장되고 `예약 없음` 의미가 흐려진다.

### 범용 analytics 또는 calendar framework 도입

거부한다. 필요한 것은 두 개의 bounded 관리자 read model과 반응형 Web 화면이며 범용 분석·일정
편집 기능은 현재 범위가 아니다.

## 결과

- Reservation 완료와 TimeSlot 운영 정책은 변경되지 않는다.
- 새로운 mutable truth, correction 귀속일, 통계 snapshot과 이벤트 원장을 만들지 않는다.
- 월간 집계와 주간 조립은 각각 전용 Backend query/API를 사용한다.
- OpenAPI와 generated client 동기화 후 관리자 Web이 결과만 표시한다.
- Phase C 전체 Gate 완료 후 MVP 4를 시작한다.
