> 이 파일은 초기 대화 기록 보존용이다. 구현의 SSOT는 `docs/product/`, `docs/architecture/`, `docs/tasks/`이며 충돌하면 `docs/`를 따른다. AI loop의 실행 입력으로 이 파일을 사용하지 않는다.

# 1차 개발

## 0. 모노레포 프로젝트 구조

이 프로젝트는 백엔드와 프론트엔드를 함께 개발하는 모노레포로 구성한다.

초기 루트 구조:

```text
horse/
  backend/
    README.md
  frontend/
    README.md
  TMP.md
```

`backend/`는 예약, 회원, 쿠폰, 정원, 관리자 처리 API와 비즈니스 규칙을 담당한다.

`frontend/`는 회원 웹 화면과 관리자 웹 화면을 담당한다.

현재 단계에서는 프레임워크를 바로 생성하지 않고 README 기반의 골격만 만든다. FastAPI/NestJS/Django REST Framework, Next.js/Vite React/Remix 같은 선택지는 다음 단계에서 확정한다.

최종적으로는 다음과 같은 확장 구조를 목표로 한다.

```text
horse/
  backend/
    src/
      modules/
        members/
        reservations/
        coupons/
        timeslots/
        admin/
      shared/
    tests/
  frontend/
    src/
      app/
      features/
        reservations/
        coupons/
        members/
        admin/
      shared/
    public/
    tests/
```

## 1. 핵심 예약 정책

모든 예약은 관리자 확정 전까지 확정 예약이 아니다.

예약 신청 상태에서도 정원은 점유한다. 단, 입금대기는 2시간이 지나면 만료 처리하고 정원을 반환한다.

### 예약 흐름

```text
유효 쿠폰 있음
→ 회원 예약 신청
→ 관리자 승인대기
→ 정원 점유
→ 사용 예정 쿠폰 자동 선택 및 회원에게 표시
→ 관리자 확정
→ 예약확정
→ 쿠폰 1회 점유
→ 수업완료
→ 쿠폰 차감 확정
```

```text
유효 쿠폰 없음
→ 회원 예약 신청
→ 입금대기
→ 정원 점유
→ 2시간 내 입금 안내
→ 관리자 입금 확인
→ 예약확정
→ 결제 방식: 1회 결제
→ 수업완료
→ 쿠폰 차감 없음
```

```text
입금대기 2시간 초과
→ 입금대기 만료
→ 정원 반환
→ 회원에게 관리자 연락 안내
```

입금대기 만료 안내 문구:

```text
입금 확인 시간이 지나 예약이 확정되지 않았습니다.
예약을 원하시면 관리자에게 연락해 주세요.
```

## 2. 예약 상태

```text
pending_admin_approval  쿠폰 보유 회원의 관리자 승인대기
pending_payment         쿠폰 없는 회원의 입금대기
payment_expired         입금대기 2시간 만료
confirmed               예약확정
completed               수업완료
cancelled               취소
no_show                 노쇼
```

정원 계산에 포함되는 상태:

```text
pending_admin_approval
pending_payment
confirmed
```

정원 계산에서 제외되는 상태:

```text
payment_expired
completed
cancelled
no_show
```

## 3. 클래스 정책

일반 기승 클래스:

```text
왕초보: 0회
원형초보: 1-5회
원형 속보: 6-20회
대마장초보: 21-25회
대마장 속보: 26회 이상
```

특수 클래스:

```text
마장마술
장애물
```

특수 클래스는 횟수 기준이 아니라 관리자 상담 후 승인된 회원만 예약할 수 있다.

마장마술/장애물 승인 회원은 일반 기승 예약 시 대마장 클래스도 예약 가능하게 한다.

마장마술/장애물 탑승 이력은 일반 기승 횟수와 별도 집계한다. 일반 기승 등급 산정에는 일반 기승 완료 횟수만 사용한다.

회원은 자기 현재 등급보다 낮은 클래스도 예약할 수 있다. 초보 우선권은 1차 개발에서는 제외한다.

## 4. 쿠폰 및 결제 정책

쿠폰 종류:

```text
일반 10회권
마장마술 10회권
장애물 10회권
```

결제 방식:

```text
coupon          보유 쿠폰 사용
single_payment  1회 결제
```

쿠폰 규칙:

- 예약하려는 클래스와 쿠폰 종류가 맞아야 한다.
- 일반 기승 클래스는 일반 쿠폰을 사용한다.
- 마장마술 클래스는 마장마술 쿠폰을 사용한다.
- 장애물 클래스는 장애물 쿠폰을 사용한다.
- 유효 쿠폰이 있으면 시스템이 만료일이 가장 가까운 쿠폰을 자동 선택한다.
- 회원에게 사용 예정 쿠폰, 만료일, 잔여 횟수 변화를 시각적으로 보여준다.
- 쿠폰 유효기간은 첫 기승일 기준 3개월이다.
- 만료일 이후 기승은 불가하다.
- 만료된 쿠폰의 잔여 횟수는 소멸 처리한다.
- 만료된 쿠폰에서는 차감하지 않는다.
- 쿠폰당 무료 변경권은 1회다.

쿠폰 없는 예약은 쿠폰을 생성하지 않고 `single_payment`로 처리한다.

10회권을 구매한 경우에만 관리자가 회원에게 신규 쿠폰을 등록한다.

## 5. 변경 정책

변경 마감 기준은 예약일 전날 21:00이다.

```text
전날 21시 전:
- 날짜 변경 가능
- 시간 변경 가능
- 쿠폰 차감 없음
- 무료 변경권 사용 없음
```

```text
전날 21시 후:
- 무료 변경권이 있으면 날짜/시간 변경 가능
- 이 경우 쿠폰의 무료 변경권을 사용 처리
- 무료 변경권이 없으면 평일에 한해 당일 내 다른 시간대로만 변경 가능
- 평일 당일 내 시간 변경은 무료 변경권을 사용하지 않음
- 주말은 당일 시간 변경 불가
```

평일 당일 내 시간 변경은 같은 날짜 안에서 시간만 바꾸는 경우를 의미한다.

## 6. 취소 정책

기본 정책:

```text
마감 전 취소: 쿠폰 반환 또는 1회 결제 취소
마감 후 회원 사유 취소: 기본 차감
마장 사유 취소: 반환
노쇼: 차감
```

관리자가 취소 처리할 때는 차감/반환을 직접 선택할 수 있어야 한다.

관리자 취소 처리에는 사유 메모가 필수다.

회원이 아파서 못 오는 경우처럼 회원 책임이지만 마장에서 예외적으로 차감하지 않는 경우가 있으므로, 최종 쿠폰 처리 결과는 관리자가 선택한다.

## 7. 정원 및 리소스 정책

관리자가 각 날짜/시간/클래스별 정원을 설정한다.

시스템은 관리자 설정 위에 물리 제한을 추가 검증한다.

기본 제한:

```text
한 타임 전체 예약 수 <= 8명
원형 클래스 합계 <= 4명
클래스별 예약 수 <= 관리자 설정 정원
```

마장 리소스 참고:

```text
원형마장 2개
원형마장 1개당 회원 2명
실내 대마장 1개
야외 대마장 1개
일반 기승 기준 한 타임 말 8마리
최대 강사 3명
```

1차 개발에서는 강사/실내/야외 자동 배정은 구현하지 않는다.

장애물/마장마술이 실내에서 진행될지 야외에서 진행될지는 관리자가 운영 판단으로 결정한다.

관리자 화면에는 타임별 현재 예약 구성은 보여준다.

예:

```text
2026-07-10 10:00
전체 6/8명
원형 3/4명

왕초보 1/2
원형초보 2/2
대마장속보 3/4
```

## 8. 데이터 모델 초안

### Member

```text
id
name
phone
general_ride_count
dressage_approved
jumping_approved
large_arena_allowed
created_at
updated_at
```

### Coupon

```text
id
member_id
type: general | dressage | jumping
total_count
remaining_count
first_used_at
expires_at
free_change_used
status: active | expired | depleted
created_at
updated_at
```

### Reservation

```text
id
member_id
class_type
lesson_date
start_time
status
payment_source: coupon | single_payment
coupon_id nullable
planned_coupon_id nullable
admin_confirmed_at nullable
payment_due_at nullable
cancelled_at nullable
cancellation_responsibility: member | stable | exception nullable
coupon_action: deduct | return | none nullable
admin_memo nullable
created_at
updated_at
```

`planned_coupon_id`는 관리자 승인대기 상태에서 사용 예정 쿠폰을 보여주기 위한 값이다.

관리자 확정 시점에는 쿠폰 유효성, 잔여 횟수, 만료일을 다시 검사한 뒤 `coupon_id`로 확정한다.

### TimeSlotCapacity

```text
id
lesson_date
start_time
total_capacity
round_arena_capacity
class_capacity_json
created_at
updated_at
```

### CouponUsageLog

```text
id
coupon_id
reservation_id
member_id
action: planned | reserved | used | returned | deducted | expired | free_change_used
used_at
memo
created_at
```

### ReservationChangeLog

```text
id
reservation_id
member_id
actor_type: member | admin
from_lesson_date
from_start_time
to_lesson_date
to_start_time
change_type: before_deadline | free_change | weekday_same_day | paid_change
coupon_action: none | free_change_used | deduct
memo
created_at
```

## 9. 예약 가능 여부 판정 로직

예약 신청 시 검사:

```text
1. 회원 등급으로 예약 가능한 일반 클래스인지 확인
2. 마장마술/장애물은 승인 회원인지 확인
3. 해당 타임 전체 정원 8명 이하인지 확인
4. 원형 정원 4명 이하인지 확인
5. 클래스별 관리자 설정 정원을 넘지 않는지 확인
6. 클래스에 맞는 유효 쿠폰이 있는지 확인
```

결과:

```text
유효 쿠폰 있음
→ payment_source = coupon
→ status = pending_admin_approval
→ planned_coupon_id = 만료일이 가장 가까운 유효 쿠폰
→ 정원 점유
```

```text
유효 쿠폰 없음
→ payment_source = single_payment
→ status = pending_payment
→ payment_due_at = 예약 신청 시점 + 2시간
→ 정원 점유
```

관리자 확정 시 재검사:

```text
1. 예약 상태가 확정 가능한 상태인지 확인
2. 정원 조건이 여전히 유효한지 확인
3. coupon 예약이면 쿠폰이 아직 유효한지 확인
4. coupon 예약이면 쿠폰 잔여 횟수가 있는지 확인
5. 특수 클래스 승인 상태가 유지되는지 확인
```

## 10. 회원 화면 목록

### 회원 홈

- 다음 예약 요약
- 예약 상태 표시
- 일반 기승 완료 횟수
- 현재 예약 가능 클래스
- 보유 쿠폰 요약
- 입금대기/승인대기 알림

### 예약 달력

- 날짜별 예약 가능 여부 표시
- 클래스 필터
- 가능한 시간대 표시
- 정원 마감 표시
- 회원 등급/승인 상태에 따라 예약 불가 클래스 숨김 또는 비활성화

### 예약 신청 화면

- 선택한 날짜/시간/클래스 확인
- 사용 예정 쿠폰 표시
- 쿠폰 만료일 표시
- 예약 후 잔여 횟수 예상 표시
- 유효 쿠폰이 없으면 1회 결제 입금대기 안내
- 신청 버튼

### 내 예약 목록

- 승인대기
- 입금대기
- 예약확정
- 수업완료
- 취소
- 입금대기 만료

### 예약 상세 화면

- 날짜/시간/클래스
- 예약 상태
- 결제 방식
- 쿠폰 사용 예정 또는 사용 확정 내역
- 입금대기 만료 시간
- 변경/취소 가능 여부
- 관리자 메모 표시 여부

### 예약 변경 화면

- 변경 가능 시간대 목록
- 전날 21시 전/후 정책 안내
- 무료 변경권 사용 여부 표시
- 평일 당일 내 시간 변경 가능 여부 표시
- 주말 당일 변경 불가 안내

### 예약 취소 화면

- 취소 마감 여부 표시
- 예상 쿠폰 처리 안내
- 취소 사유 입력
- 관리자 최종 처리 대상임을 안내

### 내 쿠폰 화면

- 쿠폰 종류
- 잔여 횟수
- 만료일
- 무료 변경권 사용 여부
- 상태: 활성/만료/소진

### 쿠폰 사용 내역 화면

- 사용 날짜
- 예약 날짜/시간
- 클래스
- 처리 상태: 예정/점유/사용확정/반환/차감/만료
- 잔여 횟수 변화

## 11. 관리자 화면 목록

### 관리자 대시보드

- 오늘 예약 현황
- 승인대기 예약
- 입금대기 예약
- 입금대기 만료 예약
- 취소/변경 요청
- 수업 완료 처리 대상

### 예약 관리

- 날짜/시간별 예약 목록
- 상태별 필터
- 회원별 검색
- 클래스별 필터
- 예약 상세 진입

### 예약 상세/확정 화면

- 회원 정보
- 예약 클래스
- 날짜/시간
- 결제 방식
- 사용 예정 쿠폰
- 쿠폰 유효성
- 정원 현황
- 관리자 확정 버튼
- 반려/취소 버튼
- 관리자 메모

### 입금대기 관리

- 입금대기 예약 목록
- 입금 마감 시간
- 만료 여부
- 입금 확인 후 예약확정
- 만료 예약 복구 시도
- 정원 부족 시 복구 불가 안내

### 수업 완료 처리

- 날짜/시간별 일괄 완료
- 출석/노쇼 처리
- 쿠폰 차감 확정
- 1회 결제 예약 완료 처리
- 일반 기승 횟수 증가
- 마장마술/장애물 별도 이력 증가

### 변경/취소 처리

- 회원 요청 변경/취소 목록
- 마감 전/후 자동 판정
- 쿠폰 차감/반환 선택
- 무료 변경권 사용 처리
- 관리자 예외 처리
- 사유 메모 필수 입력

### 타임/정원 설정

- 날짜별 시간대 생성
- 전체 정원 설정
- 원형 정원 설정
- 클래스별 정원 설정
- 휴무/예약마감 설정

### 회원 관리

- 회원 목록
- 회원 상세
- 일반 기승 완료 횟수
- 마장마술 승인 여부
- 장애물 승인 여부
- 대마장 허용 여부
- 예약 이력
- 결제/쿠폰 이력

### 쿠폰 관리

- 신규 쿠폰 등록
- 쿠폰 종류 선택
- 총 횟수 설정
- 첫 기승일/만료일 확인
- 잔여 횟수 수정
- 만료/소진 처리
- 무료 변경권 사용 여부 확인

### 쿠폰 사용 로그

- 회원별 사용 내역
- 예약별 사용 내역
- 반환/차감/만료 내역
- 관리자 메모

## 12. API 목록 초안

API 경로는 REST 기준 초안이다. 실제 프로젝트 구조에 맞춰 네이밍은 조정 가능하다.

### 회원 API

```text
GET /api/me
```

내 회원 정보, 일반 기승 횟수, 특수 클래스 승인 여부 조회.

```text
GET /api/me/eligible-classes
```

현재 회원이 예약 가능한 클래스 목록 조회.

```text
GET /api/timeslots?date=YYYY-MM-DD&classType=...
```

특정 날짜/클래스 기준 예약 가능한 시간대 조회.

```text
POST /api/reservations
```

예약 신청.

요청:

```text
class_type
lesson_date
start_time
```

응답:

```text
status: pending_admin_approval | pending_payment
payment_source: coupon | single_payment
planned_coupon nullable
payment_due_at nullable
```

```text
GET /api/me/reservations
```

내 예약 목록 조회.

```text
GET /api/me/reservations/{reservationId}
```

내 예약 상세 조회.

```text
POST /api/me/reservations/{reservationId}/change
```

예약 변경 요청.

요청:

```text
lesson_date
start_time
reason nullable
```

```text
POST /api/me/reservations/{reservationId}/cancel
```

예약 취소 요청.

요청:

```text
reason
```

```text
GET /api/me/coupons
```

내 쿠폰 목록 조회.

```text
GET /api/me/coupon-usage-logs
```

내 쿠폰 사용 내역 조회.

### 관리자 예약 API

```text
GET /api/admin/reservations
```

예약 목록 조회.

쿼리:

```text
date
status
memberId
classType
```

```text
GET /api/admin/reservations/{reservationId}
```

예약 상세 조회.

```text
POST /api/admin/reservations/{reservationId}/confirm
```

예약 확정.

쿠폰 예약이면 관리자 확정 시 쿠폰을 점유한다.

1회 결제 예약이면 입금 확인 후 확정한다.

```text
POST /api/admin/reservations/{reservationId}/reject
```

예약 반려.

요청:

```text
memo
```

```text
POST /api/admin/reservations/{reservationId}/cancel
```

관리자 취소 처리.

요청:

```text
cancellation_responsibility: member | stable | exception
coupon_action: deduct | return | none
memo
```

```text
POST /api/admin/reservations/{reservationId}/change
```

관리자 예약 변경 처리.

요청:

```text
lesson_date
start_time
coupon_action: none | free_change_used | deduct
memo
```

```text
POST /api/admin/reservations/{reservationId}/complete
```

수업 완료 처리.

쿠폰 예약이면 쿠폰 차감 확정.

1회 결제 예약이면 결제 예약 완료 처리.

일반 기승이면 일반 기승 횟수 증가.

특수 클래스면 별도 이력 증가.

```text
POST /api/admin/reservations/{reservationId}/no-show
```

노쇼 처리.

요청:

```text
coupon_action: deduct | return | none
memo
```

```text
POST /api/admin/reservations/complete-bulk
```

타임별 수업 일괄 완료 처리.

요청:

```text
lesson_date
start_time
reservation_ids
```

### 관리자 입금대기 API

```text
GET /api/admin/pending-payments
```

입금대기 예약 목록 조회.

```text
POST /api/admin/reservations/{reservationId}/expire-payment
```

입금대기 수동 만료 처리.

```text
POST /api/admin/reservations/{reservationId}/restore-payment
```

입금대기 만료 예약 복구 시도.

현재 정원이 남아 있을 때만 복구 가능하다.

### 관리자 타임/정원 API

```text
GET /api/admin/timeslots
```

타임 목록 조회.

```text
POST /api/admin/timeslots
```

타임 생성.

```text
PATCH /api/admin/timeslots/{timeslotId}
```

타임 수정.

```text
DELETE /api/admin/timeslots/{timeslotId}
```

타임 삭제 또는 비활성화.

```text
PUT /api/admin/timeslots/{timeslotId}/capacity
```

전체 정원, 원형 정원, 클래스별 정원 설정.

### 관리자 회원 API

```text
GET /api/admin/members
```

회원 목록 조회.

```text
GET /api/admin/members/{memberId}
```

회원 상세 조회.

```text
PATCH /api/admin/members/{memberId}
```

회원 정보 수정.

```text
PATCH /api/admin/members/{memberId}/riding-permissions
```

마장마술 승인, 장애물 승인, 대마장 허용 여부 수정.

```text
PATCH /api/admin/members/{memberId}/ride-counts
```

일반 기승 횟수 또는 특수 클래스 이력 보정.

### 관리자 쿠폰 API

```text
GET /api/admin/members/{memberId}/coupons
```

회원 쿠폰 목록 조회.

```text
POST /api/admin/members/{memberId}/coupons
```

신규 쿠폰 등록.

요청:

```text
type: general | dressage | jumping
total_count
first_used_at nullable
expires_at nullable
memo nullable
```

```text
PATCH /api/admin/coupons/{couponId}
```

쿠폰 정보 수정.

```text
POST /api/admin/coupons/{couponId}/expire
```

쿠폰 만료 처리.

```text
GET /api/admin/coupon-usage-logs
```

쿠폰 사용 로그 조회.

쿼리:

```text
memberId
couponId
reservationId
action
dateFrom
dateTo
```

### 시스템 작업 API

```text
POST /api/jobs/expire-pending-payments
```

2시간이 지난 입금대기 예약을 `payment_expired`로 변경하고 정원을 반환하는 작업.

```text
POST /api/jobs/expire-coupons
```

만료일이 지난 쿠폰을 `expired`로 변경하고 잔여 횟수를 소멸 처리하는 작업.
