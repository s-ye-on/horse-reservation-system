# ADR-013: 시간 기반 예약 생명주기와 행동 판정 책임

## 상태

Accepted

## 배경

예약 상태만으로 행동 가능 여부를 판단하면 수업 시작 뒤에도 승인, 변경, 취소가
가능하고 수업 시작 전 완료와 노쇼가 가능하다. 승인대기 정리 Job만으로 차단하면 Job
실행 주기 사이에 잘못된 Command가 성공할 수 있다. 프론트엔드가 상태와 기기 시각으로
정책을 재구현하면 Command 정책과 화면이 서로 달라질 수 있다.

## 결정

### 시간 기준

- 모든 시간 판정은 `Asia/Seoul`을 사용한다.
- `lessonStartAt`은 Reservation의 `lessonDate`와 `startTime`을 결합한 값이다.
- 승인, 변경, 취소는 `now < lessonStartAt`일 때만 허용한다.
- 완료와 노쇼는 `now >= lessonStartAt`일 때만 허용한다.
- 모든 Command는 트랜잭션 실행 시점에 정책을 다시 검증한다.

### 책임 경계

Reservation Aggregate는 자신의 상태, 결제 방식, 수업일과 시작 시각만으로 판단할 수
있는 불변식을 책임진다. `validateCanApprove(now)`, `validateCanChange(now)`,
`validateCanCancel(now)`, `validateCanComplete(now)`, `validateCanNoShow(now)`처럼
행동 의도가 드러나는 메서드가 상태와 시간 경계를 함께 검증한다. 상태 전이는 검증을
우회하지 않는다.

Application Service는 주입된 `Clock`에서 현재 시각을 얻고, 트랜잭션과 잠금, 회원
소유권, 대상 TimeSlot 정원, 쿠폰 점유·차감·반환처럼 여러 Aggregate를 조정한다.
대상 시간대 정원이나 쿠폰 유효기간처럼 Reservation 내부 정보만으로 판단할 수 없는
규칙은 기존 Domain Policy 또는 Application Service에 둔다.

Controller는 Request DTO를 검증하고 Application Service를 호출한 뒤 Response DTO를
반환한다. 시간 또는 행동 가능 여부를 직접 판단하지 않는다.

### Scheduler와 상태 정리

- 수업 시작까지 승인되지 않은 쿠폰 예약은 `approval_expired`로 전이한다.
- 승인대기 만료 시 Reservation 정원 점유와 쿠폰 임시 점유를 같은 트랜잭션에서
  해제한다.
- Scheduler는 얇은 진입점이고 Application Service를 호출한다.
- Job은 지연 상태를 정리할 뿐이며 Command 검증의 선행 조건이 아니다.
- 입금대기는 2시간 마감과 수업 시작 시각 중 빠른 시점을 만료 기준으로 사용한다.
- 과거 `confirmed`는 사람의 출석 판단이 필요하므로 자동 종료하지 않는다.

### 읽기 모델

조회 API는 `displayGroup`(`UPCOMING`, `PAST`)과 행동별 `allowed`, `blockedReason`을
제공한다. 변경, 취소, 완료, 노쇼와 관리자 승인 판정을 서로 독립적으로 제공한다.
Read Model은 화면 편의를 위한 스냅샷이며 Command 권한을 부여하지 않는다. 실제
Command는 트랜잭션 안에서 동일한 도메인 규칙을 다시 검증한다.

## 결과

- Job 지연 중에도 잘못된 Command가 성공하지 않는다.
- Aggregate 내부 불변식과 여러 Aggregate 조정 책임이 분리된다.
- 프론트엔드는 도메인 정책을 재구현하지 않고 서버의 Read Model을 표현한다.
- 조회 이후 Command 실행 전 시간이 경과해도 서버가 최종적으로 정책을 강제한다.
- 상태와 OpenAPI 계약이 변경되므로 Flyway, API Client와 경계값 회귀 테스트가 필요하다.
