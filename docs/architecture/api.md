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
details { ... }
```

기존 `code`와 `message` 계약은 유지한다. 업무 예외의 `fieldErrors`는 빈 배열이며,
`@Valid`와 바인딩 오류는 `COMMON_INVALID_REQUEST`와 필드별 공개 검증 메시지를 반환한다.
거부된 입력값은 오류 응답에 포함하지 않는다. 상세 결정은 ADR-008을 따른다.
`details`는 항상 존재하며 값이 없으면 빈 객체다. 업무 오류는 후속 조치에 필요한 공개
값만 추가한다. 예를 들어 날짜 휴무 확정 충돌은 `activeReservationCount`를 반환한다.

## 관리자 예약 집계

```text
GET /api/admin/reservations/summary
  query: lessonDateFrom?, lessonDateTo?
  response:
    lessonDateFrom
    lessonDateTo
    totalCount
    statusCounts[] { status, count }
    dailyCounts[] { lessonDate, totalCount, statusCounts[] { status, count } }
```

날짜를 모두 생략하면 `Asia/Seoul` 기준 오늘을 시작일과 종료일로 사용한다. 한쪽만
지정하면 지정하지 않은 경계도 같은 날짜로 사용한다. 시작일이 종료일보다 늦으면
`RESERVATION_INVALID_QUERY_DATE_RANGE`로 거부한다. 상태별 집계는 `ReservationStatus` 전체를 고정
순서로 반환하고, 일자별 집계는 예약이 존재하는 날짜만 날짜·상태 순으로 반환한다.
조회는 예약, 정원, 쿠폰과 감사 이력을 변경하지 않는다.

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

`GET /api/me/reservations`는 `page`, `size`와 선택적 `displayGroup`
(`UPCOMING`, `PAST`), `status`(`ReservationStatus`의 외부 값) query parameter를
받는다. 필터를 생략하면 예정 예약 오름차순 뒤에 지난 예약 내림차순을 제공하는 기존
혼합 정렬을 유지한다. 필터를 지정하면 DB의 필터 결과를 기준으로 6필드 Page
메타데이터를 계산한다. 정확히 수업 시작 시각부터 `PAST`다.

예약 신청 응답은 `status`, `payment_source`, 선택된 쿠폰과 임시 점유 정보, `payment_due_at`을 포함한다.
회원 신규 예약은 `lessonStartAt - 3시간`까지 허용하며, 겹치는 활성 예약은
`RESERVATION_OVERLAPPING_ACTIVE_RESERVATION` 409로 반환한다.

변경 preview는 선택한 `target_time_slot_id`의 현재 정원과 변경 정책을 검증해 예상 쿠폰 처리를 반환하지만 데이터를 변경하지 않는다. 실제 변경 실행은 실행 시점에 같은 정책과 정원을 다시 검증한다.

취소 preview와 실행은 같은 서버 정책 판정기를 사용한다. 회원 취소 실행은 활성 예약을 즉시 `cancelled`로 전이하며 회원 책임과 사유를 감사 이력에 남긴다.

## 관리자 예약

```text
GET  /api/admin/reservations
POST /api/admin/reservations
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

관리자 수동 예약 생성은 필수 `Idempotency-Key` Header와
`memberId`, `timeSlotId`, `classType`, 필수 `reason`을 받는다. 관리자는 회원의 3시간
마감을 우회하지만 수업 시작 시각부터는 거부된다. 대상 회원의 유효한 본인 Coupon이
있으면 기존 자동 선택 순서로 1회 점유하고 `confirmed`, 없으면 `pending_payment`로
생성한다. 입금 마감은 생성 후 2시간과 수업 시작 시각 중 빠른 시각이다.

반려 API는 관리자 승인 전 예약만 `rejected`로 전이한다. 취소 API는 `cancelled`로 전이하므로 서로 대체하지 않는다.

관리자 취소 preview는 선택한 `cancellation_responsibility`에 따른 권장 `coupon_action`을 반환한다. 실행 요청은 `cancellation_responsibility`, 최종 `coupon_action`, 필수 `memo`를 받으며 preview의 권고와 달라도 예약 유형별 허용 범위 안이면 관리자 선택을 적용한다. 노쇼 요청도 `coupon_action`과 관리자 `memo`를 받는다.

일괄 출석 처리는 `lesson_date`, `start_time`, 최대 8개의 `items`를 받는다. 각 항목은 `reservation_id`, `action`(`complete` 또는 `no_show`)을 포함하며 노쇼에는 `coupon_action`과 `memo`가 필요하다. 구조적으로 유효한 요청은 HTTP 200으로 항목별 성공 여부와 최종 상태 또는 오류 코드·메시지를 반환한다. 항목은 독립 트랜잭션으로 처리되므로 부분 성공할 수 있다.

## 관리자 감사 이력

```text
GET /api/admin/audit-logs
  query:
    keyword?
    reservationId?
    occurredDateFrom?
    occurredDateTo?
    actorType?
    changeType?
    page? = 0
    size? = 20 (max 100)
  response:
    content[] {
      auditLogId
      reservationId
      memberId
      memberName
      actorAuthSubject
      actorType
      changeType
      fromStatus
      toStatus
      fromLessonDate
      fromStartTime
      toLessonDate
      toStartTime
      couponAction
      couponId
      paymentDueAt
      memo
      occurredAt
    }
    page
    size
    totalElements
    totalPages
    hasNext
```

기간을 생략하면 전체 예약 변경 이력을 조회하고 `occurredAt DESC`, `auditLogId DESC`로 정렬한다. `keyword`는 회원 이름과 전화번호를 통합 검색한다. 조회는 `ReservationChangeLog`를 수정하거나 현재 예약 상태에서 과거 이력을 합성하지 않으며 `ROLE_ADMIN`만 접근한다. 쿠폰 사용 원장은 M3-09에서 별도 다운로드 계약으로 제공한다.

## 관리자 감사 내역 다운로드

```text
GET /api/admin/audit-logs/export
  query:
    keyword?
    reservationId?
    occurredDateFrom?
    occurredDateTo?
    actorType?
    changeType?
  response: text/csv attachment

GET /api/admin/coupon-usage-logs/export
  query:
    keyword?
    couponId?
    reservationId?
    occurredDateFrom?
    occurredDateTo?
  response: text/csv attachment
```

예약 감사 CSV는 `ReservationChangeLog`, 쿠폰 사용 CSV는 `CouponUsageLog`를 각각 직접 조회한다. 두 응답은 UTF-8 BOM과 CRLF를 사용하며 모든 필드를 CSV quoting하고 수식 주입을 방어한다. 회원 전화번호는 검색에만 사용하고 파일에는 포함하지 않으며, 예약 파일은 `actorAuthSubject`를 제외한다. 두 API는 `ROLE_ADMIN`만 접근할 수 있고 `Cache-Control: no-store`를 반환한다.

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

`PATCH`는 호환 진입점으로 TimeSlotClosure 휴강 시작·철회·완료 후 재개 workflow에
위임하며 `is_closed` 또는 `admin_closed`를 직접 변경하지 않는다. close 요청은 신규
예약 유입을 차단하고 영향 예약을 자동 취소하지 않으며, reopen 요청은 현재 closure
상태에 따라 철회 또는 재개한다.
`DELETE`는 예약 이력이 없는 오생성 시간대만 물리 삭제하며, 예약 이력이 있으면 `409 Conflict`를 반환한다.

### Checkpoint 1 일정 운영 계약

M31-R11은 M31-R04~R10의 내부 Application workflow를 다음 관리자 API로 노출한다.

```text
GET    /api/admin/schedule-templates
POST   /api/admin/schedule-templates/preview
POST   /api/admin/schedule-templates
PUT    /api/admin/schedule-templates/{templateId}
PATCH  /api/admin/schedule-templates/{templateId}/activation

GET    /api/admin/recurring-holidays
POST   /api/admin/recurring-holidays/preview
POST   /api/admin/recurring-holidays
PUT    /api/admin/recurring-holidays/{holidayId}
PATCH  /api/admin/recurring-holidays/{holidayId}/activation

GET    /api/admin/schedule-dates?dateFrom=&dateTo=
GET    /api/admin/schedule-dates/{date}
GET    /api/admin/schedule-dates/{date}/closure-impact
POST   /api/admin/schedule-dates/{date}/closing
POST   /api/admin/schedule-dates/{date}/closing/cancel
POST   /api/admin/schedule-dates/{date}/close
POST   /api/admin/schedule-dates/{date}/reservations/{reservationId}/cancel-for-closure

GET    /api/admin/timeslots/{timeslotId}/closure-impact
POST   /api/admin/timeslots/{timeslotId}/closure
POST   /api/admin/timeslots/{timeslotId}/closure/complete
POST   /api/admin/timeslots/{timeslotId}/closure/withdraw
POST   /api/admin/timeslots/{timeslotId}/closure/reopen
POST   /api/admin/timeslots/{timeslotId}/reservations/{reservationId}/cancel-for-closure

GET    /api/admin/schedule-sync
POST   /api/admin/jobs/sync-schedule-occurrences/retry
```

날짜 `CLOSING` 취소는 저장된 `resumeStatus`인 `NORMAL` 또는 `OPEN`으로 복귀한다.
독립적인 `NORMAL → OPEN` 설정과 날짜 예외 해제 API는 현재 내부 workflow에 없으므로
M31-R11이 임의로 상태 전이를 추가하지 않는다. 해당 운영 진입점은 제품 전이 규칙을
확정한 후 별도 Task에서 공개한다.

일정 설정과 휴무 API는 모두 `ROLE_ADMIN`만 접근한다. 관리자 식별자는 요청 본문에서
받지 않고 JWT `sub`를 사용한다. 휴무 정리 취소는 요청에서 책임이나 쿠폰 처리를 받지
않으며 서버가 쿠폰 예약은 `stable/RETURN`, 1회 결제 예약은 `stable/NONE`으로 고정한다.

날짜와 TimeSlot closure impact 응답은 안정적인 예약 ID 순서로 활성 예약 또는 고정
영향 예약과 회원 운영 정보를 제공한다. 휴무 확정 시 활성 예약이 남아 있으면
`TIMESLOT_ACTIVE_RESERVATIONS_EXIST_ON_CLOSURE_DATE` 409와
`details.activeReservationCount`를 반환한다.

날짜 CLOSING 시작 감사에는 `initialReservationCount`를 저장한다. 날짜 impact 응답은
이 최초 건수와 현재 활성 예약 수를 기준으로 `resolvedReservationCount`,
`remainingReservationCount`, `progressPercent`를 반환한다. 날짜 영향 membership은
별도 snapshot 테이블로 고정하지 않고 현재 활성 예약 목록을 ID 순서로 반환한다.

기존 `PATCH /api/admin/timeslots/{id}`는 호환 기간 동안 유지하되 같은 휴강 Application
Service를 호출한다. 호환 요청 DTO의 `closed = true`는 활성 예약이 있으면 휴강
정리를 시작하고 없으면 즉시 확정한다. `closed = false`는 날짜 상태가 허용할 때
휴강을 취소하거나 재개한다. 두 값은 영속 필드 직접 쓰기가 아닌 workflow 선택
플래그이며, 웹은 진행률이 필요한 신규 closure API를 우선 사용한다.

신규 closure 완료·철회·재개 API는 필수 `expectedVersion`으로 낙관적 충돌을 검증한다.
호환 PATCH에는 기존 요청 계약상 version 필드가 없으므로 최신 상태를 잠근 뒤 멱등
workflow를 실행하는 best-effort 진입점으로만 유지한다. 신규 웹과 Expo는 호환 PATCH
대신 version을 제공하는 closure API를 사용한다.

`GET /api/admin/schedule-sync`는 active/pending version, 전체·완료 날짜 수, 시작·완료
시각과 마지막 실패 코드·요약을 반환한다. retry API는 현재 pending version만 다시
실행하며 새 version을 만들지 않는다.

실제 시각은 RFC 3339 `date-time`과 `+09:00` offset으로 반환한다. 구조화된 후속 조치
정보가 필요한 업무 오류는 기존 `ErrorResponse` 필드를 유지하면서 `details`에 값을
추가하고, 값이 없으면 빈 객체를 반환한다. `details`는 숫자·문자열 등 JSON 값을
허용하는 free-form map이며 생성 Client에서는 `Record<string, any>`에 해당한다.

ScheduleConfigGuard가 `SYNCING`이면 회원 예약 가능 TimeSlot 조회, 회원 예약 생성,
예약 변경, 관리자 수동 예약과 수동 TimeSlot 생성은
`SCHEDULE_CONFIG_SYNC_IN_PROGRESS` 503을 반환한다. 기존 예약·쿠폰·휴무 정리 조회와
휴무 정리 취소, 반려, 자동 입금·승인 만료, 상태 조회와 retry는 허용한다.
`Retry-After`는 안정적인 완료 예상값이 있을 때만 포함한다.

Template·정기 휴일 관리 Controller는 occurrence 동기화 엔진이 완료된 M31-R06 이후
M31-R11에서 함께 공개한다. R04·R05의 내부 Service만으로 외부에서 `ACTIVE → SYNCING`
전환을 실행할 수 없다.

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

`GET /api/admin/members`는 `page`(기본 0), `size`(기본 20, 최대 100)와 선택적
`query`(최대 100자)를 받는다. `query`를 생략하거나 공백으로 보내면 기존 전체 회원
목록을 유지한다. 검색어가 있으면 전체 회원의 이름 또는 전화번호를 부분 검색한 뒤
`createdAt DESC, id DESC`로 정렬하고 검색 결과 기준 Page 메타데이터를 반환한다.
이름은 대소문자를 구분하지 않고, 전화번호 검색은 하이픈·공백을 무시한다.
`%`와 `_`는 wildcard가 아닌 검색 문자열 자체로 취급한다.

## 관리자 작업

```text
POST /api/admin/jobs/expire-pending-payments
```

입금대기 만료는 Spring Scheduler가 Application Service를 직접 호출해 자동 실행한다. 관리자 수동 실행 API는 같은 Service를 호출하며 `ROLE_ADMIN`만 접근할 수 있다. 외부 Scheduler나 머신 인증이 필요한 시점에 시스템 작업 API와 별도 권한을 재검토한다.
