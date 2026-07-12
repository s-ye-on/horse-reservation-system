# 도메인 모델

## Member

```text
id
auth_subject unique
name
phone
general_ride_count
dressage_ride_count
jumping_ride_count
dressage_approved
jumping_approved
large_arena_allowed
created_at
updated_at
```

일반 클래스 등급은 `general_ride_count`로 계산한다. 특수 클래스 횟수는 일반 등급에 영향을 주지 않는다.
JWT `sub`는 변경 가능한 회원 상태를 담지 않고 `auth_subject`와 일치시켜 회원을 조회한다.

## Coupon

```text
id
member_id
type: general | dressage | jumping
total_count
remaining_count
held_count
first_used_at nullable
expires_at nullable
free_change_used
status: active | expired | depleted
created_at
updated_at
```

사용 가능 횟수는 `remaining_count - held_count`다. 첫 수업 완료 전에는 `first_used_at`과 `expires_at`이 비어 있을 수 있다.

## Reservation

```text
id
member_id
class_type
lesson_date
start_time
status
payment_source: coupon | single_payment
coupon_id nullable
payment_due_at nullable
approval_requested_at
admin_confirmed_at nullable
rejected_at nullable
rejected_by nullable
rejection_reason nullable
cancelled_at nullable
cancellation_responsibility: member | stable | exception nullable
coupon_action: deduct | return | none nullable
admin_memo nullable
version
created_at
updated_at
```

`coupon_id`는 임시 점유부터 실제 사용 완료까지 같은 쿠폰을 추적한다. 임시 점유 여부와 단계는 쿠폰 사용 로그로 구분한다.

## TimeSlotCapacity

```text
id
lesson_date
start_time
total_capacity
round_arena_capacity
class_capacity_json
is_closed
created_at
updated_at
```

## CouponUsageLog

```text
id
coupon_id
reservation_id
member_id
action: held | confirmed | used | released | deducted | expired | free_change_used
count_delta
occurred_at
actor_type: system | member | admin
memo nullable
created_at
```

## ReservationChangeLog

```text
id
reservation_id
actor_id
actor_type: member | admin | system
from_status
to_status
from_lesson_date nullable
from_start_time nullable
to_lesson_date nullable
to_start_time nullable
change_type nullable
coupon_action: none | free_change_used | deduct | return nullable
memo nullable
created_at
```

## 핵심 불변식

- 정원을 점유하는 예약 합계는 시간대 제한을 초과할 수 없다.
- 쿠폰의 `held_count`는 `remaining_count`보다 클 수 없다.
- 만료되거나 소진된 쿠폰은 새 임시 점유를 만들 수 없다.
- 수업일이 쿠폰 만료일 이후면 해당 쿠폰을 사용할 수 없다.
- 완료·노쇼·취소의 쿠폰 처리는 예약당 한 번만 확정된다.
- 일반 탑승 횟수는 일반 기승 수업 완료당 한 번만 증가한다.
- 반려와 취소는 각각 `rejected`, `cancelled` 상태로 기록되며 서로 대체하지 않는다.
