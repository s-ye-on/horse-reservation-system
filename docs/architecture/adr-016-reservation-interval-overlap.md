# ADR-016: 회원·날짜 직렬화와 활성 예약 구간 중복 방지

## 상태

확정, Checkpoint 1 보정 구현 대기

## 맥락

V15 UNIQUE는 동일 회원의 같은 `lessonDate/startTime` 활성 예약만 차단한다. 수동
TimeSlot은 정규 시간표와 시작 시각이 달라도 45분 구간이 겹칠 수 있으므로 exact-start
제약만으로는 실제 기승자 중복을 막을 수 없다. MySQL은 일반적인 시간 구간 exclusion
constraint를 UNIQUE로 표현할 수 없다.

## 결정

Reservation에 `end_time` 스냅샷을 추가하고 모든 수업 구간을 `[start_time, end_time)`으로
판정한다. 활성 상태는 기존과 같이 `pending_admin_approval`, `pending_payment`,
`confirmed`다.

겹침 조건은 다음과 같다.

```text
existing.start_time < candidate.end_time
AND candidate.start_time < existing.end_time
```

### 직렬화 행

점유 원장을 추가하지 않고 일정 충돌 전용 `reservation_member_day_guards`를 둔다.

```text
member_id
lesson_date
created_at
PRIMARY KEY(member_id, lesson_date)
```

필요한 행은 다음 순서로 원자 확보한다.

```sql
INSERT INTO reservation_member_day_guards(member_id, lesson_date, created_at)
VALUES (:memberId, :lessonDate, CURRENT_TIMESTAMP(6))
ON DUPLICATE KEY UPDATE
    created_at = reservation_member_day_guards.created_at;

SELECT member_id, lesson_date
FROM reservation_member_day_guards
WHERE member_id = :memberId
  AND lesson_date = :lessonDate
FOR UPDATE;
```

동일 UNIQUE key의 upsert가 행 생성을 직렬화하고 이어지는 `FOR UPDATE`가 해당 회원·날짜의
일정 변경을 직렬화한다. 이 행은 점유 상태나 잔액을 보관하지 않으며 현재 범위에서는
삭제하지 않아 cleanup과 예약 생성 사이의 경쟁을 만들지 않는다.

upsert와 `SELECT ... FOR UPDATE`는 반드시 같은 트랜잭션에서 실행한다. upsert가 얻은
배타 잠금은 커밋까지 유지되지만, 후속 `FOR UPDATE`는 신규·기존 행 경로를 같은
Repository 계약으로 통일하고 실제 guard 행을 읽어 존재와 키를 검증하며 이후 로직이
명시적인 잠금 객체를 사용하게 한다. 별도 트랜잭션으로 분리하면 upsert 커밋과 재잠금
사이에 다른 Command가 진입할 수 있으므로 허용하지 않는다.

신규 예약, 관리자 수동 예약, 예약 변경과 입금 만료 복구처럼 활성 구간을 생성·이동·복구하는
Command는 반드시 이 guard를 사용한다. 수업 시작 전 활성 예약을 반려·만료·취소하는
Command도 신규 일정 Command와의 전후 관계를 직렬화하기 위해 guard를 사용한다.

승인과 입금 확인은 활성 상태에서 다른 활성 상태로 이동하며 일정 구간과 점유 여부가
바뀌지 않으므로 member-day guard와 overlap 재검사를 하지 않는다. 수업 완료와 노쇼는
수업 시작 이후에만 허용되어 신규 예약·변경과 경쟁하지 않으므로 대상 Reservation row를
최소 잠금으로 사용하고, 쿠폰·기승 횟수 효과가 있으면 canonical 후순위의 Coupon과
Member를 잠근다. 여러 날짜 또는 회원 guard를 처리하면 `member_id`, `lesson_date`
오름차순으로 잠근다.

### overlap 잠금 조회

```sql
SELECT id, start_time, end_time
FROM reservations
WHERE member_id = :memberId
  AND lesson_date = :lessonDate
  AND active_slot_guard = 1
  AND start_time < :candidateEndTime
  AND end_time > :candidateStartTime
ORDER BY start_time, id
FOR UPDATE;
```

후보 인덱스는 다음과 같다.

```text
(member_id, lesson_date, active_slot_guard, start_time, id, end_time)
```

`active_slot_guard`가 DB 활성 상태 정의의 SSOT이므로 같은 상태 목록의 `status IN`은
중복 적용하지 않는다. 두 조건을 동시에 사용하면 의미가 늘지 않고 Java·migration 중
한쪽만 변경됐을 때 결과가 조용히 누락될 수 있다. `member_id`, `lesson_date`,
`active_slot_guard`는 동등 조건, `start_time`은 range 조건, `end_time`은 residual
filter다. M31 보정 migration 이후 실제 데이터로
`EXPLAIN ANALYZE`를 실행해 선택 인덱스, 예상·실제 행 수, filesort와 잠금 범위를
검증한다.

MySQL `REPEATABLE READ`에서 overlap `FOR UPDATE`는 후보 인덱스의 같은
`member_id/lesson_date/active_slot_guard`와 `start_time < candidateEndTime` 범위에
record/next-key lock을 만들 수 있다. 구간 부재 자체의 직렬화는 이 range lock에 의존하지
않고 먼저 획득한 정확한 member-day guard record lock이 책임진다. 따라서 실행 계획이
바뀌어도 두 경쟁 요청이 동시에 빈 overlap 결과를 보고 저장할 수 없다.

현재 V15 스키마에서 2026-07-22의 대표 데이터를 대상으로 45분을 식으로 계산한
`EXPLAIN ANALYZE`는 `uk_reservations_active_member_slot`의
`member_id/lesson_date/start_time` range scan으로 2행을 읽어 1행을 반환했고,
`ORDER BY id` filesort가 발생했다. 이는 기존 제약이 overlap 후보를 일부 줄일 수는 있지만
새 조회와 정렬을 만족하는 최종 인덱스가 아님을 보여준다.

### 오류 변환

- Application 선검사에서 overlap이 확인되면
  `RESERVATION_OVERLAPPING_ACTIVE_RESERVATION`과 `409 Conflict`를 반환한다.
- member-day guard로 정상 API 경쟁을 직렬화하므로 서로 다른 TimeSlot 경쟁도 같은
  선검사 경로를 사용한다.
- V15 exact-start UNIQUE는 부분집합 최종 방어로 유지한다.
- V15 위반으로 트랜잭션이 실패하면 rollback 뒤 별도 조회에서 실제 overlap을 재검증한
  경우에만 같은 오류로 변환한다. 원인을 확인할 수 없으면 일반 동시성 오류를 반환한다.
- 기존 `RESERVATION_DUPLICATE_ACTIVE_TIME_SLOT`은 외부 응답에서 사용하지 않는다.
  이미 배포된 모바일 클라이언트가 없으므로 생성 Client와 웹을 같은 Task에서 새 코드로
  전환하고, enum 상수는 한 호환 기간 뒤 제거한다.

## 잠금 순서

1. Idempotency row
2. `ScheduleConfigGuard`: 일반 일정 Command는 `FOR SHARE`, 설정 변경은 `FOR UPDATE`
3. 관련 `ScheduleDate` rows, 날짜 오름차순
4. `reservation_member_day_guards`, 회원·날짜 오름차순
5. 관련 `TimeSlotCapacity` rows, ID 오름차순
6. overlap 및 정원 집계 Reservation rows, 시작 시각·ID 오름차순
7. 대상 Reservation row가 앞 단계에 없으면 ID 잠금
8. Coupon row
9. Member row
10. 추가 전용 감사 로그

Template·정기 휴일 변경은 ScheduleConfigGuard를 `SYNCING`으로 커밋한 뒤 날짜별
동기화를 수행한다. 신규 유입 Command는 같은 guard를 공유 잠금하고 `ACTIVE`를 요구한다.
동기화와 휴무 전환은 각 날짜의 ScheduleDate를 잠가 같은 날짜의 변경을 직렬화한다.

Command별 실제 잠금 대상은
[Checkpoint 1 보정 Lock matrix](../tasks/mvp-3.1-checkpoint-1-remediation.md#lock-matrix-목표)를
따른다. 승인·입금 확인은 `ScheduleDate → Reservation`, 입금 만료 복구는
`ScheduleConfigGuard → ScheduleDate → member-day guard → TimeSlot → overlap/대상
Reservation`, 반려·자동 만료는 `ScheduleDate → member-day guard → Reservation →
Coupon(쿠폰 예약)` 순서다. 완료·노쇼는 Reservation을 먼저 잠그고 Coupon과 Member 효과를
후순위로 처리한다.

## 결과

- Reservation은 계속 정원 점유 SSOT이며 guard는 직렬화 수단일 뿐이다.
- 다른 시작 시각과 클래스의 겹치는 예약도 동시 요청에서 하나만 성공한다.
- exact-start UNIQUE를 보존하면서 외부 오류 의미는 실제 구간 중복으로 통합된다.
- guard 보존 비용은 회원·예약 날짜당 한 행이며 cleanup 경쟁보다 작다.

## 검증

- `10:00~10:45` 이후 `10:29`, `10:44` 시작은 실패하고 `10:45` 시작은 성공한다.
- 서로 다른 트랜잭션의 겹치는 예약 중 하나만 성공한다.
- 실패한 요청은 Coupon heldCount, Reservation과 정원 점유를 남기지 않는다.
- create, change, restore, cancel, expire와 출석 Command 교차 테스트에서 잠금 순서 역전이 없다.
- V15 위반 재검증은 실제 overlap일 때만 통합 오류를 반환한다.
- 모든 ReservationStatus에 대해 Java `occupiesCapacity/occupyingStatuses`와 DB
  `active_slot_guard` 결과가 일치하는 MySQL 계약 테스트를 통과한다.
