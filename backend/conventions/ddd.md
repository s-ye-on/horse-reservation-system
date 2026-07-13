# Domain Driven Design Rule

## Entity

- Entity는 자신의 상태를 스스로 변경한다.
- public setter를 만들지 않는다.
- `approve...`, `change...`, `increase...`, `decrease...`처럼 의도를 드러내는 메서드를 사용한다.
- 도메인 검증은 Entity 또는 관련 도메인 타입 내부에서 수행한다.
- 검증 실패는 `BusinessException`의 기능별 하위 예외로 표현한다.

```java
member.approveDressage();
member.increaseRideCount();
member.changePhone(phone);
```

다음 형태는 금지한다.

```java
member.setPhone(phone);
member.setDressageApproved(true);
```

## Constructor

- JPA 기본 생성자는 `protected`로 둔다.
- 비즈니스 생성자는 `private`로 둔다.
- 외부 생성은 `create()`, `of()`, `from()` 정적 팩토리를 사용한다.
- JPA Entity에 `record`를 사용하지 않는다.
