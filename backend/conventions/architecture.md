# Architecture Rule

## Runtime Flow

```text
Controller
-> Application Service
-> Repository에서 Entity 조회
-> Entity 상태 변경
-> Repository 저장
```

## Dependency Direction

```text
presentation -> application -> domain
infrastructure -> domain
```

Domain은 presentation, application, infrastructure에 의존하지 않는다.

## Required

- Controller는 요청 검증, DTO 변환, Service 호출, 응답 반환만 담당한다.
- Controller는 Entity 또는 Repository를 직접 사용하지 않는다.
- Service는 트랜잭션과 비즈니스 흐름을 조합하고 Entity의 의미 있는 메서드를 호출한다.
- Service는 Entity 필드를 직접 변경하지 않는다.
- Repository는 저장과 조회 기술을 담당하며 비즈니스 정책을 판단하지 않는다.
- 도메인에 필요한 Repository 조회 메서드는 허용한다.

## Example

```text
GOOD: Controller -> MemberCreateRequest -> MemberService -> Member -> MemberResponse
BAD:  Controller -> Member
BAD:  Controller -> MemberRepository
```
