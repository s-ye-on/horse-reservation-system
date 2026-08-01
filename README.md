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

로그인 구현 전 회원·관리자 화면을 로컬에서 확인할 때는 인증된 데모 환경을 사용한다.
백그라운드에서 MySQL, 백엔드, JWT 프록시, Vite를 함께 실행한다.

```bash
mise run demo:up
mise run demo:status
# 종료할 때
mise run demo:down
```

데모 화면은 `http://127.0.0.1:5173`에서 확인한다. `web:dev`와 `backend:dev`는
각 서버만 직접 실행하므로 로그인 토큰을 자동으로 주입하지 않는다.

모바일은 Expo 개발 서버를 사용한다.

```bash
mise run mobile:dev
```

## 운영 실행 계약

운영 백엔드는 반드시 `prod` 프로필과 외부 JWT Secret을 함께 주입해서 실행한다.
실제 Secret은 저장소의 설정 파일, Compose, 문서 또는 이미지에 기록하지 않는다.

```bash
export SPRING_PROFILES_ACTIVE=prod
export JWT_SECRET="${JWT_SECRET_FROM_SECRET_STORE:?외부 Secret 주입 필요}"
java -jar backend/build/libs/horse-backend-0.0.1-SNAPSHOT.jar
```

`prod`에서 `JWT_SECRET`이 누락되거나 비어 있거나 32바이트 미만이거나 개발 기본값·알려진
placeholder이면 애플리케이션이 시작되지 않는다. 기본 프로필의 개발 fallback은 로컬 실행
전용이므로 운영 배포에서 `SPRING_PROFILES_ACTIVE=prod`를 생략해서는 안 된다.

## 검증

```bash
mise run harness:self-check
mise run verify:changed
mise run verify:all
```

`verify:changed`는 Git 저장소가 아직 초기화되지 않은 경우 전체 검증을 실행한다.
OpenAPI 생성 검증은 `M0-05` 구현 후 품질 게이트에 포함한다.
