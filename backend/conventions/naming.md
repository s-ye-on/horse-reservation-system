# Naming Rule

## Factory

- `create()`: 새 도메인 객체 생성
- `of()`: 여러 값으로 값 객체 구성
- `from()`: 다른 타입에서 변환

## Query

- `findBy...`: 없을 수 있는 단건 조회
- `get...`: 반드시 존재해야 하는 값 조회
- `exists...`: 존재 여부 조회

## State Change

- 승인: `approve...`
- 값 변경: `change...`
- 증가: `increase...`
- 감소: `decrease...`

## Type Suffix

- 요청: `MemberCreateRequest`, `MemberUpdateRequest`
- 응답: `MemberResponse`
- 예외: `MemberException`, `ReservationException`
- 오류 코드: `MEMBER_INVALID_*`, `MEMBER_NOT_FOUND`, `MEMBER_ALREADY_*`
