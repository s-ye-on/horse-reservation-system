# Coding Style

## Required

- public setter를 만들지 않는다.
- JPA Entity 이외의 불변 DTO와 값 타입에는 `record`를 우선 검토한다.
- 설명 없는 magic number를 사용하지 않고 의미 있는 상수나 도메인 타입으로 표현한다.
- JPA 기본 생성자는 `protected`, 비즈니스 생성자는 `private`로 둔다.
- 생성 규칙이 있는 객체는 정적 팩토리 메서드를 제공한다.
- `null` collection과 `null` Optional을 반환하지 않는다.
- 생성자 주입 의존성과 불변 지역 값에는 `final`을 우선한다.
- Builder는 선택 인자가 많아 생성 의도가 더 명확해질 때만 사용한다.

## Optional

- 단건 Repository 조회 반환에는 사용할 수 있다.
- Entity 필드, DTO 필드, 메서드 인자, collection 반환에는 사용하지 않는다.

magic number, Optional, Builder 필요성은 문맥 의존적이므로 자체 검토와 테스트에서도 확인한다.
