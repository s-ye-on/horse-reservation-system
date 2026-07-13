# Database Rule

## Required

- JPA 연관관계는 `FetchType.LAZY`를 기본으로 한다.
- Cascade는 함께 생명주기를 가져야 하는 관계에만 최소로 사용한다.
- DDL은 Flyway migration으로 명시적으로 관리한다.
- Hibernate `ddl-auto`는 스키마 검증에 사용한다.
- DB table과 column은 `snake_case`, Java 타입과 필드는 `camelCase`를 사용한다.
- Repository는 영속성·조회 기술을 담당하고 비즈니스 정책을 판단하지 않는다.

## Query Review

- 목록 조회는 N+1 가능성을 확인한다.
- 필요한 연관 데이터는 fetch join 또는 EntityGraph로 명시한다.
- 성능에 민감한 조회는 query count 또는 통합 테스트로 검증한다.
