# MVP 4 Backlog: 회원 모바일 앱

| ID | 단일 작업 | 의존성 | 완료 신호 | 제외 범위 | 검증 명령 |
|---|---|---|---|---|---|
| M4-00 | Expo TypeScript 앱 골격 | M31-17 | Android·iOS 개발 빌드와 타입 검사 성공 | 회원 기능·웹 변경 | `mise run verify:m4-00` |
| M4-01 | 모바일 JWT 로그인 상태 | M31-17, M4-00 | 토큰 복원·만료·로그아웃 테스트 성공 | 회원 가입·생체 인증 | `mise run verify:m4-01` |
| M4-02 | 모바일 예약 달력 | M32-10, M4-01 | 클래스·날짜·시간 조회 화면 테스트 성공 | 예약 제출 | `mise run verify:m4-02` |
| M4-03 | 모바일 예약 신청 | M4-02, M1-26 API | 쿠폰·입금대기 제출 흐름 테스트 성공 | 결제 연동 | `mise run verify:m4-03` |
| M4-04 | 모바일 내 예약 | M4-01, M1-27 API | 모든 상태와 상세 화면 테스트 성공 | 변경·취소 | `mise run verify:m4-04` |
| M4-05 | 모바일 쿠폰 내역 | M4-01, M1-28 API | 잔여·점유·사용 이력 화면 테스트 성공 | 쿠폰 구매 | `mise run verify:m4-05` |
| M4-06 | 모바일 예약 변경·취소 | M4-04, MVP 2 API | 변경·취소 정책 결과 표시 성공 | 관리자 예외 | `mise run verify:m4-06` |
| M4-07 | 모바일 핵심 E2E와 export | M4-01, M4-02, M4-03, M4-04, M4-05, M4-06 | Android·iOS 핵심 흐름과 export 성공 | 스토어 제출·푸시 | `mise run verify:m4-07` |

모든 항목은 `TEMPLATE.md`의 공통 중단 조건을 상속한다.

`M4-00`과 `M4-01`만 Phase A 완료 후 진행할 수 있다. 예약·쿠폰 API를 직접 사용하는
`M4-02~M4-06`은 Phase B Gate와 OpenAPI diff 승인 후 시작한다.
