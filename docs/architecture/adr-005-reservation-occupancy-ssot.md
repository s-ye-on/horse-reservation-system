# ADR 005: Reservation 기반 정원 점유 SSOT

## 상태

`ACCEPTED`

## 배경

M1 예약 신청은 동시에 처리되더라도 시간대의 전체·원형·클래스별 정원을 초과하지 않아야 한다. 정원 점유를 별도 모델로 저장할지, 기존 Reservation 상태에서 계산할지 결정해야 한다.

현재 모듈 경계에서 `timeslots`는 시간대와 정원 설정을 소유하고 `reservations`는 예약 상태 전이와 정원 점유를 소유한다. 승인대기와 입금대기도 모두 Reservation으로 생성된다.

## 결정

M1에서는 Reservation을 정원 점유의 Single Source of Truth로 사용한다.

다음 상태만 활성 점유로 계산한다.

- `pending_admin_approval`
- `pending_payment`
- `confirmed`

예약 생성과 점유 상태 진입은 하나의 트랜잭션에서 다음 순서를 지킨다.

1. 대상 `TimeSlotCapacity` 행을 `PESSIMISTIC_WRITE`로 잠근다.
2. 마감 여부와 정원 설정을 확인한다.
3. 활성 Reservation을 잠금 조회하여 전체·원형·클래스별 점유를 계산한다.
4. 정원 제한을 검증한다.
5. Reservation을 활성 점유 상태로 생성하거나 전이한다.

Reservation이 비점유 상태로 전이되면 별도 점유 수를 감소시키지 않는다. 이후 활성 Reservation 집계에서 자연스럽게 제외한다. 모든 점유 진입 경로는 TimeSlot 우선 잠금 순서를 동일하게 적용한다.

별도의 `TimeSlotOccupancy`, Occupancy Ledger 또는 점유 카운터는 M1에 도입하지 않는다.

## 검토한 대안

### Reservation 상태 기반 집계

- 기존 상태 모델을 재사용하고 중복 데이터를 만들지 않는다.
- 상태 전이와 점유 여부가 항상 같은 사실에서 계산된다.
- 활성 예약 조회 비용과 모든 쓰기 경로의 잠금 규약 준수가 필요하다.

### 별도 Occupancy Ledger

- Reservation 생성 전 홀드, 다중 좌석, 저장소 분리, 별도 감사 또는 Projection에 유리하다.
- 현재 요구에는 중복 모델이며 Reservation 상태와 동기화 실패 가능성과 운영 복잡도를 만든다.

## 결과

- M1-05는 Reservation 스키마를 선행 작업으로 요구한다.
- `reservations` 영속성 조회와 `timeslots` 비관적 잠금을 M1-05 허용 범위에 포함한다.
- 점유 해제는 카운터 감소가 아니라 Reservation의 비점유 상태 전이로 표현한다.
- 활성 Reservation 조회를 위한 날짜·시각·상태·클래스 인덱스를 예약 스키마에 둔다.
- Reservation 생성 전 홀드, 다중 좌석, 저장소 분리, 별도 점유 감사 또는 성능 Projection 요구가 실제로 발생할 때 Ledger를 재검토한다.

## 검증

- 동일 시간대 병렬 예약에서 허용된 수만 성공하는 MySQL 통합 테스트를 작성한다.
- 마감, 전체 8명, 원형 4명, 클래스별 정원 경계를 각각 검증한다.
- `payment_expired`, `rejected`, `cancelled` 등 비점유 상태가 정원 집계에서 제외되는지 검증한다.
- 점유 진입 유스케이스가 TimeSlot 우선 잠금 순서를 사용하는지 통합 테스트로 확인한다.
