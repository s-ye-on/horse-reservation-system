# Horse Reservation

마장 예약 서비스 모노레포다. 정책과 구현 범위는 `docs/`를 기준으로 한다.

## 구성

- `backend/`: Java 21, Spring Boot 4.1, JPA, MySQL, Flyway
- `frontend/apps/web/`: React, TypeScript, Vite
- `frontend/apps/mobile/`: React Native, Expo
- `frontend/packages/`: 클라이언트 간 공유 패키지
- `compose.yaml`: 로컬 MySQL 8.4
- `mise.toml`: 런타임 버전과 공통 작업

## 최초 실행

```bash
cp .env.example .env
mise install
mise run install
mise run db:up
mise run backend:dev
```

별도 터미널에서 웹을 실행한다.

```bash
mise run web:dev
```

모바일은 Expo 개발 서버를 사용한다.

```bash
mise run mobile:dev
```

## 검증

```bash
mise run harness:self-check
mise run verify:changed
mise run verify:all
```

`verify:changed`는 Git 저장소가 아직 초기화되지 않은 경우 전체 검증을 실행한다.
OpenAPI 생성 검증은 `M0-05` 구현 후 품질 게이트에 포함한다.
