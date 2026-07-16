# API 초안

Spring Boot가 OpenAPI 계약을 제공하고 웹·모바일 TypeScript 클라이언트를 생성한다. 생성 클라이언트는 직접 수정하지 않는다.

## 오류 응답

Spring MVC의 업무 예외와 요청 검증·바인딩 예외는 다음 단일 형식을 사용한다.

```text
code
message
status
timestamp
path
fieldErrors[] { field, message }
```

기존 `code`와 `message` 계약은 유지한다. 업무 예외의 `fieldErrors`는 빈 배열이며,
`@Valid`와 바인딩 오류는 `COMMON_INVALID_REQUEST`와 필드별 공개 검증 메시지를 반환한다.
거부된 입력값은 오류 응답에 포함하지 않는다. 상세 결정은 ADR-008을 따른다.

## 회원

```text
GET  /api/me
GET  /api/me/eligible-classes
GET  /api/timeslots?date=&classType=
POST /api/reservations
GET  /api/me/reservations
GET  /api/me/reservations/{reservationId}
GET  /api/me/reservations/{reservationId}/change/preview
POST /api/me/reservations/{reservationId}/change
GET  /api/me/reservations/{reservationId}/cancellation-preview
POST /api/me/reservations/{reservationId}/cancel
GET  /api/me/coupons
GET  /api/me/coupon-usage-logs
```

예약 신청 응답은 `status`, `payment_source`, 선택된 쿠폰과 임시 점유 정보, `payment_due_at`을 포함한다.

변경 preview는 선택한 `target_time_slot_id`의 현재 정원과 변경 정책을 검증해 예상 쿠폰 처리를 반환하지만 데이터를 변경하지 않는다. 실제 변경 실행은 실행 시점에 같은 정책과 정원을 다시 검증한다.

취소 preview와 실행은 같은 서버 정책 판정기를 사용한다. 회원 취소 실행은 활성 예약을 즉시 `cancelled`로 전이하며 회원 책임과 사유를 감사 이력에 남긴다.

## 관리자 예약

```text
GET  /api/admin/reservations
GET  /api/admin/reservations/{reservationId}
POST /api/admin/reservations/{reservationId}/confirm
POST /api/admin/reservations/{reservationId}/reject
POST /api/admin/reservations/{reservationId}/change
GET  /api/admin/reservations/{reservationId}/cancellation-preview
POST /api/admin/reservations/{reservationId}/cancel
POST /api/admin/reservations/{reservationId}/complete
POST /api/admin/reservations/{reservationId}/no-show
POST /api/admin/reservations/complete-bulk
```

반려 API는 관리자 승인 전 예약만 `rejected`로 전이한다. 취소 API는 `cancelled`로 전이하므로 서로 대체하지 않는다.

관리자 취소 preview는 선택한 `cancellation_responsibility`에 따른 권장 `coupon_action`을 반환한다. 실행 요청은 `cancellation_responsibility`, 최종 `coupon_action`, 필수 `memo`를 받으며 preview의 권고와 달라도 예약 유형별 허용 범위 안이면 관리자 선택을 적용한다. 노쇼 요청도 `coupon_action`과 관리자 `memo`를 받는다.

## 입금대기

```text
GET  /api/admin/pending-payments
POST /api/admin/reservations/{reservationId}/expire-payment
POST /api/admin/reservations/{reservationId}/restore-payment
```

복구 API는 현재 정원을 원자적으로 확보한 경우에만 성공한다.

## 시간대와 정원

```text
GET    /api/admin/timeslots
POST   /api/admin/timeslots
PATCH  /api/admin/timeslots/{timeslotId}
DELETE /api/admin/timeslots/{timeslotId}
PUT    /api/admin/timeslots/{timeslotId}/capacity
```

`PATCH`는 `is_closed`만 변경해 신규 예약을 마감하거나 재개한다. 기존 예약은 변경하지 않는다.
`DELETE`는 예약 이력이 없는 오생성 시간대만 물리 삭제하며, 예약 이력이 있으면 `409 Conflict`를 반환한다.

## 회원과 쿠폰

```text
GET   /api/admin/members
GET   /api/admin/members/{memberId}
PATCH /api/admin/members/{memberId}
PATCH /api/admin/members/{memberId}/riding-permissions
PATCH /api/admin/members/{memberId}/ride-counts

GET   /api/admin/members/{memberId}/coupons
POST  /api/admin/members/{memberId}/coupons
PATCH /api/admin/coupons/{couponId}
POST  /api/admin/coupons/{couponId}/expire
GET   /api/admin/coupon-usage-logs
```

## 관리자 작업

```text
POST /api/admin/jobs/expire-pending-payments
```

입금대기 만료는 Spring Scheduler가 Application Service를 직접 호출해 자동 실행한다. 관리자 수동 실행 API는 같은 Service를 호출하며 `ROLE_ADMIN`만 접근할 수 있다. 외부 Scheduler나 머신 인증이 필요한 시점에 시스템 작업 API와 별도 권한을 재검토한다.
