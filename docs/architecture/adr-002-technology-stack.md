# ADR 002: Java 백엔드와 React 계열 클라이언트

## 상태

확정

## 맥락

개발자는 Java, Spring, JPA 경험이 있고 서비스는 웹과 Android·iOS 앱을 모두 제공해야 한다. 예약 정책의 최종 판정은 하나의 백엔드에서 일관되게 수행해야 한다.

## 결정

- 백엔드는 Java, Spring Boot, Spring Data JPA, Hibernate, Spring Security를 사용한다.
- 데이터베이스는 MySQL, 마이그레이션은 Flyway를 사용한다.
- 백엔드는 Gradle Wrapper로 빌드한다.
- 웹은 React, TypeScript, Vite, React Router를 사용한다.
- 모바일은 React Native, TypeScript, Expo, Expo Router를 사용한다.
- 웹을 먼저 운영하고 API 안정화 후 회원 모바일 앱을 제공한다.
- Spring OpenAPI 계약으로 웹·모바일 TypeScript API 클라이언트를 생성한다.
- 웹과 모바일은 API 클라이언트, 타입, 순수 유틸리티를 공유하되 UI 공유는 강제하지 않는다.
- 루트 개발 작업은 mise로 통일한다.

## 검토한 대안

- Next.js 웹: 공개 SEO나 서버 렌더링 이점보다 Spring 백엔드와 서버 책임이 겹치는 비용이 크다.
- Expo 단일 웹·앱 UI: 코드 공유율은 높지만 데스크톱 관리자 화면과 모바일 회원 화면의 사용성이 달라 유지보수 비용이 커질 수 있다.

## 결과

백엔드는 기존 경험을 활용하고 프론트는 React 지식을 웹과 앱에 공통 적용할 수 있다. 대신 Java와 Node 도구 체인을 함께 관리해야 하므로 mise와 생성형 OpenAPI 클라이언트가 필수 하네스가 된다.

## 검증

- Gradle Wrapper로 백엔드 테스트와 빌드가 가능해야 한다.
- pnpm workspace에서 웹·모바일 타입 검사와 빌드가 가능해야 한다.
- OpenAPI 변경 후 클라이언트 생성과 변경 없음 검사가 가능해야 한다.
- 루트 mise 명령으로 위 검증을 재현할 수 있어야 한다.

