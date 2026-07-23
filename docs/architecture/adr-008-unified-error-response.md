# ADR-008: 통합 오류 응답 계약

## 상태

Accepted

## 배경

업무 예외는 `ErrorResponse`로 변환되지만 Spring MVC의 `@Valid` 및 바인딩 오류는
기본 응답을 사용하고 있다. 클라이언트가 오류 종류마다 다른 JSON을 해석하지 않도록
오류 응답의 단일 계약이 필요하다. 기존 API는 `code`와 `message`를 사용하므로 이 두
필드는 호환성을 위해 유지해야 한다.

## 결정

- Spring MVC에서 처리하는 업무 예외와 요청 검증·바인딩 예외는 하나의
  `ErrorResponse` 형식을 사용한다.
- 응답 필드는 `code`, `message`, `status`, `timestamp`, `path`, `fieldErrors`와 선택적
  `details`로 한다.
- 기존 `code`와 `message`의 의미는 변경하지 않는다.
- 업무 예외의 `fieldErrors`는 빈 배열로 반환한다.
- 검증 오류는 `COMMON_INVALID_REQUEST`를 사용하며 필드명과 공개 가능한 검증 메시지만
  `fieldErrors`에 담는다. 거부된 입력값은 노출하지 않는다.
- `details`는 클라이언트가 후속 조치를 수행하는 데 필요한 구조화된 공개 정보에만
  사용한다. 날짜 휴무 확정 충돌은 `activeReservationCount`를 제공하며 회원 개인정보,
  JWT subject와 내부 예외 정보는 포함하지 않는다.
- 오류 응답 생성은 `ErrorResponse` 정적 팩토리와 `GlobalExceptionHandler`에서 담당한다.
  Controller는 `ErrorResponse`를 직접 생성하지 않는다.
- `MethodArgumentNotValidException`과 `BindException`은 같은 변환 규칙을 사용한다.
- 인증 필터 경계와 예상하지 못한 서버 오류의 공개 정책은 별도 보안·운영 작업에서
  확장한다. 이번 결정은 Spring MVC 업무·검증 예외 통합을 우선한다.

## 검토한 대안

### RFC 9457 `ProblemDetail`로 전환

표준성이 높지만 기존 `code`와 `message` 중심 계약 및 생성 클라이언트에 불필요한
마이그레이션 비용이 발생하므로 채택하지 않는다.

### 검증 전용 응답 DTO 추가

변경량은 작지만 오류 응답 SSOT가 둘로 나뉘어 클라이언트 분기와 유지보수 비용이
증가하므로 채택하지 않는다.

## 결과

- 기존 클라이언트는 `code`와 `message`를 계속 사용할 수 있다.
- 신규 클라이언트는 HTTP 상태, 발생 시각, 요청 경로와 필드 오류를 일관되게 표시할 수 있다.
- 필드를 추가하는 확장 변경이므로 엄격한 스키마 검증을 하는 외부 소비자가 있다면
  배포 전에 호환성을 확인해야 한다.

## 검증

- 업무 예외와 Validation 예외의 JSON 구조 계약 테스트
- `MethodArgumentNotValidException`과 `BindException` 변환 테스트
- `backend:convention`, `backend:check`, `verify:changed`
