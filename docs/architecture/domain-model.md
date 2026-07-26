# 도메인 모델

이 문서는 현재 구현 모델과 승인된 Checkpoint 1 보정 목표를 함께 설명한다. 아래 일정 관련
추가 필드는 `docs/tasks/mvp-3.1-checkpoint-1-remediation.md` 완료 전까지 구현 대기 상태다.

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
created_at
updated_at
```

일반 클래스 등급은 `general_ride_count`로 계산한다. 특수 클래스 횟수는 일반 등급에 영향을 주지 않는다.
JWT `sub`는 변경 가능한 회원 상태를 담지 않고 `auth_subject`와 일치시켜 회원을 조회한다.
대마장 이용 가능 여부는 저장하지 않는다. 일반 기승 완료 21회 이상이거나 마장마술 또는 장애물 승인을 받은 회원이면 `Member.canUseLargeArena()`가 계산한다.

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
end_time
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

정원 점유는 별도 모델로 저장하지 않고 `pending_admin_approval`, `pending_payment`, `confirmed` 상태의 Reservation을 집계해 계산한다.
수업 구간은 날짜와 `[start_time, end_time)` 스냅샷으로 보존한다.

## TimeSlotCapacity

```text
id
lesson_date
start_time
end_time
source: template | manual
template_id nullable
total_capacity
round_arena_capacity
class_capacity_json
is_closed
admin_closed
recurring_holiday_closed
template_inactive_closed
created_at
updated_at
```

`TimeSlotCapacity`는 45분 concrete occurrence, 정원과 개별 휴강 상태를 저장한다. 현재 점유
수는 Reservation에서 파생한다. `is_closed`는 세 마감 원인의 OR과 일치해야 하며 자동
occurrence는 물리 삭제하지 않는다.

개별 휴강의 운영 상태와 예약 정리 workflow는 분리한다. `adminClosed`는 신규 유입 차단
상태이고 `TimeSlotClosure`는 `IN_PROGRESS`, `COMPLETED`, `WITHDRAWN` 이력을 보존한다.
`TimeSlotClosureImpact`는 시작 당시 활성 Reservation의 고정 membership이며 현재
Reservation 상태와 원래 TimeSlot 유지 여부로 해결 상태를 파생한다. 연락 상태나 메모는
이 모델에 포함하지 않는다.

R01에서 `end_time`, `source`를 먼저 이관했고 R02에서 `end_time` NOT NULL,
`template_id`, 세 마감 원인과 generated `is_closed` 파생 제약을 추가했다. JPA는
generated 값을 읽기 전용으로 매핑하고 도메인은 세 원인의 OR로 현재 상태를 계산한다.

## RegularScheduleTemplate

```text
id
day_of_week
start_time
end_time
total_capacity
round_arena_capacity
class_capacity_json
active
version
created_by
updated_by
created_at
updated_at
```

요일과 시작 시각별 행을 사용한다. 월요일 정기 휴일과 독립적으로 월요일 Template도
존재하므로 날짜 OPEN 시 정규 시간표 전체를 생성할 수 있다.

## RecurringHolidayRule

```text
id
day_of_week
effective_from
effective_to nullable
reason
active
version
created_by
updated_by
created_at
updated_at
```

정기 휴일은 Template을 삭제하지 않고 자동 occurrence 생성을 억제한다.

## ScheduleDate

```text
id
schedule_date unique
status: normal | open | closing | closed
resume_status: normal | open nullable
reason nullable
changed_by nullable
applied_config_version
version
created_at
updated_at
```

오늘부터 3개월 후까지 NORMAL을 포함한 실제 행을 유지한다. 예약, 변경, 수동 예약과
휴무 전환 Command는 이 행을 공통 날짜 잠금으로 사용한다.

## ScheduleConfigGuard

```text
id: 1
status: active | syncing
active_version
pending_version nullable
sync_started_at nullable
sync_started_by nullable
last_completed_at nullable
last_failed_at nullable
last_failure_code nullable
last_failure_summary nullable
version
```

설정 변경과 신규 일정 유입이 공유하는 singleton 직렬화 행이다. 일반 Command는 공유
잠금으로 ACTIVE를 확인하고 설정 변경은 배타 잠금으로 SYNCING에 진입한다. 모든
ScheduleDate가 pending version을 적용한 뒤에만 active version을 전환한다. 진행률은
ScheduleDate의 적용 version에서 파생하고 미완료 version은 재시작 후에도 이어서 처리한다.

## ReservationMemberDayGuard

```text
member_id
lesson_date
created_at
PRIMARY KEY(member_id, lesson_date)
```

점유나 예약 상태를 복제하지 않고 같은 회원·날짜의 구간 중복 검사만 직렬화한다.

## ScheduleAuditLog

```text
id
target_type: template | recurring_holiday | schedule_date | time_slot
target_key
action
from_state nullable
to_state nullable
actor_auth_subject
reason
metadata_json nullable
created_at
```

일정 설정과 휴무 처리 행위를 추가만 하는 감사 이력이다. 현재 상태는 각 Aggregate가
보유하며 감사 로그에서 재구성하지 않는다.

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
actor_auth_subject
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

`ReservationChangeLog`는 현재 예약 상태를 표현하지 않고 발생한 행위를 추가만 하는 감사 이력이다. 애플리케이션은 기존 행의 수정·삭제 기능을 제공하지 않는다. JWT `sub`는 DB 식별자와 혼동하지 않도록 `actorAuthSubject` / `actor_auth_subject`로 저장한다.

만료 예약 복구는 `change_type = payment_restored`, `actor_type = admin`, `from_status = payment_expired`, `to_status = confirmed`, `coupon_action = none`으로 기록하며 관리자 메모는 필수이고 최대 500자다.

## 핵심 불변식

- 정원을 점유하는 예약 합계는 시간대 제한을 초과할 수 없다.
- 쿠폰의 `held_count`는 `remaining_count`보다 클 수 없다.
- 만료되거나 소진된 쿠폰은 새 임시 점유를 만들 수 없다.
- 수업일이 쿠폰 만료일 이후면 해당 쿠폰을 사용할 수 없다.
- 완료·노쇼·취소의 쿠폰 처리는 예약당 한 번만 확정된다.
- 일반 탑승 횟수는 일반 기승 수업 완료당 한 번만 증가한다.
- 반려와 취소는 각각 `rejected`, `cancelled` 상태로 기록되며 서로 대체하지 않는다.
- 정규 및 수동 TimeSlot과 Reservation 일정 스냅샷은 45분이며 자정을 넘지 않는다.
- 행이 없는 ScheduleDate를 정상 운영일로 간주하지 않는다.
- 날짜 CLOSING/CLOSED와 개별 TimeSlot 마감은 신규 예약과 해당 대상으로의 변경을 차단한다.
- 동일 회원의 활성 Reservation 수업 구간은 같은 날짜에 서로 겹칠 수 없다.
