# 예약 상태 전이

## 상태

| 상태 | 의미 | 정원 점유 |
|---|---|---|
| `pending_admin_approval` | 쿠폰 예약 관리자 승인대기 | 예 |
| `pending_payment` | 쿠폰 없는 1회 결제 입금대기 | 예 |
| `payment_expired` | 입금대기 2시간 만료 | 아니요 |
| `confirmed` | 관리자 확정 | 예 |
| `completed` | 수업 완료 | 아니요 |
| `rejected` | 관리자가 예약 신청을 승인하지 않음 | 아니요 |
| `cancelled` | 접수된 예약을 회원 또는 관리자가 이후 취소 | 아니요 |
| `no_show` | 노쇼 | 아니요 |

## 쿠폰 예약

```text
예약 신청
  -> 정원 점유
  -> 쿠폰 1회 임시 점유
  -> pending_admin_approval
  -> 관리자 승인
  -> confirmed
  -> 수업 완료
  -> completed + 쿠폰 실제 차감
```

반려하면 `rejected`, 취소하면 `cancelled`로 전이한다. 반환 대상이면 정원과 쿠폰 임시 점유를 함께 해제한다.

## 1회 결제 예약

```text
예약 신청
  -> 정원 점유
  -> pending_payment
  -> 2시간 내 입금 확인
  -> confirmed
  -> 수업 완료
  -> completed
```

2시간이 지나면 `payment_expired`가 되고 정원을 반환한다. 관리자가 복구할 때는 정원을 다시 확보한 뒤 `confirmed`로 전이한다.

## 관리자 승인대기 경고

경고는 예약 상태가 아니다. `approval_requested_at`, 현재 시각, 수업 시작 시각으로 계산하는 조회 값이다.

```text
normal     신청 후 2시간 이내
warning    신청 후 2시간 초과
critical   신청 후 24시간 초과 또는 수업 시작 24시간 이내
```

## 상태 전이 원칙

- 허용된 이전 상태에서만 전이한다.
- 같은 완료·취소 요청이 반복되어도 부수 효과는 한 번만 발생한다.
- 상태 전이와 정원 및 쿠폰 변경은 같은 트랜잭션에서 처리한다.
- 관리자 예외 처리에는 행위자와 사유를 기록한다.
- `rejected`에는 관리자와 반려 사유를, `cancelled`에는 취소 행위자·책임·사유를 기록한다.
