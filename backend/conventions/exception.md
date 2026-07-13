# Exception Rule

## Structure

```text
RuntimeException
-> BusinessException
   -> MemberException
   -> ReservationException
   -> AuthException
```

## Required

- `BusinessException`만 `RuntimeException`을 직접 상속할 수 있다.
- 업무 예외는 반드시 `BusinessException`의 하위 타입으로 만든다.
- 운영 코드에서 `IllegalArgumentException`과 원시 `RuntimeException`을 생성하지 않는다.
- 예외 메시지와 외부 오류 코드는 `ExceptionCode`가 관리한다.
- 기능별 예외는 문자열이 아니라 `ExceptionCode`를 받는다.
- `GlobalExceptionHandler`에서만 `ErrorResponse`를 생성한다.

```java
throw new MemberException(ExceptionCode.MEMBER_INVALID_NAME);
```

## ExceptionCode

현재는 단일 enum을 사용한다.

- 회원: `MEMBER_*`
- 인증: `AUTH_*`
- 예약: `RESERVATION_*`
- 말: `HORSE_*`
- 공통: `COMMON_*`

`code()`, `message()`, `status()` 계약을 유지한다. enum이 비대해지거나 기능별 소유권과 메타데이터가 달라지면 `ErrorCode` 인터페이스와 기능별 enum으로 분리한다.
