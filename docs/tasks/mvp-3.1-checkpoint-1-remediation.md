# MVP 3.1 Checkpoint 1 보정 Backlog

## 상태

`ACTIVE`

- `M31-R00`: COMPLETED
- `M31-R01`: COMPLETED
- `M31-R02`: COMPLETED
- `M31-R03`: COMPLETED

최신 일정 운영 정책이 기존 M31-01~M31-05의 시간, TimeSlot과 중복 예약 가정을
대체하므로 Checkpoint 1을 다시 연다. 기존 완료 Task와 커밋은 당시 정책의 증거로
보존하고 아래 보정 Task를 별도 커밋으로 수행한다. M31-R14가 끝나기 전 M31-06을
시작하지 않는다.

각 Task는 [실행 작업 템플릿](TEMPLATE.md)의 중단 조건을 상속한다. 작업별
`verify:m31-rXX` 등록은 해당 신규 Task 계약의 일부이며 기존 검증을 완화하지 않는다.
모든 Task는 마지막에 `mise run verify:changed`를 실행하고, 백엔드 변경 Task는
`mise run backend:convention`도 실행한다.

## Task

| ID | 단일 작업 | 선행 작업 | 완료 조건 | 제외 범위 | 검증 명령 |
|---|---|---|---|---|---|
| M31-R00 | 정책·ADR·보정 Task 계약 | M31-05 | 설정 변경 경쟁 대안·version guard, 다중 마감 원인 전이, CLOSING matrix, 정기 휴일 동시 등록, migration·잠금·API 경계 문서 일치 | 운영 코드·DB | `mise run docs:check` |
| M31-R01 | 45분 수업 구간과 source migration | M31-R00 | NULL·비정상 시각·자정·계산 불가·활성 overlap read-only preflight, TimeSlot·Reservation endTime backfill, 기존·신규 슬롯 MANUAL, 기존 마감 API 회귀, isClosed 저장 방식 비교와 R02 권고 성공 | Template·휴일·마감 원인 컬럼과 API | `mise run verify:m31-r01` |
| M31-R02 | 일정 운영 기반 스키마 | M31-R01 | Template·휴일·ScheduleConfigGuard·ScheduleDate·ScheduleAuditLog·member-day guard·독립 마감 원인과 R01 권고 isClosed 제약·Repository 통합 테스트 성공 | 동기화 Job·웹 | `mise run verify:m31-r02` |
| M31-R03 | ScheduleDate 3개월 horizon | M31-R02 | 양 끝 날짜 포함 초기화·다음 날 보충·동시 실행 중복 없음·과거 보존 | TimeSlot 생성 | `mise run verify:m31-r03` |
| M31-R04 | 정규 Template 관리 backend | M31-R02 | 설정 guard 배타 잠금·요일별 CRUD·45분·정원·월요일 Template·SYNCING version·감사 테스트 성공 | Controller·운영 진입점·정기 휴일·occurrence sync | `mise run verify:m31-r04` |
| M31-R05 | 정기 휴일 관리 backend | M31-R04 | 설정 guard 직렬화·같은 요일 기간 중복 및 동시 등록 방지·월요일 기본 규칙·영향 건수·감사 테스트 성공 | Controller·운영 진입점·날짜 휴무·웹 | `mise run verify:m31-r05` |
| M31-R06 | materialized occurrence 동기화 | M31-R03, M31-R05 | 오늘~3개월 생성·OPEN 우회·version별 반복/동시 멱등·TEMPLATE만 자동 마감·MANUAL/ADMIN 보존·부분 적용 재시작·상태/진행률·수동 재시도·장기 지속 로그·전체 적용 뒤 ACTIVE 전환 성공 | Controller·공개 API·예약 생성 | `mise run verify:m31-r06` |
| M31-R07 | 회원 신규 예약 3시간 경계 | M31-R03, M31-R06 | 정확히 3시간 전 성공·최소 정밀도 이후 실패·조회 숨김·직접 POST 차단 성공 | 기존 변경 마감 정책 | `mise run verify:m31-r07` |
| M31-R08 | 회원 활성 예약 overlap 불변식 | M31-R01, M31-R02 | 같은 트랜잭션 guard upsert/재잠금·overlap SQL/인덱스·DB/Java 활성 상태 계약·V15 재검증·통합 409·MySQL 경쟁·rollback 성공 | 가족 쿠폰 | `mise run verify:m31-r08` |
| M31-R09 | 날짜 CLOSING/CLOSED backend | M31-R06, M31-R08 | 미래 날짜 전이·확정 Command 차단/허용 matrix·영향 목록·stable 취소·0건 확정·취소 복귀·감사 성공 | 알림·일괄 취소 | `mise run verify:m31-r09` |
| M31-R10 | 개별 TimeSlot 휴강 backend | M31-R08, M31-R09 | 즉시 신규 차단·영향 목록·stable 취소·0건 확정·재개·당일 미래 슬롯 경계 성공 | 날짜 전체 휴무 UI | `mise run verify:m31-r10` |
| M31-R11 | API·OpenAPI·생성 Client | M31-R04~M31-R10 | 일정·휴일·날짜·휴강·동기화 상태/재시도 API, SYNCING 503, `SCHEDULE_*` prefix 확장, offset 시간, ErrorResponse details와 생성 Client 계약 성공 | 웹 화면 | `mise run verify:m31-r11` |
| M31-R12 | 정규 시간표·정기 휴일 웹 | M31-R11 | 검색 가능한 운영 폼·영향 미리보기·중복 제출 방지·320px·키보드·오류 코드 UX 성공 | 날짜 CLOSING 화면 | `mise run verify:m31-r12` |
| M31-R13 | 날짜 휴무·CLOSING 웹 | M31-R11 | 영향 예약 목록·건수·진행률·전용 취소·확정/복귀·개별 휴강 UX 성공 | 알림·일괄 취소 | `mise run verify:m31-r13` |
| M31-R14 | Checkpoint 1 재검증 | M31-R12, M31-R13 | migration preflight, MySQL 교차 동시성, OpenAPI, 웹 E2E와 전체 Gate 연속 2회 성공 | M31-06 이후 구현 | `mise run verify:m31-r14` |

## Migration 순서

1. 기존 TimeSlot·Reservation의 NULL·비정상 시작 시각, 자정 도달·초과, 종료 시각 계산
   불가와 동일 회원 활성 예약의 45분 구간 overlap을 읽기 전용 preflight로 출력한다.
   문제 유형, 테이블·ID, 날짜·시작·계산 종료 시각, 충돌 예약 ID와 유형별·전체 건수를
   보고하며 데이터를 자동 수정·삭제·병합하지 않는다.
2. R01에서 `time_slot_capacities.end_time`, `source`와 `reservations.end_time`만
   additive migration으로 추가한다.
3. 기존 TimeSlot을 `source = MANUAL`, `end_time = start_time + 45분`으로 backfill한다.
4. 기존 Reservation을 `end_time = start_time + 45분`으로 backfill한다.
5. R02에서 `template_id`, 세 마감 원인 플래그, Template, 정기 휴일,
   ScheduleConfigGuard, ScheduleDate, ScheduleAuditLog와 member-day guard를 추가한다.
6. R03에서 기존 TimeSlot·Reservation의 distinct 날짜와 현재 3개월 horizon으로
   ScheduleDate를 채운다. ScheduleDate의 `applied_config_version`은 초기 active
   version으로 설정한다.
7. R01은 45분·자정·source CHECK를 적용하고, R02는 단계적 이관의 최종 NOT NULL,
   Template FK·UNIQUE와 generated 마감 원인 OR 제약을 적용한다. overlap 조회 인덱스는
   실제 조회를 연결하는 R08의 소유 범위에서 적용한다.
8. V15는 수정하지 않는다. preflight가 실패하면 데이터를 자동 삭제하거나 병합하지 않고 중단한다.

## Lock matrix 목표

| Command | Config guard | ScheduleDate | Member-day guard | TimeSlot | Reservation | Coupon | Member | 비고 |
|---|---|---|---|---|---|---|---|---|
| 회원 예약 생성(쿠폰) | SHARE, ACTIVE | 대상 날짜 | 대상 회원·날짜 | 대상 ID | overlap + 대상 점유 | 선택 Coupon | 없음 | Idempotency가 최선행 |
| 회원 예약 생성(1회 결제) | SHARE, ACTIVE | 대상 날짜 | 대상 회원·날짜 | 대상 ID | overlap + 대상 점유 | 없음 | 없음 | Idempotency가 최선행 |
| 관리자 수동 예약(쿠폰) | SHARE, ACTIVE | 대상 날짜 | 대상 회원·날짜 | 대상 ID | overlap + 대상 점유 | 선택 Coupon | 없음 | M31-09에서 구현 |
| 관리자 수동 예약(1회 결제) | SHARE, ACTIVE | 대상 날짜 | 대상 회원·날짜 | 대상 ID | overlap + 대상 점유 | 없음 | 없음 | M31-09에서 구현 |
| 예약 변경(쿠폰) | SHARE, ACTIVE | 원본·대상 날짜순 | 원본·대상 회원·날짜순 | ID순 | 원본·대상 overlap과 대상 예약 | 기존 Coupon | 없음 | CLOSING 대상 차단 |
| 예약 변경(1회 결제) | SHARE, ACTIVE | 원본·대상 날짜순 | 원본·대상 회원·날짜순 | ID순 | 원본·대상 overlap과 대상 예약 | 없음 | 없음 | CLOSING 대상 차단 |
| 입금 만료 복구 | SHARE, ACTIVE | 예약 날짜 | 대상 회원·날짜 | 대상 ID | overlap·점유와 대상 예약 | 없음 | 없음 | 활성 집합 재진입 |
| 예약 승인 | 없음 | 예약 날짜 | 없음 | 없음 | 대상 예약 | 없음 | 없음 | 활성→활성, CLOSING 차단 |
| 입금 확인 | 없음 | 예약 날짜 | 없음 | 없음 | 대상 예약 | 없음 | 없음 | 활성→활성, CLOSING 차단 |
| 예약 반려(쿠폰) | 없음 | 예약 날짜 | 대상 회원·날짜 | 없음 | 대상 예약 | 연결 Coupon | 없음 | 활성 이탈과 점유 반환 |
| 예약 반려(1회 결제) | 없음 | 예약 날짜 | 대상 회원·날짜 | 없음 | 대상 예약 | 없음 | 없음 | 활성 이탈 |
| 자동 입금 만료 | 없음 | 예약 날짜 | 대상 회원·날짜 | 없음 | 대상 예약 | 없음 | 없음 | PENDING_PAYMENT 이탈 |
| 자동 승인 만료(쿠폰) | 없음 | 예약 날짜 | 대상 회원·날짜 | 없음 | 대상 예약 | 연결 Coupon | 없음 | 활성 이탈과 점유 반환 |
| 자동 승인 만료(1회 결제) | 없음 | 예약 날짜 | 대상 회원·날짜 | 없음 | 대상 예약 | 없음 | 없음 | 활성 이탈 |
| 수업 완료(기승 횟수 증가·쿠폰) | 없음 | 없음 | 없음 | 없음 | 대상 예약 | 연결 Coupon | 대상 회원 | 시작 이후 최소 잠금 |
| 수업 완료(기승 횟수 증가·1회 결제) | 없음 | 없음 | 없음 | 없음 | 대상 예약 | 없음 | 대상 회원 | 시작 이후 최소 잠금 |
| 수업 완료(기승 횟수 불변·쿠폰) | 없음 | 없음 | 없음 | 없음 | 대상 예약 | 연결 Coupon | 없음 | 시작 이후 최소 잠금 |
| 수업 완료(기승 횟수 불변·1회 결제) | 없음 | 없음 | 없음 | 없음 | 대상 예약 | 없음 | 없음 | 시작 이후 최소 잠금 |
| 노쇼(쿠폰) | 없음 | 없음 | 없음 | 없음 | 대상 예약 | 연결 Coupon | 없음 | 시작 이후 최소 잠금 |
| 노쇼(1회 결제) | 없음 | 없음 | 없음 | 없음 | 대상 예약 | 없음 | 없음 | 시작 이후 최소 잠금 |
| 수동 TimeSlot 생성 | SHARE, ACTIVE | 대상 날짜 | 없음 | 생성 UNIQUE key | 없음 | 없음 | 없음 | SYNCING 차단 |
| TimeSlot 정원 변경 | 없음 | 대상 날짜 | 없음 | 대상 ID | 슬롯 활성 예약 ID순 | 없음 | 없음 | 동기화와 날짜·슬롯 잠금으로 직렬화 |
| 날짜 CLOSING 시작·확정 | 없음 | 대상 날짜 | 없음 | 없음 | 날짜 활성 예약 ID순 | 없음 | 없음 | 확정 시 0건 재검사 |
| 날짜 휴무 정리 취소(쿠폰) | 없음 | 대상 날짜 | 대상 회원·날짜 | 없음 | 대상 예약 | 원래 Coupon | 없음 | stable/RETURN |
| 날짜 휴무 정리 취소(1회 결제) | 없음 | 대상 날짜 | 대상 회원·날짜 | 없음 | 대상 예약 | 없음 | 없음 | stable/NONE |
| TimeSlot 휴무 정리 취소(쿠폰) | 없음 | 대상 날짜 | 대상 회원·날짜 | 대상 ID | 대상 예약 | 원래 Coupon | 없음 | stable/RETURN |
| TimeSlot 휴무 정리 취소(1회 결제) | 없음 | 대상 날짜 | 대상 회원·날짜 | 대상 ID | 대상 예약 | 없음 | 없음 | stable/NONE |
| 개별 TimeSlot 휴강 | 없음 | 대상 날짜 | 없음 | 대상 ID | 슬롯 활성 예약 ID순 | 없음 | 없음 | 먼저 admin_closed 적용 |
| occurrence 동기화 | SHARE, pending version | 대상 날짜 | 없음 | 기존 행 ID 또는 UNIQUE | 없음 | 없음 | 없음 | applied version 기록, 영향 조회는 별도 |
| Template·휴일 변경 | UPDATE, ACTIVE→SYNCING | 없음 | 없음 | 없음 | 없음 | 없음 | 없음 | 겹침 검사와 version 커밋 |
| 설정 동기화 완료 | UPDATE, pending→ACTIVE | 없음 | 없음 | 없음 | 없음 | 없음 | 없음 | 전체 horizon version 재검사 |

공통 순서는 `Idempotency → ScheduleConfigGuard → ScheduleDate → member-day guard →
TimeSlot → Reservation → Coupon → Member → append-only audit`이다. 여러 행은 각 키 또는
ID 오름차순으로 잠근다. 설정 상태에 종속되지 않는 안전한 종료 Command는 Config guard를
건너뛰되 뒤 단계의 순서를 역전하지 않는다.

## 추가 필수 검증 시나리오

- M31-R01: generated `is_closed`와 mutable+CHECK를 현재 JPA·직접 SQL 쓰기 경로로
  비교하고 R02 권고와 영향 경로를 기록한다. R01은 기존 mutable 필드와 개별 마감·재개
  API 회귀를 유지하고 마감 원인 컬럼을 조기 구현하지 않는다.
- M31-R06: 일부 ScheduleDate만 pending version을 적용한 뒤 프로세스를 중단하고
  재시작해 같은 version으로 완료한다. 이미 적용한 날짜는 중복 변경하지 않는다.
- M31-R06: 정기 휴일과 Template 비활성화가 TEMPLATE 행만 닫고 MANUAL 행은 유지한다.
- M31-R06: 기존 활성 예약이 남은 TEMPLATE 행을 닫아도 version 동기화 후 ACTIVE로
  복귀하고 별도 영향 목록에서 남은 예약을 조회한다.
- M31-R06: 장기 SYNCING과 실패가 version·진행률·비식별 실패 코드가 포함된 구조화 로그를
  남긴다.
- M31-R08: 모든 ReservationStatus에서 Java 활성 상태 정의와 DB
  `active_slot_guard`가 일치한다.
- M31-R11: SYNCING 차단 대상은 동일한 503 ErrorResponse를 반환하고 허용 대상은
  정상 응답한다. 불안정한 추정만으로 `Retry-After`를 반환하지 않는다.

## Checkpoint Gate

```bash
mise run harness:loop-ready
mise run docs:check
mise run backend:convention
mise run backend:check
mise run backend:integration
mise run api:check
mise run web:lint
mise run web:typecheck
mise run web:test
mise run verify:all
mise run verify:all
```

M31-R14는 설정 변경과 예약 유입, 정기 휴일 동시 등록, 자동 occurrence와 휴무,
예약·변경·복구·취소·출석의 MySQL 교차 동시성 테스트와 핵심 Playwright E2E가
`verify:all`에 포함된 경우에만 완료한다.
