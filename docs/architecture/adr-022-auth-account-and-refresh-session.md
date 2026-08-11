# ADR 022: AuthAccount와 회전형 Refresh Session

## 상태

확정

## 맥락

기존 `Member`는 기승 이력, 클래스 자격과 예약의 주체이며 안정적인 `authSubject`로 JWT 주체와
연결된다. 이메일·비밀번호 로그인과 관리자 Bootstrap을 추가하면서 자격 증명, 계정 상태와 역할을
`Member`에 함께 넣으면 관리자에게 가짜 기승 프로필을 만들거나 역할·상태의 원천을 중복하게 된다.
또한 Access Token은 stateless 검증을 유지하되 Refresh Token은 서버에서 폐기할 수 있어야 한다.

## 결정

### 계정과 회원 경계

- `AuthAccount`가 정규화 이메일, 비밀번호 hash, 역할과 로그인 상태의 단일 원천이다.
- MEMBER 계정은 기존 `Member`와 1:1로 연결하고 두 레코드는 회원가입 트랜잭션에서 함께 생성한다.
- ADMIN 계정은 기승 프로필이 필요하지 않으므로 `member_id`가 없다.
- JWT `sub`는 이메일이나 DB PK가 아닌 생성 후 변하지 않는 `auth_subject`를 사용한다.
- MEMBER의 `AuthAccount.authSubject`와 `Member.authSubject`는 같은 값을 사용해 기존 조회 계약을 유지한다.
- 기존 Member에는 가짜 이메일이나 비밀번호를 backfill하지 않는다. 기존 회원 계정 연결은 후속 작업이다.
- `/api/auth/me`는 로컬 `AuthAccount`의 현재 상태를 조회하는 Endpoint다. 계정 연결 전 기존 Member는
  기존 subject 기반 회원 API를 계속 사용할 수 있지만 이 신규 Endpoint의 대상은 아니며, 연결·초대
  절차가 완료된 뒤 사용한다.
- 계정 응답의 `subject`는 JWT `sub`와 동일한 외부 안정 식별자다. ADMIN처럼 Member가 없는 계정도
  이메일을 식별자로 오용하지 않고 이 값을 사용한다.

### 이메일과 비밀번호

- 이메일은 앞뒤 공백 제거 후 `Locale.ROOT` 소문자로 정규화하고 binary collation UNIQUE로 최종 보장한다.
- 비밀번호는 Spring Security의 delegating `PasswordEncoder`를 사용한다. 현재 기본은 bcrypt이며
  알고리즘 식별자를 hash에 포함해 향후 안전한 점진 업그레이드를 허용한다.
- 비밀번호는 최소 8자이며 bcrypt 입력 한계와 맞춰 UTF-8 기준 최대 72 byte로 제한한다.
- 회원가입과 관리자 Bootstrap은 같은 `PasswordEncoder` bean을 사용하고 원문은 저장·로그·응답하지 않는다.

### Access Token

- 기존 HMAC SHA-256 Secret과 `roles` claim 변환을 유지한다.
- 발급 Access Token의 issuer 기본값은 `horse-api`, 수명은 15분이며 설정으로 조정할 수 있다.
- 기존 외부·테스트 토큰 호환을 위해 Decoder에 issuer 강제 검증을 새로 추가하지 않는다.
- 발급 응답은 JSON으로 제공하며 브라우저 `localStorage`나 Cookie 사용을 백엔드 계약으로 고정하지 않는다.

### Refresh Session

- Refresh Token은 256-bit 암호학적 난수를 Base64URL로 전달하고 DB에는 SHA-256 hash만 저장한다.
- 기본 수명은 30일이다. 세션은 계정, 상태, 만료 시각, token family와 이전 세션 관계를 추적한다.
- refresh는 token hash 행을 `PESSIMISTIC_WRITE`로 잠근 트랜잭션에서 기존 세션을 ROTATED로 바꾸고
  후속 세션을 생성한다. 이전 세션은 재사용할 수 없고 동시 요청은 하나만 성공한다.
- M31-16A2부터 재사용 정책은 `REJECT_ONLY`가 아니라 `FAMILY_REVOCATION`이다. 이미 `ROTATED`된
  Token이 다시 제출되면 같은 트랜잭션에서 해당 family의 모든 `ACTIVE` Session을 ID 순서로 잠그고
  `REVOKED` 처리한 뒤 기존 `AUTH_INVALID_REFRESH_TOKEN` 401을 반환한다.
- 알 수 없음·만료·이미 `REVOKED`된 Token은 family 폐기를 실행하지 않는다. 서로 다른 로그인은
  독립된 `family_id`를 사용하므로 다른 기기나 로그인 family에는 영향을 주지 않는다.
- 서버는 탈취 재사용과 정상적인 중복 네트워크 요청을 구분할 수 없다. 같은 ACTIVE Token의 동시
  refresh 중 뒤늦은 요청도 재사용으로 판단해 새 successor를 폐기하므로 사용자는 다시 로그인해야 한다.
  서버에는 grace period를 두지 않으며 M31-16B2의 클라이언트 single-flight refresh로 중복 요청을 막는다.
- logout은 현재 refresh session을 REVOKED로 바꾼다. 이미 ROTATED 또는 REVOKED인 알려진 세션의
  반복 logout은 성공으로 처리하지만, 알 수 없거나 만료된 토큰은 인증 실패다.
- 일반 logout은 제출된 Session 하나만 처리하며 family 전체 또는 다른 로그인 Session을 폐기하지 않는다.
- V35는 parent FK와 `UNIQUE(parent_session_id)`를 제공하지만 parent와 child의 `family_id` 일치 자체를
  DB 제약으로 강제하지 않는다. 같은 family 전파는 `RefreshTokenSession.createSuccessor()`의
  애플리케이션 불변식이며, schema 재설계가 필요해지는 시점까지 통합 테스트로 보호한다.
- 기존 모바일·CLI용 `/api/auth/**` API는 Access Token과 Refresh Token을 JSON body로 전달하는 계약을
  유지한다. 웹 전용 `/api/auth/web/**` API는 Access Token만 JSON으로 전달하고 Refresh Token은
  JavaScript가 읽을 수 없는 HttpOnly Cookie로 전달한다. 두 API는 같은 로그인·회전·family 폐기·logout
  Application Service를 사용하며 인증 상태 전이 로직을 복제하지 않는다.

### 웹 Token 전달과 CSRF

- 웹 회원가입은 기존 `/api/auth/signup`을 사용하고 자동 로그인하지 않는다. 가입 후 웹 login을 별도로
  호출해야 Refresh Cookie가 생성된다. `/api/auth/me`도 기존 Bearer Access Token 계약을 유지한다.
- 웹 Refresh Cookie는 host-only, `HttpOnly`, `SameSite=Lax`, `Path=/api/auth/web`이며 Max-Age는
  Refresh Session TTL과 같다. 운영에서는 `Secure=true`, localhost HTTP 개발에서만 `false`다.
  발급·회전·삭제는 같은 Cookie 속성을 사용한다.
- Cookie가 자동 전송되는 웹 login·refresh·logout은 Spring Security의 SPA CSRF 지원과
  `CookieCsrfTokenRepository`로 보호한다. `XSRF-TOKEN` Cookie와 `X-XSRF-TOKEN` Header를 사용하며
  Refresh Cookie는 인증 자격 증명, CSRF Token은 브라우저 자동 요청을 구분하는 증명값이다.
- 서버 Session은 만들지 않고 `SessionCreationPolicy.STATELESS`를 유지한다. CSRF Token은 Cookie에
  저장하므로 `JSESSIONID`가 필요하지 않다. refresh 성공마다 별도 CSRF 회전 로직을 추가하지 않는다.
- 개발 CORS는 `http://localhost:5173`을 정확히 허용하고 credential 요청에 wildcard Origin을 사용하지
  않는다. 운영 Origin은 외부 설정으로 주입하며 같은 origin reverse proxy 배포를 우선한다.
- 웹 logout은 유효한 현재 Session만 기존 로직으로 폐기한다. 누락·만료·폐기·알 수 없는 Cookie도
  Cookie를 삭제하고 204를 반환해 최종 로그아웃 상태를 멱등 보장하며 다른 family는 건드리지 않는다.
- M31-16B1은 React의 Access Token 메모리 보관, 인증 화면과 보호 Route를 담당한다. M31-16B2는 앱 시작
  복구, single-flight refresh와 동시 401 처리를 담당한다. 웹 요청은 `credentials: include`를 사용해야 한다.
- XSS sanitizer, 전용 backend filter와 CSP 상세 정책은 M31-16B0 범위가 아니다. 후속 React 화면은 서버
  문자열을 일반 text rendering으로 출력하고 `dangerouslySetInnerHTML`을 사용하지 않는다.

### 최초 관리자 Bootstrap

- `bootstrap-admin` 프로필의 ApplicationRunner만 `BOOTSTRAP_ADMIN_EMAIL`과
  `BOOTSTRAP_ADMIN_PASSWORD`를 읽는다.
- 일반 `prod` 실행에서는 Bootstrap을 수행하지 않는다.
- 최초 관리자에는 고정된 bootstrap guard를 기록하고 DB UNIQUE로 다중 인스턴스 경쟁에서도 한 명만 만든다.
- 기존 관리자가 있으면 아무것도 변경하지 않는다. 실제 자격 증명은 외부 Secret으로만 주입한다.
- 최초 성공 후 다음 배포부터 Bootstrap 프로필과 두 환경 변수를 제거한다.

## 결과

예약·회원 도메인은 기존 안정적인 subject를 유지하고 인증 자격 증명은 독립적으로 수명주기를 갖는다.
Access Token은 기존 stateless 검증과 호환되며 Refresh Session만 서버 상태를 가진다. 기존 회원 로그인
연결, rate limit, 비밀번호 재설정, 관리자 추가·회수 및 key rotation은 후속 보안 작업으로 남는다.
