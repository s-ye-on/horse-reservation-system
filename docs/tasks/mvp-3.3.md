# MVP 3.3 Backlog: 관리자 운영 가시성

| ID | 단일 작업 | 의존성 | 완료 신호 | 제외 범위 | 검증 명령 |
|---|---|---|---|---|---|
| M33-00 | 관리자 운영 통계·주간 캘린더 SSOT와 ADR | M32-10 | 완료 예약 통계와 materialized TimeSlot 조회 계약 확정 | 제품 코드·migration·MVP 4 | `mise run verify:m33-00` |
| M33-01 | 관리자 월간 완료 기승 통계 Query API | M33-00 | 월·종류별 총 횟수와 공동 1위 집계·쿼리 비용 테스트 성공 | Web·mutable 통계·M32-07 변경 | `mise run verify:m33-01` |
| M33-02 | 관리자 주간 운영 캘린더 Query API | M33-00 | 7일 TimeSlot·빈 슬롯·회원·RidingClass·기존 상태 일괄 조회 성공 | 일정·예약 Command·Web | `mise run verify:m33-02` |
| M33-03 | Phase C OpenAPI와 generated client | M33-01, M33-02 | 통계·캘린더 schema와 Web 타입 재생성 diff 승인 | 제품 정책·화면 | `mise run verify:m33-03` |
| M33-04 | 관리자 월간 기승 통계 Web | M33-03 | 월·종류 이동, 0건과 공동 1위 반응형 화면 테스트 성공 | 주간 캘린더·통계 export | `mise run verify:m33-04` |
| M33-05 | 관리자 주간 운영 캘린더 Web | M33-03 | 주 이동·오늘·빈 TimeSlot·다중 회원·예약 상태 반응형 E2E 성공 | 일정 편집·범용 Dashboard | `mise run verify:m33-05` |
| M33-06 | Phase C 전체 품질 Gate | M33-04, M33-05 | Backend 집계·OpenAPI diff·Web 회귀·E2E 승인 | MVP 4 구현 | `mise run verify:m33-06` |

모든 항목은 [실행 작업 템플릿](TEMPLATE.md)의 작업 크기 상한과 공통 중단 조건을 상속한다.
M33-01과 M33-02는 M33-00 이후 병렬로 진행할 수 있고 M33-04와 M33-05는 generated client가
동기화된 M33-03 이후 병렬로 진행할 수 있다.

## M33-00 계약

### 월간 완료 기승 통계

- 월간 기승 현황은 Horse 예약 시스템 운영 이후, 해당 월의 `COMPLETED` Reservation을 기준으로
  집계한 운영 실적이다.
- 조회 월은 `Asia/Seoul`의 `lessonDate`가 속한 달이며 기본값은 현재 월이다.
- 조회 종류는 `ALL`, `GENERAL`, `DRESSAGE`, `JUMPING`이다. 일반 여부는
  `RidingClass.isGeneral()`과 기존 `DRESSAGE`, `JUMPING` catalog로 파생하며 별도 영속 분류를 만들지 않는다.
- 종류를 바꾸면 월간 총 횟수, 공동 최다 기승 회원 전체와 각 회원의 횟수를 함께 다시 집계한다.
- 0건이면 총 횟수는 0이고 공동 최다 회원 목록은 비어 있다.
- baseline, progression 상태, 특수 승인 인정분, promotion hold, Member 누적 횟수와 M32-07
  보정은 집계하지 않는다. correction 실제 수업일이나 PRE/POST Anchor를 추가하지 않는다.

### 주간 운영 캘린더

- 조회 범위는 선택 기준일이 속한 월요일부터 일요일까지 7일이며 기본값은 현재 주다.
- 이전·다음 주와 `오늘`을 제공하고 `오늘`은 일간 화면이 아니라 현재 주로 복귀한다.
- 실제 `TimeSlotCapacity`에서 조회를 시작해 예약이 없는 materialized TimeSlot도 반환한다.
- 같은 날짜와 시작 시각의 Reservation을 TimeSlot 하나 아래에 묶고 회원, 예약 RidingClass와
  현재 `ReservationStatus`를 반환한다. 모든 기존 상태를 그대로 표현하며 새 상태를 만들지 않는다.
- TimeSlot은 여러 RidingClass capacity를 가진다. 빈 TimeSlot에 임의의 단일 수업 클래스를 붙이지
  않고 `예약 없음`으로 표시하며 클래스는 실제 Reservation별로 표시한다.
- 기존 upcoming/history 목록을 일곱 번 호출하지 않는다. 한 번의 주간 API가 bounded query로
  TimeSlot, Reservation과 Member를 일괄 조회한다.

### 구현 경계

- M33-01은 Reservation DB aggregation을 사용한다. 기존 `(lesson_date, status)` index를 먼저
  검증하고 실제 `EXPLAIN` 또는 통합 테스트 근거가 있을 때만 필요한 최소 index migration을 추가한다.
- M33-02는 7일 범위의 TimeSlot과 Reservation을 각각 bounded query로 읽어 조립하거나 동등한
  projection을 사용한다. 슬롯별·회원별 반복 query를 금지한다.
- 통계를 위한 mutable counter, snapshot table, event sourcing과 범용 analytics/calendar framework는
  현재 요구를 위해 도입하지 않는다.
- M33-03이 Backend runtime 계약을 OpenAPI와 generated TypeScript client에 동기화한 뒤 Web Task가
  소비한다. Frontend는 통계 집계나 RidingClass 분류 규칙을 복제하지 않는다.
