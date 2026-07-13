# 시스템 아키텍처 개요

## 결정 범위

이 프로젝트는 하나의 저장소에 백엔드, 웹, 모바일 앱을 두는 모노레포다. 백엔드는 Java와 Spring Boot 기반 단일 애플리케이션으로 시작하고, 웹을 먼저 운영한 뒤 같은 API를 사용하는 모바일 앱을 추가한다.

## 기술 스택

- 백엔드: Java, Spring Boot, Spring Data JPA, Hibernate, Spring Security, JWT
- 데이터베이스: MySQL, Flyway
- 백엔드 빌드: Gradle Wrapper
- 웹: React, TypeScript, Vite, React Router
- 모바일: React Native, TypeScript, Expo, Expo Router
- API 계약: Spring OpenAPI 문서에서 TypeScript 클라이언트 생성
- 프론트 패키지 관리: pnpm workspace
- 루트 작업 실행기: mise

## 경계

```text
horse/
  backend/
    build.gradle
    gradlew
    src/main/java/
    src/test/java/
  frontend/
    apps/
      web/
      mobile/
    packages/
      api-client/
      shared-types/
      shared-utils/
    pnpm-workspace.yaml
  docs/
  mise.toml
```

백엔드가 예약 가능 여부, 상태 전이, 정원, 쿠폰과 변경·취소 정책의 최종 권한을 가진다. 웹과 모바일은 백엔드 판정 결과를 표시하고 사용자 입력을 수집한다. 웹과 모바일은 API 클라이언트와 타입·순수 유틸리티만 공유하며 UI 컴포넌트 공유는 강제하지 않는다.

## 핵심 모듈

- `auth`: JWT 검증과 회원·관리자 역할 기반 권한
- `members`: 회원 정보, 일반 기승 횟수, 특수 클래스 승인
- `timeslots`: 수업 시간대, 휴무, 전체·원형·클래스 정원
- `reservations`: 예약 상태 전이, 정원 점유, 변경·취소
- `coupons`: 쿠폰 등록, 임시 점유, 확정 점유, 차감, 반환, 만료
- `admin`: 승인, 입금 확인, 수업 완료, 예외 처리와 감사 이력

## 일관성 경계

다음 작업은 하나의 DB 트랜잭션 안에서 처리한다.

- 예약 신청과 정원 점유
- 쿠폰 선택과 임시 점유
- 관리자 승인과 쿠폰 점유 전환
- 예약 변경 시 신규 정원 확보와 기존 정원 반환
- 수업 완료와 쿠폰 차감 및 탑승 이력 증가
- 취소·노쇼와 쿠폰 차감 또는 반환
- 입금대기 만료와 정원 반환

중복 요청으로 쿠폰이나 탑승 횟수가 두 번 반영되지 않도록 상태 조건과 멱등성 키를 사용한다.

M1 정원 점유는 Reservation 상태를 SSOT로 사용한다. `pending_admin_approval`, `pending_payment`, `confirmed`만 활성 점유이며 별도 점유 원장이나 카운터를 두지 않는다. 점유 상태로 진입하는 모든 트랜잭션은 `TimeSlotCapacity`를 먼저 비관적 잠금한 뒤 활성 Reservation을 잠금 조회하고 상태를 변경한다. 자세한 결정은 [ADR 005](adr-005-reservation-occupancy-ssot.md)를 따른다.

## 백그라운드 작업

- 2시간 지난 입금대기 만료
- 유효기간 지난 쿠폰 만료 및 잔여 횟수 소멸
- 승인대기 경고 등급 계산 또는 조회

작업은 여러 번 실행해도 같은 결과가 나와야 한다.

## 품질 속성

- 정확성: 정원·쿠폰·탑승 횟수 중복 반영 방지
- 감사 가능성: 관리자 결정과 쿠폰 변화 이력 보존
- 운영성: 승인대기와 입금대기 우선순위 표시
- 변경 용이성: 정책 판정을 UI와 분리된 도메인 서비스로 관리
- 보안: 회원은 자신의 데이터만, 관리자는 허용된 운영 기능만 접근

API 인증은 서명된 JWT Bearer 토큰을 사용한다. 토큰의 `roles` claim을 `MEMBER`, `ADMIN` 권한으로 변환하며 백엔드는 서버 세션을 생성하지 않는다. 토큰 발급·갱신·폐기 정책은 로그인 작업에서 별도로 확정한다.

## 비목표

초기 버전은 마이크로서비스, 이벤트 소싱, 강사·말·마장 자동 배정, 외부 결제·알림 연동을 사용하지 않는다. Next.js 서버 기능과 모바일 관리자 화면도 초기 범위에서 제외한다.
