# M31 Command Lock Matrix

## 목적과 기준

이 문서는 M31-R14 완료 시점의 프로덕션 Command가 실제로 획득하는 MySQL 잠금을
Repository 메서드와 SQL까지 추적한 기준선이다. M31-06에서 발견한 순서 차이는
M31-07에서 보정했으며 아래 matrix는 M31-07 적용 후 실제 순서를 기록한다.

목표 순서는 다음과 같다.

`Idempotency -> ScheduleConfigGuard -> ScheduleDate -> member-day guard -> TimeSlot
-> TimeSlotClosure -> Reservation -> Coupon -> Member -> append-only audit`

여러 날짜, 회원 날짜 guard, TimeSlot, Closure 또는 Reservation을 잠글 때는 날짜·복합 키
또는 ID 오름차순을 사용한다. Idempotency는 M31-08, 관리자 수동 예약은 M31-09에서
추가하므로 현재 순서에는 아직 나타나지 않는다.

검증 환경은 MySQL 8.4, `REPEATABLE-READ`, Flyway V32다. 실행 가능한 근거는
`M31CommandLockPlanIntegrationTest`가 제공한다.

## 잠금 SQL과 실행 계획

| 단계 | Repository 메서드 | SQL 조건과 순서 | 인덱스와 MySQL 8.4 계획 | 잠금 범위 |
|---|---|---|---|---|
| Config 공유 | `ScheduleConfigGuardRepository.findSingletonForShare` | `id = 1 FOR SHARE` | `PRIMARY`, `const` | singleton record S lock |
| Config 변경 | `ScheduleConfigGuardRepository.findSingletonForUpdate` | `id = 1 FOR UPDATE` | `PRIMARY`, `const` | singleton record X lock |
| 날짜 단건 | `ScheduleDateRepository.findByScheduleDateForUpdate` | `schedule_date = ? FOR UPDATE` | `uk_schedule_dates_date`, `const` | 존재 행 record lock, 부재 시 unique gap |
| 날짜 범위 | `findAllByScheduleDateBetweenForUpdate` | `FORCE INDEX (uk_schedule_dates_date)`, `BETWEEN`, 날짜순 | `range`, `uk_schedule_dates_date`, filesort 없음 | 범위의 record와 next-key lock |
| 회원·날짜 guard | `ReservationMemberDayGuardRepository.acquire` | PK upsert 후 같은 `(member_id, lesson_date) FOR UPDATE` | `PRIMARY`, `const` | 복합 PK record lock. upsert와 조회는 같은 트랜잭션 |
| TimeSlot 단건 | `TimeSlotCapacityRepository.findByIdForUpdate` | `id = ? FOR UPDATE` | `PRIMARY`, `const` | TimeSlot record lock |
| TimeSlot 날짜·시각 | `findByLessonDateAndStartTimeForUpdate` | unique key equality | `uk_time_slot_capacities_lesson_date_start_time`, `const` | 존재 record, 부재 unique gap |
| 날짜의 TimeSlot | `findAllByLessonDateForUpdateOrdered` | `FORCE INDEX (idx_time_slot_capacities_lesson_date_id)`, ID순 | `ref`, `(lesson_date, id)`, filesort 없음 | 해당 날짜 index range의 next-key lock |
| 회원 수업 겹침 | `ReservationRepository.findActiveOverlapsForUpdate` | member/date/active equality, `start_time < ?`, `end_time > ?`, 시작·ID순 | `range`, V28 index의 member/date/active/start 사용, `end_time`은 index condition의 잔여 구간 판정 | V28 equality prefix 아래 `start_time` 범위의 record·next-key·gap lock |
| 시간대 점유 | `findOccupyingByLessonDateAndStartTimeForUpdate` | date/start equality, status IN, ID순 | V32 격리 fixture는 `idx_reservations_occupancy`를 후보로 두지만 ID순을 위해 `PRIMARY` index scan, filesort 없음. 운영 개발 DB에서는 occupancy `range`와 filesort도 관측됨 | 선택된 실행 계획이 검사한 index records/gaps |
| 시간대 예약 이력 | `findHistoryIdsByLessonDateAndStartTimeForUpdate` | occupancy index 강제, date/start equality, ID순 | `ref`, `idx_reservations_occupancy`, ID순 filesort | 해당 date/start의 전체 Reservation index records |
| Reservation 단건 | `ReservationRepository.findByIdForUpdate` | `id = ? FOR UPDATE` | `PRIMARY`, `const` | Reservation record lock |
| Closure 진행 중 | `TimeSlotClosureRepository.findInProgressByTimeSlotIdForUpdate` | timeSlot/status equality, ID순 | `uk_time_slot_closures_active`의 `time_slot_id` prefix | 해당 TimeSlot의 closure record·gap |
| Closure 전체 | `findAllByTimeSlotIdForUpdate` | timeSlot equality, ID순 | `uk_time_slot_closures_active` 또는 history index | 해당 TimeSlot의 closure records와 gap |
| Coupon 단건 | `CouponRepository.findByIdForUpdate` | `id = ? FOR UPDATE` | `PRIMARY`, `const` | Coupon record lock |
| Coupon 자동 선택 | `CouponRepository.findFirstSelectableForUpdate` | member/type/active/잔여/수업일, 만료·생성·ID순, `LIMIT 1 FOR UPDATE` | `idx_coupons_member_status_expiry`의 member/status 사용, 정렬식 때문에 filesort | 인덱스로 검사한 유효 후보 records; 반환 1행만 잠긴다고 가정하지 않음 |
| Member 단건 | `MemberRepository.findByIdForUpdate` | `id = ? FOR UPDATE` | `PRIMARY`, `const` | Member record lock |

핵심 범위 SQL은 다음과 같다.

```sql
SELECT reservation.*
FROM reservations reservation
FORCE INDEX (idx_reservations_member_date_active_interval)
WHERE reservation.member_id = :memberId
  AND reservation.lesson_date = :lessonDate
  AND reservation.active_slot_guard = 1
  AND reservation.start_time < :candidateEndTime
  AND reservation.end_time > :candidateStartTime
ORDER BY reservation.start_time, reservation.id
FOR UPDATE;
```

```sql
SELECT reservation.*
FROM reservations reservation
WHERE reservation.lesson_date = :lessonDate
  AND reservation.start_time = :startTime
  AND reservation.status IN (:occupyingStatuses)
ORDER BY reservation.id
FOR UPDATE;
```

```sql
SELECT coupon.*
FROM coupons coupon
WHERE coupon.member_id = :memberId
  AND coupon.coupon_type = :couponType
  AND coupon.status = 'active'
  AND coupon.remaining_count > coupon.held_count
  AND (coupon.expires_at IS NULL OR DATE(coupon.expires_at) >= :lessonDate)
ORDER BY (coupon.expires_at IS NULL) ASC,
         coupon.expires_at ASC,
         coupon.created_at ASC,
         coupon.id ASC
LIMIT 1
FOR UPDATE;
```

`EXPLAIN FORMAT=JSON`에서 날짜 범위와 overlap은 `range`, 날짜별 TimeSlot은 `ref`,
단건 PK/UNIQUE는 `const`를 사용한다. overlap의 `used_key_parts`는 `member_id`,
`lesson_date`, `active_slot_guard`, `start_time`이며 `EXPLAIN ANALYZE`에서도 V28
인덱스 range scan이 실행된다. V32 격리 fixture에서 점유 조회는
`idx_reservations_occupancy`를 `possible_keys`로 식별하지만 `ORDER BY id`를 만족시키기
위해 `PRIMARY` index scan을 선택한다. 반면 현재 운영 개발 DB의 별도 EXPLAIN에서는
occupancy `range`와 filesort가 관측됐다. Coupon 선택은
`idx_coupons_member_status_expiry`와 filesort를 사용한다. 특정 인덱스를 강제하지 않는
점유 SQL의 잠금 범위는 통계와 카디널리티에 따라 달라진다.

## M31-07 적용 후 Command matrix

`현재 순서`는 조회용 snapshot을 제외하고 실제 비관적 잠금만 적는다. `Closure?`는 해당
TimeSlot에 진행 중 휴강이 있을 때 함께 잠긴다는 뜻이다.

| Command | 현재 순서 | 판정과 교차 대기 |
|---|---|---|
| 회원 예약 생성, 쿠폰 | Config S -> Date -> member-day -> TimeSlot -> overlap -> 점유 -> Coupon -> audit | 목표 순서와 일치 |
| 회원 예약 생성, 1회 결제 | Config S -> Date -> member-day -> TimeSlot -> overlap -> 점유 -> audit | 목표 순서와 일치 |
| 예약 변경 | Config S -> Date 오름차순 -> member-day 날짜순 -> TimeSlot ID순 -> 대상 overlap -> 대상 점유 -> Reservation -> Coupon? -> audit | 목표 순서와 일치 |
| 입금 만료 복구 | Config S -> Date -> member-day -> TimeSlot -> overlap -> 점유 -> Reservation -> audit | 활성 집합 재진입을 guard로 직렬화 |
| 예약 승인·입금 확인 | Date -> TimeSlot -> Closure? -> Reservation -> Coupon? | 활성 상태 유지. 휴강 시작과 TimeSlot에서 교차 대기 |
| 예약 반려 | Date -> member-day -> Reservation -> Coupon? -> audit | 활성 집합 이탈. 생성·변경과 member-day에서 교차 대기 |
| 자동 승인 만료 | Date -> member-day -> Reservation -> Coupon -> audit | 반려와 같은 이탈 순서 |
| 자동 입금 만료 | Date -> member-day -> Reservation -> audit | 반려와 같은 이탈 순서 |
| 회원·일반 관리자 취소 | Date -> member-day -> TimeSlot -> Closure? -> Reservation -> Coupon? -> audit | 휴강 중 일반 관리자 취소는 gate에서 차단 |
| 날짜 휴무 전용 취소 | Date -> member-day -> Reservation -> Coupon? -> audit | 날짜 CLOSING과 Date에서 직렬화 |
| TimeSlot 휴무 전용 취소 | Date -> member-day -> TimeSlot -> Closure -> Reservation -> Coupon? -> audit | 날짜 휴무 전용 취소와 Date/member-day/Reservation 순서 일치 |
| 수업 완료 | Date -> TimeSlot -> Closure? -> Reservation -> Coupon? -> Member | Coupon 예약은 CouponUsageLog까지 Coupon 잠금 안에서 반영한 뒤 Member를 잠금 |
| 노쇼 | Date -> TimeSlot -> Closure? -> Reservation -> Coupon? -> audit | 목표 순서와 일치 |
| 수동 TimeSlot 생성 | Config S -> Date -> unique insert | 동기화와 Config/Date에서 직렬화 |
| TimeSlot 정원 변경 | Config S -> Date -> TimeSlot -> 점유 Reservation | CLOSING/CLOSED·설정 동기화와 Date에서 직렬화하고 최신 상태 재검증 |
| TimeSlot 삭제 | Config S -> Date -> TimeSlot -> 예약 이력 FOR UPDATE -> delete | Date 전환 뒤 최신 이력을 current read로 재검증하며 이력 존재 시 기존 409 정책 유지 |
| 날짜 CLOSING·CLOSED·복귀 | Date -> 활성 Reservation ID/건수 재검사 -> audit | 모든 유입 Command가 Date를 먼저 잠그는 계약에 의존 |
| 개별 TimeSlot 휴강 시작 | Date -> TimeSlot -> Closure 전체 -> 점유 Reservation ID순 -> Impact/audit | 목표 순서와 일치 |
| 개별 휴강 완료·철회·재개 | Date -> TimeSlot -> Closure -> 점유 Reservation? -> audit | 목표 순서와 일치 |
| occurrence 날짜 동기화 | Config S -> Date -> TimeSlot ID순 | 예약 유입과 Config/Date/TimeSlot에서 직렬화 |
| Template·정기 휴일 변경 | Config X, ACTIVE -> SYNCING | 설정 변경 commit 후 occurrence 동기화는 별도 짧은 트랜잭션 |
| 설정 동기화 완료 | Config X -> Date 범위 | 모든 날짜의 applied version 재검사 |
| Coupon 만료 Job | Coupon ID순 -> audit | Reservation/Date를 잠그지 않는 Coupon 단독 흐름 |

## 교차 대기와 M31-07 입력

| 교차 Command | 공통 잠금과 대기 방향 | 현재 위험 | M31-07 회귀 기준 |
|---|---|---|---|
| 생성 vs 생성·변경·복구 | Date -> member-day -> TimeSlot -> Reservation range | 같은 회원·날짜와 같은 TimeSlot 요청은 직렬화됨 | 순서 유지, 1213 재시도 후 예약·점유 exactly-once |
| 생성·변경 vs occurrence 동기화 | Config S -> Date -> TimeSlot | 같은 방향 | Config가 SYNCING이면 유입 503, 부분 occurrence 생성 금지 |
| 생성·변경 vs 날짜 CLOSING | Date | 먼저 Date를 얻은 트랜잭션 후 상태 재검사 | CLOSING 이후 활성 예약 유입 0건 |
| 생성·변경 vs TimeSlot 휴강 | Date -> TimeSlot | 휴강이 `adminClosed`를 바꾼 뒤 유입이 상태 재검사 | 휴강 snapshot 이후 신규 활성 예약 0건 |
| 반려·만료·취소 상호 경쟁 | Date -> member-day -> Reservation -> Coupon? | 같은 Reservation은 record lock으로 직렬화 | 첫 상태 변경만 Coupon·audit 반영 |
| 완료 vs Coupon 종료 Command | Reservation -> Coupon -> Member | 공통 Coupon 종료 흐름과 같은 방향 | 완료·노쇼 경쟁에서 첫 상태 변경만 Coupon·audit·기승 횟수 반영 |
| TimeSlot 정원 변경·삭제 vs 동기화·날짜 휴무 | Config S -> Date -> TimeSlot | 같은 방향 | CLOSING 전환 잠금 뒤 대기하고 최신 날짜 상태로 두 Command 모두 거부 |
| Coupon 자동 선택 vs Coupon 만료 | Coupon 후보와 Coupon ID | 후보 range/filesort와 ID순 만료가 넓게 교차할 수 있음 | 실제 1213만 전체 트랜잭션 밖 최대 3회 재시도 |

## InnoDB 해석 기준

- 단건 PK/UNIQUE의 존재 행은 record lock이다. 검색 키가 없으면 삽입 위치의 gap을 잠글 수
  있으므로 "조회 결과 0건"을 "잠금 0개"로 해석하지 않는다.
- `REPEATABLE READ`의 `FOR UPDATE` 범위 조회는 검색한 인덱스 범위에 record와
  next-key lock을 잡는다. M31 통합 테스트는 overlap 결과가 없어도 같은 member/date의
  범위 안 삽입이 lock wait timeout으로 차단되는 것을 재현한다.
- `end_time > :candidateStartTime`은 V28 인덱스 순서상 range 경계를 더 줄이지 못한다.
  `start_time` 범위에서 검사한 뒤 겹침을 판정하므로 실제 잠금 범위는 반환 행 수보다 클 수
  있다.
- 점유 조회의 `status IN`과 Coupon 선택의 filesort 역시 반환 행 수만으로 잠금 행 수를
  추정할 수 없다. M31-07은 실행 계획을 유지하거나 더 좁아졌음을 별도 EXPLAIN으로
  증명해야 한다.

## M31-07 완료 Gate

1. 위 두 구조적 차이인 수업 완료의 `Member -> Coupon`, TimeSlot 변경·삭제의 Date 누락을
   제거한다.
2. 모든 Command별 실제 호출 순서 테스트 또는 교차 MySQL 테스트가 canonical 순서를
   깨는 회귀를 탐지한다.
3. MySQL 오류 1213만 트랜잭션 경계 밖에서 최초 실행 포함 최대 3회 backoff+jitter로
   재시도한다. lock timeout, 업무·검증·인증 오류는 재시도하지 않는다.
4. 재시도와 동시 실행 뒤 Reservation 상태, Coupon `remainingCount/heldCount`, Member
   기승 횟수와 append-only audit가 논리적으로 정확히 한 번만 반영된다.
5. 변경된 SQL의 `EXPLAIN FORMAT=JSON`, `EXPLAIN ANALYZE`, 사용 인덱스와 잠금 범위를
   이 문서와 실행 테스트에 다시 반영한다.

M31-07은 기존 인덱스와 migration을 변경하지 않았다. TimeSlot ID/date snapshot은
잠금 근거가 아니며, 삭제 예약 이력 조회만 `idx_reservations_occupancy`를 강제한
`FOR UPDATE` 현재 읽기로 보강했다. 실행 계획과 잠금 범위는
`M31CommandLockPlanIntegrationTest`에서 검증한다.

실제 MySQL 검증은 다음을 고정한다.

- `DeadlockRetryIntegrationTest`: 역순 Member 잠금으로 1213/40001을 재현하고 피해
  트랜잭션 rollback 뒤 새 transaction·Persistence Context에서 전체 Command를 재시도한다.
  최종 Reservation 점유 해제, Coupon 수량, CouponUsageLog와 기승 횟수는 각각 한 번만
  반영된다.
- `GeneralRideCompletionApiTest`: 완료와 노쇼가 같은 Reservation·Coupon에서 경쟁해도
  한 Command만 종료 효과를 반영한다.
- `AdminTimeSlotScheduleDateLockIntegrationTest`: CLOSING 전환이 Date 잠금을 보유한
  동안 정원 변경·삭제가 대기하고, 커밋 뒤 최신 상태로 거부된다.
