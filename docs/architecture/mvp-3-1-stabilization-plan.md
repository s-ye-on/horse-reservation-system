# MVP-3.1 운영 안정화 계획

## 결정 상태

MVP-3.1과 MVP-3.2의 구현 순서와 품질 Gate를 정의한다. 개별 구조 결정은 각 Task에서
ADR로 확정하며 이 문서는 실행 순서의 SSOT다.

2026-07-23 최신 운영 정책에 따라 Checkpoint 1을 다시 열었다. 기존 M31-01~M31-05의
커밋과 완료 증거는 당시 정책의 이력으로 보존하고, 별도 M31-R Task가 이를 보정한다.
M31-06은 Checkpoint 1 재검증이 끝날 때까지 시작하지 않는다.

## 시간 정책

- 모든 시간 판정과 마장 표시 시각은 `Asia/Seoul` 기준이다.
- 회원 신규 예약은 당일에도 가능하며 `now <= lessonStartAt - 3시간`일 때 허용한다.
- 회원 신규 예약 마감 이후에는 `RESERVATION_BOOKING_DEADLINE_PASSED`, 정확히 수업 시작
  시각부터는 `RESERVATION_LESSON_ALREADY_STARTED`를 반환한다.
- 관리자는 별도 수동 예약 Command에서 3시간 마감을 우회하지만 수업 시작 시각부터는
  예약할 수 없다.
- 기존 예약 변경 마감은 수업 전날 21:00을 유지한다.
- Scheduler는 지연 상태를 정리하지만 조회와 Command의 시간 검증을 대신하지 않는다.

외부 API의 실제 시점은 RFC3339 offset을 포함한다. 웹과 Expo는 기기 시간대와 관계없이
`Asia/Seoul`로 표시한다. 계약 테스트는 최소 `UTC`, `America/Los_Angeles`,
`Asia/Seoul` 환경에서 같은 마장 시각이 표시되는지 검증한다.

## 일정 운영과 중복 범위

정규 시간표는 요일·시작 시각별 Template로 정의하고 `TimeSlotCapacity` concrete
occurrence를 오늘부터 3개월 후까지 생성한다. 정규 및 수동 TimeSlot은 모두 45분이며
서로 겹칠 수 있다. Template은 정기 휴일과 독립적이고 월요일 OPEN에도 사용할 월요일
행을 보유한다.

날짜 운영 상태는 실제 ScheduleDate 행의 `NORMAL`, `OPEN`, `CLOSING`, `CLOSED`로
관리한다. 날짜 상태, 개별 TimeSlot 마감, OPEN, 정기 휴일, Template 순으로 판정한다.
자동 occurrence는 삭제하지 않고 닫힌 행을 유지하며 마감 원인으로 자동 재개 범위를
구분한다. 단일 마감 원인을 사용하지 않고 `admin`, `recurring holiday`,
`template inactive` 원인을 독립적으로 보존한다. 상세 결정은 ADR-015를 따른다.

동일 회원은 같은 날짜에 `[startTime, endTime)`이 겹치는 활성 Reservation을 둘 수 없다.
활성 상태는 다음과 같다.

- `pending_admin_approval`
- `pending_payment`
- `confirmed`

V15 exact-start UNIQUE는 부분집합 최종 방어로 유지한다. 정상 Command는 회원·날짜 guard
행을 먼저 잠그고 overlap을 검사하며 외부 오류는
`RESERVATION_OVERLAPPING_ACTIVE_RESERVATION`으로 통합한다. 상세 결정은 ADR-016을 따른다.

## 관리자 수동 예약

회원 예약 API의 역할 분기로 구현하지 않고 별도 관리자 Command와 API를 사용한다.

- 사용 가능한 쿠폰 있음: 쿠폰을 점유하고 즉시 `confirmed`
- 사용 가능한 쿠폰 없음: `pending_payment`
- `pending_payment`는 관리자 입금 확인 후 `confirmed`
- 입금 마감은 `min(createdAt + 2시간, lessonStartAt)`
- 수업 시작, 자격, 중복, 정원, 마감 TimeSlot과 쿠폰 불변식은 우회하지 않음

Phase A에서는 개인 소유 쿠폰 후보를 사용한다. Phase B에서 동일한 후보 선택 Port를
가족 구성원 소유 쿠폰까지 확장하며 Reservation 생성 흐름을 다시 분기하지 않는다.

## 잠금 계약

### Canonical order

1. Idempotency row: 생성 API만 해당
2. ScheduleConfigGuard: 일정 신규 유입은 `FOR SHARE`, 설정 변경은 `FOR UPDATE`
3. ScheduleDate rows: 날짜 오름차순
4. Member-day guard rows: 회원·날짜 오름차순
5. TimeSlot rows: ID 오름차순
6. overlap과 정원 집계 Reservation rows: 시작 시각·ID 오름차순
7. 대상 Reservation row: 앞 단계에서 잠기지 않은 기존 예약 Command
8. FamilyGroup row: 가족 후보 선택 시
9. 선택된 Coupon row 한 건
10. Member row: 횟수 변경 시
11. 추가 전용 감사 로그

Template·정기 휴일 변경은 ScheduleConfigGuard를 `SYNCING`으로 전환한 뒤 version별
날짜 동기화를 수행한다. 설정 변경 커밋과 occurrence 반영 사이에는 신규 예약·예약
변경·복구·관리자 수동 예약·수동 TimeSlot 생성을 허용하지 않는다. 기존 예약을 안전한
종료 상태로 보내는 반려, 자동 만료와 휴무 정리 취소는 이 설정 상태에 종속되지 않는다.
예약 가능 TimeSlot 조회도 `SYNCING` 동안 503으로 차단하지만 기존 예약·쿠폰·휴무 정리
조회와 동기화 상태·재시도는 허용한다. 미완료 pending version은 재시작 후 이어서
처리하며 경과 시간만으로 강제 ACTIVE 전환하지 않는다.

M31-06은 Command별로 다음 근거를 문서화해야 완료할 수 있다.

- 실제 Repository 메서드와 SQL
- 잠기는 테이블·행·범위와 순서
- where/order 조건에 사용되는 인덱스
- MySQL `EXPLAIN FORMAT=JSON` 결과
- range/gap lock 가능성과 격리 수준 영향
- 다른 Command와 교차할 때의 대기 관계

시간대 최대 인원이 작다는 사실만으로 잠금 범위가 작다고 판단하지 않는다. 실행 계획과
인덱스 근거 없이 잠금 범위를 주장할 수 없다.

### Deadlock 처리 Gate

품질 Gate는 deadlock 관측 0건을 절대 조건으로 삼지 않는다.

- Production Command 사이의 구조적 잠금 순서 역전을 제거한다.
- MySQL deadlock 오류 `1213`만 전체 트랜잭션 경계 밖에서 제한적으로 재시도한다.
- 기본 정책은 최초 실행을 포함해 최대 3회이며 backoff와 jitter를 적용한다.
- lock timeout, 업무 예외, 검증 실패와 인증 실패는 deadlock 재시도 대상으로 삼지 않는다.
- 재시도 후에도 예약, 쿠폰 수치, 기승 횟수와 감사 로그의 논리적 효과는 정확히 한 번만 반영되어야 한다.
- 재시도 소진 시 통일된 ErrorResponse와 구조화 로그를 남긴다.

## 멱등성 원자성

- 범위는 인증 주체, API operation, `Idempotency-Key` 조합이다.
- 같은 키와 같은 fingerprint는 최초 성공 결과를 반환한다.
- 같은 키와 다른 fingerprint는 `409 Conflict`다.
- Reservation 생성, 정원 점유, Coupon 점유, 감사 로그와 idempotency 완료 결과를 같은 DB 트랜잭션에서 커밋한다.
- 비즈니스 커밋 뒤 별도 트랜잭션으로 idempotency 결과를 기록하지 않는다.
- Reservation 저장 이후 idempotency 완료 기록 직전에 장애를 주입했을 때 전체 변경이 rollback되는 통합 테스트를 둔다.
- 커밋 후 응답 전송이 실패한 경우 재요청이 저장된 성공 결과를 반환하는 테스트를 둔다.

## 가족 쿠폰 선택

Coupon과 원소유자를 유지하고 가족 합산 잔액을 만들지 않는다. 가족 그룹 행을 잠근 뒤
활성 구성원의 후보에서 한 Coupon만 잠근다. MySQL에서는 다음 정렬을 명시한다.

```sql
ORDER BY (expires_at IS NULL) ASC,
         expires_at ASC,
         created_at ASC,
         id ASC
LIMIT 1
FOR UPDATE
```

만료일 NULL이 가장 마지막이며 동일 조건에서 항상 같은 Coupon을 선택하는 MySQL 통합
테스트를 작성한다.

## Checkpoint

### Checkpoint 1: M31-00~M31-05 + M31-R00~M31-R14

기존 완료 이력에 최신 3시간 예약 마감, 45분 interval, ScheduleDate, 정규 Template,
정기 휴일, occurrence 동기화, 날짜·개별 휴무와 활성 overlap 불변식을 보정한다. API,
정규 시간표 웹, 휴무 웹을 독립 Task로 검증한다. 세부 순서는
[Checkpoint 1 보정 Backlog](../tasks/mvp-3.1-checkpoint-1-remediation.md)를 따른다.

### Checkpoint 2: M31-06~M31-12

잠금 matrix와 EXPLAIN, deadlock 재시도, 원자적 멱등성, 관리자 수동 예약, ErrorResponse,
Page와 RFC3339/OpenAPI 계약을 완료한다. 종료 시 API breaking change와 client migration
위험을 보고한다.

### Checkpoint 3: M31-13~M31-17

웹 목록과 CSV, JWT 운영 설정, OpenAPI/E2E 하네스와 전체 회귀를 완료한다. 종료 시
Phase B와 모바일 진행 가능 여부를 판정한다.

각 Checkpoint가 끝나기 전 다음 구간 Task를 active로 이동하지 않는다.

## 데이터 이관 전략

- 기존 TimeSlot은 `source = MANUAL`, `end_time = start_time + 45분`으로 backfill한다.
- 기존 Reservation은 `end_time = start_time + 45분`으로 backfill한다. 자정을 넘는 기존
  행이나 활성 overlap이 있으면 임의 보정하지 않고 migration을 중단해 ID를 보고한다.
- ScheduleDate는 현재 3개월 범위뿐 아니라 기존 TimeSlot과 Reservation의 distinct 날짜도
  생성하여 기존 이력이 참조하는 날짜 행을 보존한다.
- 기존 TimeSlot과 Reservation을 근거로 Template, 정기 휴일 또는 OPEN 상태를 추정하지 않는다.
- M31-R migration 전에 활성 상태의 구간 중복을 self-join preflight로 검사한다.
- 활성 중복 제약은 상태에 따라 값을 갖는 generated marker와 UNIQUE key를 사용하고 종료
  상태 이력은 그대로 보존한다. V15는 수정하지 않고 신규 migration과 인덱스를 추가한다.
- 기본 월요일 정기 휴일은 등록하지만 Template별 정원 값은 운영 입력 없이 기존 TimeSlot
  값에서 임의 추정하지 않는다. Template 활성화 전에 관리자가 정원을 확정한다.
- Idempotency 원장과 관리자 수동 예약 감사 필드는 additive migration으로 추가한다. 기존
  예약에 멱등성 키를 역생성하지 않는다.
- Page와 RFC3339 변경은 저장 데이터를 재해석하지 않는다. 기존 날짜·시각 컬럼을
  `Asia/Seoul`의 수업 시각으로 조합하고 외부 DTO 경계에서 offset을 부여한다.
- Phase B의 가족·클래스 컬럼과 원장은 nullable 또는 빈 상태로 추가한다. 기존 Coupon
  소유권과 기승 횟수는 유지하고 가족 snapshot, progression baseline이나 promotion hold를
  추정해 채우지 않는다.

## ADR 계획

- ADR-014: exact-start 활성 예약 DB 불변식. ADR-016이 부분 대체한다.
- ADR-015: 정규 Template, 정기 휴일, ScheduleDate와 materialized occurrence
- ADR-016: 회원·날짜 guard와 활성 예약 구간 overlap
- ADR-017: Horse `Idempotency-Key` 원장, 원자성, TTL과 정리 Job
- ADR-018: Coupon 소유권 유지형 가족 공유와 snapshot 감사
- ADR-019: progression baseline, promotion hold, effective class와 기승 횟수 조정 원장
- ADR-020: Page 응답과 RFC3339 외부 API 시간 계약

M31-00은 위 ADR의 입력과 결정 범위만 고정한다. 실제 스키마와 클래스 배치는 각 구현
Task가 현재 코드와 실행 계획을 검증한 뒤 독립 ADR과 커밋으로 확정한다.

## Phase A 최종 Gate

- 시간 경계의 직전·정각·직후와 서로 다른 기기 시간대 표시 계약이 통과한다.
- Command lock matrix에 구조적 순서 역전이 없고 실제 쿼리·인덱스·EXPLAIN 근거가 있다.
- 실제 MySQL deadlock 재시도와 교차 Command 테스트가 논리적 exactly-once를 증명한다.
- 활성 중복 제약과 생성 idempotency 원자성 장애 주입 테스트가 통과한다.
- 가족 공유를 제외한 Reservation/Coupon 수치와 로그가 모든 상태 전이에서 일치한다.
- 400, 401, 403, 404, 405, 409와 500이 동일한 ErrorResponse 구조다.
- Page, OpenAPI required, RFC3339와 생성 TypeScript client 계약이 일치한다.
- 운영 실행은 JWT secret을 강제하고 demo 인증 코드가 운영 경로에 없다.
- `verify:all`이 MySQL, OpenAPI와 핵심 E2E를 포함해 깨끗한 checkout에서 연속 2회 성공한다.

Phase A와 Phase B 이후 확정된 관리자 운영 가시성 요구는 MVP 3.3 Phase C에서 먼저 구현한다.
MVP 4 전체는 Phase C 최종 Gate까지 완료한 뒤 시작한다.
