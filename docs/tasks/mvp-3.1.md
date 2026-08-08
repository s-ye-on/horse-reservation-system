# MVP 3.1 Backlog: 운영 안정화

각 Checkpoint가 끝나면 검증 결과, 남은 위험과 다음 구간 진행 가능 여부를 보고한다.

2026-07-23 운영 정책 변경으로 Checkpoint 1을 다시 열었다. 기존 M31-00~M31-05의 완료
이력은 유지하고 [Checkpoint 1 보정 Backlog](mvp-3.1-checkpoint-1-remediation.md)의
M31-R00~M31-R14를 완료해야 M31-06을 시작할 수 있다.

## Checkpoint 1: M31-00~M31-05

| ID | 단일 작업 | 의존성 | 완료 신호 | 제외 범위 | 검증 명령 |
|---|---|---|---|---|---|
| M31-00 | 운영 안정화 정책·Task 계약 | M3-18 | SSOT·3개 Checkpoint·Gate 문서 검증 성공 | 구현 코드·DB | `mise run verify:m31-00` |
| M31-01 | 당일 신규 예약 시간 정책 | M31-00 | 수업 시작 직전 허용·정각 거부 Domain 경계 성공 | 조회·예약 생성 API | `mise run verify:m31-01` |
| M31-02 | 회원 예약 가능 조회와 신청 시간 가드 | M31-01 | 조회 숨김·Read Model·직접 API 차단 성공 | 관리자 수동 예약 | `mise run verify:m31-02` |
| M31-03 | 승인·반려와 입금 만료 시각 정합성 | M31-01 | 시작 후 승인·반려 차단과 실제 `paymentDueAt` 일치 | Scheduler 주기 변경 | `mise run verify:m31-03` |
| M31-04 | 지난 TimeSlot 일반 관리 차단 | M31-01 | 생성·마감·정원·삭제 차단과 과거 조회 분리 성공 | 유지보수 데이터 삭제 API | `mise run verify:m31-04` |
| M31-05 | 동일 회원·동일 고정 슬롯 활성 중복 방지 | M31-02 | 이관 preflight·DB UNIQUE·동시 신청 하나만 성공 | 수업 구간 overlap 모델 | `mise run verify:m31-05` |

## Checkpoint 2: M31-06~M31-12

| ID | 단일 작업 | 의존성 | 완료 신호 | 제외 범위 | 검증 명령 |
|---|---|---|---|---|---|
| M31-06 | Command lock matrix와 MySQL 실행 계획 | M31-R14 | Command별 SQL·잠금·인덱스·EXPLAIN 문서와 회귀 기준 확정 | 잠금 구현 변경 | `mise run verify:m31-06` |
| M31-07 | Canonical lock order와 deadlock 제한 재시도 | M31-06 | 구조적 역전 제거·1213 최대 3회·논리적 exactly-once 성공 | 일반 업무 예외 재시도 | `mise run verify:m31-07` |
| M31-08 | 예약 생성 멱등성 원장 | M31-07 | 원자 커밋·동일 응답·hash 충돌·장애 주입 rollback 성공 | 변경·취소 API 멱등성 확장 | `mise run verify:m31-08` |
| M31-09 | 관리자 수동 예약 생성 | M31-08, M31-R11 | 쿠폰 `confirmed`·무쿠폰 `pending_payment`·ScheduleDate 검증·감사 성공 | 가족 쿠폰 후보 확장 | `mise run verify:m31-09` |
| M31-10 | MVC·Security ErrorResponse 통일 | M31-00 | 400·401·403·404·405·409·500 계약 성공 | 외부 오류 추적 서비스 | `mise run verify:m31-10` |
| M31-11 | Page 기반 조회 계약과 누락 상세 API | M31-05 | 공통 6필드 Page·안정 정렬·소유권 있는 상세 조회 성공 | Cursor 페이지네이션 | `mise run verify:m31-11` |
| M31-12 | RFC3339·required OpenAPI와 생성 Client | M31-09, M31-11 | offset·required·다중 기기 시간대 표시 계약 성공 | 모바일 예약 화면 | `mise run verify:m31-12` |

## Checkpoint 3: M31-13~M31-17

| ID | 단일 작업 | 의존성 | 완료 신호 | 제외 범위 | 검증 명령 |
|---|---|---|---|---|---|
| M31-13A | 회원 예약 Page 서버 필터 계약 | M31-11, M31-12 | 표시 그룹·상태 필터가 DB Page와 OpenAPI에 반영되고 기존 혼합 조회가 유지됨 | 웹 Page 탐색 UI·별도 count API | `mise run verify:m31-13a` |
| M31-13 | 웹 Page 조회 UX | M31-13A | 관리자·회원 목록 전체 페이지 탐색과 상태 UX 성공 | CSV 다운로드 | `mise run verify:m31-13` |
| M31-14 | 관리자 감사 CSV 다운로드 UI | M31-12 | 현재 필터·인증·파일명·중복 제출 E2E 성공 | Sheets 동기화 | `mise run verify:m31-14` |
| M31-15 | 운영 JWT secret 강제 | M31-10 | secret 없는 운영 시작 실패와 demo 격리 성공 | 실제 로그인·키 회전 | `mise run verify:m31-15` |
| M31-16 | OpenAPI·E2E·병렬 빌드 하네스 격리 | M31-12, M31-15 | 전용 포트·build 격리·중요 E2E의 `verify:all` 포함 성공 | CI 병렬 분산 | `mise run verify:m31-16` |
| M31-16B0 | 웹 인증 Cookie·CSRF 계약 | M31-16A2 | Access Token JSON·Refresh HttpOnly Cookie·STATELESS CSRF·CORS와 기존 JSON API 호환 성공 | React 인증 상태·자동 refresh·브라우저 E2E | `mise run verify:m31-16b0` |
| M31-16B1 | 웹 로그인과 메모리 인증 상태 | M31-16B0 | signup→login→`/me`·Bearer·권한 Route·logout과 메모리 Token 검증 성공 | 자동 refresh·세션 복구·동시 401·브라우저 인증 E2E | `mise run verify:m31-16b1` |
| M31-17 | Phase A 전체 품질 Gate | M31-13, M31-14, M31-16 | preflight와 `verify:all` 연속 2회 및 Checkpoint 보고 승인 | Phase B 구현 | `mise run verify:m31-17` |

모든 항목은 [실행 작업 템플릿](TEMPLATE.md)의 중단 조건을 상속한다. 백엔드 코드를
변경하는 Task에는 `backend:convention`을 적용하고, 모든 Task에는 작업별 verify와
`verify:changed`를 적용한다. 담당 영역이 아닌 기존 변경 파일은 수정하거나 커밋하지 않는다.
