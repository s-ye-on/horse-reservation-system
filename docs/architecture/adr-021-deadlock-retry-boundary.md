# ADR 021: Deadlock Retry Transaction Boundary

## 상태

`ACCEPTED`

## 배경

예약, Coupon, 일정 운영 Command는 여러 Aggregate를 비관적 잠금으로 조정한다. M31-06은
공통 잠금 순서를 확정했지만 MySQL은 올바른 순서에서도 범위 잠금이나 실행 계획 변화로
deadlock 1213을 선택할 수 있다. Repository 일부만 재실행하거나 기존 트랜잭션 안에서
`REQUIRES_NEW`를 반복하면 앞선 상태와 Persistence Context를 재사용하거나 connection
pool을 추가 점유해 원자성을 깨뜨릴 수 있다.

## 결정

쓰기 `@Transactional` Application Service의 transaction interceptor 바깥에
`DeadlockRetryAspect`를 둔다.

- MySQL error code 1213과 SQLState 40001이 함께 확인된 경우만 재시도한다.
- 최초 실행을 포함해 최대 3회 시도한다.
- 각 `ProceedingJoinPoint.proceed()`가 transaction interceptor를 다시 통과하므로 매
  시도는 새 트랜잭션과 Persistence Context를 사용한다.
- 이미 활성 트랜잭션 안에서 호출된 중첩 Application Service는 재시도하지 않고 기존
  트랜잭션에 참여한다. 따라서 외부 connection을 유지한 `REQUIRES_NEW` 반복이 없다.
- read-only 트랜잭션, 1205 lock wait timeout, 업무·검증·인증 예외는 재시도하지 않는다.
- 전체 Command가 rollback된 후 처음부터 재실행되며 Repository 일부만 재실행하지 않는다.
- 실패 시도 사이에는 짧은 exponential backoff와 jitter를 적용한다.
- 로그에는 클래스·메서드, 시도 횟수만 남기고 인자나 인증 주체를 남기지 않는다.

Advisor 순서는 Spring의 최상위 내부 interceptor 뒤이면서 transaction interceptor
앞인 `DeadlockRetryAspect(Ordered.HIGHEST_PRECEDENCE + 1) -> transaction
interceptor`로 고정한다.

## 잠금 순서 보정

- 수업 완료의 비관적 행 잠금은 `Reservation -> Coupon -> Member` 순서로 실행한다.
  CouponUsageLog는 Coupon 전이와 같은 호출에서 원자적으로 추가되며 Member 행 잠금을
  획득하지 않는다.
- TimeSlot 정원 변경과 삭제는 non-locking ID/date snapshot 뒤
  `ScheduleConfigGuard SHARE -> ScheduleDate -> TimeSlot -> Reservation` 순서로
  잠근다. ID/date snapshot은 잠금 근거가 아니며 같은 transaction의 Date·TimeSlot
  재조회가 정합성을 보장한다. 삭제의 예약 이력 검사는 `FOR UPDATE` 현재 읽기로 실행해
  snapshot 생성 뒤 Date 잠금을 기다렸더라도 최신 이력을 확인한다.

## 결과

1213은 구조적 순서 오류를 숨기는 성공 조건이 아니다. M31-06 matrix의 순서 차이를 먼저
제거하고, 실제 MySQL deadlock과 교차 Command 테스트로 rollback과 exactly-once를
검증한다. 재시도 소진 시 마지막 원본 예외를 그대로 전달하며 통일된 외부 오류 변환은
M31-10의 소유 범위다.
