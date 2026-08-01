# ADR 003: JWT 기반 API 인증

## 상태

확정

## 맥락

웹과 모바일 앱이 같은 Spring API를 사용하며 회원과 관리자 권한을 구분해야 한다. 서버 세션에 의존하면 모바일 클라이언트와 수평 확장 시 상태 관리가 추가된다.

## 결정

- API 인증은 Authorization 헤더의 Bearer JWT를 사용한다.
- 백엔드는 JWT 서명을 검증하고 `roles` claim을 `MEMBER`, `ADMIN` 권한으로 변환한다.
- API 보안 컨텍스트는 stateless로 운영한다.
- 운영 서명 키는 환경변수로 주입하며 저장소에 운영 키를 기록하지 않는다.
- 운영 실행은 `SPRING_PROFILES_ACTIVE=prod`와 외부 `JWT_SECRET`을 함께 요구한다.
- `prod`에서 Secret이 누락되거나 빈 값·공백·32바이트 미만·개발 기본값·알려진
  placeholder이면 시작에 실패한다.
- 토큰 발급, 갱신, 폐기, 클라이언트 저장 정책은 로그인 구현 task에서 확정한다.

MVP-1은 외부에서 발급된 JWT 또는 로컬 개발 토큰을 검증하는 Resource Server 역할까지만 구현한다. 로그인 화면, 토큰 발급·갱신·폐기와 웹·앱 저장 흐름은 후속 MVP 범위다.

## 결과

웹과 모바일이 같은 인증 계약을 사용하고 API 서버는 세션 상태를 보관하지 않는다. 반면 토큰 탈취와 폐기 대응을 위해 짧은 access token 수명, refresh token 관리 및 안전한 클라이언트 저장 방식이 후속 작업으로 필요하다.

개발 환경의 기본 Secret과 localhost API URL fallback은 운영 인증 결정이 아니다. M31-15부터
`prod` 프로필은 외부 `JWT_SECRET`이 없으면 시작하지 않는다. 기본 프로필의 개발 fallback은
로컬·테스트·OpenAPI·데모 호환을 위해 유지되므로 운영 배포 절차가 `prod` 활성화를 누락하지
않도록 별도 배포 Gate로 검증해야 한다.
