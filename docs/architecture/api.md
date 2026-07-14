# API 초안

Spring Boot가 OpenAPI 계약을 제공하고 웹·모바일 TypeScript 클라이언트를 생성한다. 생성 클라이언트는 직접 수정하지 않는다.

## 회원

```text
GET  /api/me
GET  /api/me/eligible-classes
GET  /api/timeslots?date=&classType=
POST /api/reservations
GET  /api/me/reservations
GET  /api/me/reservations/{reservationId}
POST /api/me/reservations/{reservationId}/change
POST /api/me/reservations/{reservationId}/cancel
GET  /api/me/coupons
GET  /api/me/coupon-usage-logs
```

예약 신청 응답은 `status`, `payment_source`, 선택된 쿠폰과 임시 점유 정보, `payment_due_at`을 포함한다.

## 관리자 예약

```text
GET  /api/admin/reservations
GET  /api/admin/reservations/{reservationId}
POST /api/admin/reservations/{reservationId}/confirm
POST /api/admin/reservations/{reservationId}/reject
POST /api/admin/reservations/{reservationId}/change
POST /api/admin/reservations/{reservationId}/cancel
POST /api/admin/reservations/{reservationId}/complete
POST /api/admin/reservations/{reservationId}/no-show
POST /api/admin/reservations/complete-bulk
```

반려 API는 관리자 승인 전 예약만 `rejected`로 전이한다. 취소 API는 `cancelled`로 전이하므로 서로 대체하지 않는다.

취소·노쇼 요청은 `coupon_action`과 관리자 `memo`를 받는다. 서버는 허용 가능한 상태와 쿠폰 처리 조합을 검증한다.

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
