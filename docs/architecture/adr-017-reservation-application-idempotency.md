# ADR 017: 예약 생성 Idempotency-Key 원장

## 상태

`ACCEPTED`

## 배경

회원 예약 생성은 Reservation, TimeSlot 정원 점유, Coupon 임시 점유와
CouponUsageLog를 한 트랜잭션에서 변경한다. 모바일 재시도, 버튼 중복 제출 또는 성공
commit 뒤 응답 전달 실패가 발생하면 같은 논리 요청이 다시 들어올 수 있다. 동일 회원의
수업 구간 중복 불변식은 두 번째 Reservation을 막지만 최초 성공 응답을 재사용하지 못하고
중복 요청과 별개의 도메인 충돌만 반환한다.

## 결정

회원 예약 생성 POST는 Horse API 계약의 필수 `Idempotency-Key` 헤더를 사용한다.

- 범위는 인증 주체, `member_reservation_create` operation과 key의 조합이다.
- key는 앞뒤 공백을 제거한 1~255자 opaque 값이며 DB binary collation으로 비교한다.
- 요청 fingerprint는 JSON 문자열이 아니라 역직렬화된 `timeSlotId`, `classType`을
  version이 있는 길이 구분 binary 형식으로 정규화한 뒤 SHA-256으로 계산한다.
- `classType` 값 자체는 기존 대소문자 구분 업무 계약을 유지하며 임의로 변경하지 않는다.
- 같은 범위와 fingerprint의 완료 행은 저장된 최초 HTTP status와 JSON response body를
  그대로 반환한다.
- 같은 범위에 다른 fingerprint를 사용하면
  `RESERVATION_IDEMPOTENCY_KEY_CONFLICT` 409를 반환한다.
- key 누락과 기술 한도 위반은 각각
  `RESERVATION_IDEMPOTENCY_KEY_REQUIRED`,
  `RESERVATION_INVALID_IDEMPOTENCY_KEY` 400으로 구분한다.
- 실패 결과는 저장하지 않는다.

## 원자성과 잠금

`reservation_application_idempotencies`가 멱등성 원장이다.

1. `INSERT IGNORE`로 범위 행을 `processing` 상태로 선점한다.
2. 경고는 MySQL duplicate key 1062만 허용하고 다른 경고는 실패시킨다.
3. 유일 scope 행을 `FOR UPDATE`로 읽어 fingerprint와 상태를 확인한다.
4. 새 선점이면 기존 예약 생성 흐름을 같은 트랜잭션에서 실행한다.
5. 성공 응답을 Spring이 사용하는 Jackson 3 설정으로 직렬화한다.
6. Reservation ID, HTTP 201, response body와 완료 시각을 원장에 기록하고 flush한다.
7. transaction interceptor가 원장, Reservation, 점유와 감사를 하나의 commit으로
   확정한 뒤 Controller가 저장된 body를 반환한다.

`processing`은 독립 commit하지 않는다. 프로세스 장애, 직렬화 실패 또는 MySQL
deadlock으로 transaction이 rollback되면 원장 선점과 모든 업무 효과가 함께 사라져 같은
key가 다시 선점될 수 있다. 동시 같은 key의 두 번째 `INSERT IGNORE`는 unique index에서
첫 transaction 종료를 기다린다. 첫 요청이 commit되면 완료 행을 replay하고 rollback되면
두 번째 요청이 새 선점자가 된다.

M31-07 `DeadlockRetryAspect`는 바깥쪽
`IdempotentReservationApplicationService.apply` 전체 트랜잭션을 새 transaction과
Persistence Context로 재실행한다. 원장 잠금이 canonical order의 첫 단계이므로 실패한
시도의 Reservation, Coupon 수치와 감사 로그는 남지 않는다.

## 응답 경계

Application 계층은 `ReservationApplicationResponseEncoder` Port만 의존한다.
presentation adapter가 기존 `ReservationApplicationResponse`를 JSON 문자열로
직렬화한다. Controller는 첫 요청과 replay 모두 원장에 저장된 status와 body를
`application/json`으로 반환한다. OpenAPI에는 성공 body를
`ReservationApplicationResponse` schema로 명시해 생성 TypeScript Client의 구조화된
응답 계약을 유지한다.

## 스키마

V33은 기존 migration을 변경하지 않고 원장을 추가한다.

- `UNIQUE(auth_subject, operation, idempotency_key)`
- 처리 중/완료 결과 일관성 CHECK
- 완료 Reservation FK
- 향후 보관 데이터 정리를 위한 `(created_at, id)` 인덱스

기존 예약에는 멱등성 키를 역생성하지 않는다.

## M31-09 관리자 수동 예약 확장

관리자 수동 예약은 같은 `reservation_application_idempotencies` 원장과 원자성 경계를
재사용하되 operation을 `admin_reservation_create`로 분리한다.

- 범위는 관리자 인증 주체, 관리자 operation과 key의 조합이다.
- fingerprint는 `memberId`, `timeSlotId`, `classType`, 정규화된 필수 사유를 포함한다.
- 회원 operation과 관리자 operation은 같은 key를 사용해도 서로 다른 논리 요청이다.
- 관리자 Coupon 예약은 같은 transaction에서 Reservation 생성, Coupon `held`와
  `confirmed` 원장 기록, Reservation `confirmed`, 관리자 생성 감사와 멱등성 완료를
  확정한다. Coupon 횟수는 차감하지 않고 `heldCount`만 1 증가한다.
- 무Coupon 예약은 같은 transaction에서 `pending_payment`, 실제 `paymentDueAt`, 관리자
  생성 감사와 멱등성 완료를 확정한다.
- 관리자 생성 감사는 대상 Reservation FK를 통해 회원, 시간대, Coupon 또는 입금 마감
  정보를 조회하고 필수 사유와 관리자 인증 주체를 append-only로 보존한다.

## 제외와 후속

제품 정책의 기본 보관 기간은 24시간이지만 M31-08은 만료 판정과 정리 Job을 구현하지
않는다. 정리 Job이 추가되기 전에는 완료 행을 보존하고 같은 key를 계속 replay한다.
24시간 이후 key 재사용 의미, 삭제 batch와 운영 관측성은 별도 Task에서 원자적 삭제와
재사용 경쟁을 함께 결정한다.

예약 변경·취소 API와 가족 Coupon 후보 확장은 현재 범위에 포함하지 않는다.
