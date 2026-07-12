# Frontend

웹과 모바일 클라이언트를 관리하는 pnpm workspace다.

## 구조

```text
frontend/
  apps/web/       React + TypeScript + Vite
  apps/mobile/    React Native + Expo Router
  packages/       API 클라이언트, 공통 타입, 순수 유틸리티
```

## 실행과 검증

루트에서 다음 명령을 실행한다.

```bash
mise run web:dev
mise run mobile:dev
mise run verify:m0-03
mise run mobile:lint
mise run mobile:typecheck
mise run mobile:test
mise run mobile:export
```

생성형 OpenAPI 클라이언트는 `M0-05` 작업에서 `packages/api-client`에 연결한다.
