# MVP 0 Backlog: 하네스와 프로젝트 골격

이 문서는 backlog다. 실행 전 각 행을 `TEMPLATE.md` 형식의 active task로 만든다. 모든 항목은 템플릿의 공통 중단 조건을 상속한다.

| ID | 단일 작업 | 의존성 | 완료 신호 | 제외 범위 | 검증 명령 |
|---|---|---|---|---|---|
| M0-00 | mise 하네스 부트스트랩 | 없음 | 공통·영역별 task가 목록에 나타남 | 애플리케이션 기능 구현 | `mise run harness:self-check`, `mise run docs:check` |
| M0-01 | Spring Boot Gradle 골격 | M0-00 | 빈 애플리케이션 테스트·빌드 성공 | 인증·도메인·DB 스키마 | `mise run verify:m0-01` |
| M0-02 | MySQL 개발 환경과 Flyway 연결 | M0-01 | 빈 마이그레이션이 MySQL에 적용됨 | 도메인 테이블 생성 | `mise run verify:m0-02` |
| M0-03 | React TypeScript Vite 웹 골격 | M0-00 | 기본 라우트 타입 검사·빌드 성공 | 실제 화면·API 연동 | `mise run verify:m0-03` |
| M0-04 | Spring Security 역할 골격 | M0-01 | MEMBER·ADMIN 접근 경계 테스트 성공 | 회원 가입·로그인 UI | `mise run verify:m0-04` |
| M0-05 | OpenAPI 생성 클라이언트 파이프라인 | M0-01, M0-03 | 생성 후 변경 없음 검사 성공 | 실제 도메인 endpoint | `mise run verify:m0-05` |
| M0-06 | 변경 범위 검증 task | M0-01, M0-03 | backend·web 변경 감지 검증 성공 | CI 공급자 설정 | `mise run verify:m0-06` |
| M0-07 | CI 기본 품질 게이트 | M0-06 | 새 checkout에서 `verify:all` 성공 | 배포·릴리스 자동화 | `mise run verify:m0-07` |

M0-00 이후 작업별 검증 명령이 존재하지 않으면 해당 작업을 시작하지 않는다.

