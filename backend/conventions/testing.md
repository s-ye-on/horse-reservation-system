# Testing Rule

## Test Type

- Entity와 순수 도메인 정책은 Domain Test를 작성한다.
- 비즈니스 동작이 있는 모든 Application Service는 테스트한다.
- Repository는 slice test를 사용한다.
- MySQL, Flyway, 잠금, 제약 조건 의미가 중요하면 Testcontainers의 MySQL을 연결한다.
- Controller는 API 또는 web slice test로 DTO, 검증, 권한 경계를 확인한다.
- 예외 경로와 경계값을 테스트한다.

## Style

- 테스트 메서드 이름은 한글로 작성하고 단어 사이는 `_`로 구분한다.
- Given, When, Then 단계는 빈 줄로 구분해 읽을 수 있게 한다.
- assertion을 약화하거나 테스트를 삭제해 검증을 통과시키지 않는다.
