# Package Rule

기능을 먼저 나누고 기능 내부에서 계층을 나눈다. 기존 복수형 기능 이름을 유지한다.

```text
com.horse.members
  presentation
  application
  domain
  infrastructure

com.horse.reservations
  presentation
  application
  domain
  infrastructure

com.horse.global
  exception
  config
```

## Required

- `Controller`는 기능의 `presentation`에 둔다.
- `Service`는 기능의 `application`에 둔다.
- Entity와 도메인 정책은 기능의 `domain`에 둔다.
- Repository 구현과 영속성 기술은 기능의 `infrastructure`에 둔다.
- 전체 기능을 가로지르는 예외 처리와 설정만 `global`에 둔다.
- 전역 `controller`, `service`, `repository` 패키지를 만들지 않는다.
