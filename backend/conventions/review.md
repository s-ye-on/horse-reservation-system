# AI Self Review

모든 백엔드 작업 완료 후 확인한다. 자동 검증 결과를 이 체크리스트로 대체할 수 없다.

## Architecture

- [ ] Controller가 Entity나 Repository를 직접 사용하는가?
- [ ] Controller가 Request/Response DTO 경계를 지키는가?
- [ ] Service가 흐름과 트랜잭션만 조합하고 Entity 메서드를 호출하는가?
- [ ] Domain이 상위 계층이나 infrastructure에 의존하는가?

## Domain

- [ ] public setter가 없는가?
- [ ] Entity가 자신의 상태를 의미 있는 메서드로 변경하는가?
- [ ] JPA 생성자는 `protected`, 비즈니스 생성자는 `private`인가?
- [ ] 외부 생성에 정적 팩토리를 사용하는가?

## Exception

- [ ] `IllegalArgumentException`이나 원시 `RuntimeException`을 생성하지 않는가?
- [ ] 업무 예외가 `BusinessException`을 상속하는가?
- [ ] `ExceptionCode`에 올바른 도메인 prefix를 사용했는가?
- [ ] `GlobalExceptionHandler`에서만 `ErrorResponse`를 생성하는가?

## Code And Test

- [ ] 설명 없는 magic number와 불필요한 Optional, Builder가 없는가?
- [ ] DTO에 적절히 `record`를 사용했는가?
- [ ] 변경 동작과 예외 경로의 적절한 테스트를 작성했는가?
- [ ] `mise run backend:convention`이 성공하는가?
