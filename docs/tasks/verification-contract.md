# 검증 명령 계약

코드가 아직 없더라도 검증 명령의 이름과 책임은 먼저 계약한다. 실제 명령은 M0-00부터 구현한다.

## 공통 명령

```bash
mise tasks --all
mise run harness:self-check
mise run docs:check
mise run verify:changed
mise run verify:all
```

- `harness:self-check`: 런타임, Wrapper, pnpm workspace, 필수 task 존재 확인
- `docs:check`: Markdown 링크, 빈 템플릿 값, 상태 enum 불일치 검사
- `verify:changed`: 변경된 앱과 패키지만 포맷·정적 검사·테스트
- `verify:all`: 백엔드, 웹, 모바일, 계약 검증 전체 실행

## 영역 명령

```bash
mise run backend:check
mise run backend:test
mise run backend:integration
mise run backend:build

mise run web:lint
mise run web:typecheck
mise run web:test
mise run web:build

mise run mobile:lint
mise run mobile:typecheck
mise run mobile:test
mise run mobile:export

mise run api:generate
mise run api:check
mise run db:migrate:test
```

## 작업별 명령

모든 backlog 항목은 `.mise/tasks/verify/<task-id>`에 대응하는 `mise run verify:<task-id>` 명령을 가진다. 이 명령은 해당 작업에 필요한 가장 작은 테스트만 실행하고, 성공하지 않으면 작업을 완료할 수 없다.

## 부트스트랩 예외

M0-00 시작 전에는 mise task가 없을 수 있다. M0-00은 다음 명령으로 완료를 검증한다.

```bash
mise tasks --all
mise run harness:self-check
mise run docs:check
```

두 번째 작업부터 `mise run verify:<task-id>`가 없는 상태는 중단 조건이다.

## 검증 수준

- 문서 변경: 링크와 용어·상태 enum 일관성
- 도메인 정책: 단위 테스트와 경계값 테스트
- JPA·정원·쿠폰: MySQL Testcontainers 통합 테스트
- API: Spring MockMvc 또는 통합 테스트와 OpenAPI diff
- 웹·모바일: 타입 검사, 컴포넌트 테스트, 핵심 흐름 E2E
- 동시성: 동일 자원에 대한 병렬 요청 통합 테스트

## 실패 처리

검증 실패를 기존 오류로 간주해 무시하지 않는다. 현재 변경과 무관함을 증명할 수 없으면 `BLOCKED`로 처리하고 실패 명령과 로그를 남긴다.

