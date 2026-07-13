# Security Rule

- JWT를 인증 수단으로 사용한다.
- Controller는 토큰 검증을 직접 수행하지 않는다.
- Application Service는 `SecurityContext`와 `SecurityContextHolder`를 직접 참조하지 않는다.
- 인증 주체 해석과 권한 변환은 auth/security 경계에서 처리한다.
- Service에는 인증 프레임워크 객체가 아니라 검증된 회원 식별자나 인증 컨텍스트 값을 전달한다.
- JWT에는 기승 횟수나 특수 승인처럼 변경 가능한 회원 상태를 저장하지 않는다.
