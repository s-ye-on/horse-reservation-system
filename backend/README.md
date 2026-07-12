# Backend

Java 21과 Spring Boot 4.1 기반의 마장 예약 서비스 API다.

## 기술 스택

- Spring Boot, Spring MVC, Spring Security
- Spring Data JPA, Hibernate
- MySQL 8.4, Flyway
- Gradle Wrapper
- JUnit, Testcontainers

## 실행

루트에서 다음 명령을 실행한다.

```bash
mise run db:up
mise run backend:dev
```

헬스 체크는 `http://localhost:8080/actuator/health`에서 확인한다.

```bash
mise run backend:check
mise run backend:build
mise run backend:integration
```

데이터베이스 연결값은 루트 `.env`에서 관리하며, 스키마 변경은
`src/main/resources/db/migration`의 Flyway 마이그레이션으로만 적용한다.
