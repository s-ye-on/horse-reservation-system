# ADR-018: Coupon 소유권 유지형 가족 공유와 snapshot 감사

## 상태

`ACCEPTED`

## 배경

가족은 서로의 유효 Coupon을 사용할 수 있어야 하지만 Coupon을 가족 잔액으로 합치면 원소유자,
기존 Reservation hold와 반환 대상을 잃는다. 현재 Reservation은 선택된 Coupon을 끝까지 추적하므로
가족 관계가 바뀌더라도 이미 성립한 예약의 Coupon 관계는 안정적으로 유지되어야 한다.

가족 운영은 ADMIN 책임이며 회원 대표자나 원자적 가족 이관은 MVP 3.2에 필요하지 않다.

## 검토한 대안

### A. 가족 합산 Coupon

Coupon을 FamilyGroup 소유 잔액으로 병합한다. 조회는 단순하지만 기존 소유권과 사용 이력을
재작성해야 하고 hold·반환의 원래 대상을 잃으므로 선택하지 않는다.

### B. 소유권 유지와 후보 범위 확장

개별 Coupon과 원소유자를 유지하고 활성 membership이 신규 예약의 후보 범위만 확장한다.
예약 시점의 관계를 snapshot으로 남길 수 있고 기존 hold를 안정적으로 유지하므로 선택한다.

### C. 현재 membership 동적 조회만으로 과거 이력 표현

별도 snapshot 없이 현재 가족 관계로 과거 사용을 해석한다. 구성원 제거 후 과거 사용의 근거가
달라 보이므로 선택하지 않는다.

## 결정

### FamilyGroup과 membership

- FamilyGroup은 안정적인 Group ID가 identity다.
- 관리자 구분용 그룹 이름은 필수이며 중복을 허용한다.
- 대표자, 소유 회원, 대표자 승계와 MEMBER 가족 관리 권한은 두지 않는다.
- ADMIN만 그룹 생성, 구성원 추가·제거와 그룹 해제를 수행한다.
- 회원은 동시에 하나의 활성 FamilyGroup에만 속할 수 있다. 어느 그룹에도 속하지 않은 상태는 허용한다.
- 빈 ACTIVE 그룹을 허용한다. 마지막 구성원 제거는 그룹을 해제하지 않는다.
- 구성원 제거는 membership만 종료한다.
- 그룹 해제는 비가역적이며 FamilyGroup을 종료하고 모든 active membership을 종료한다.
- 그룹과 membership의 과거 상태는 삭제하거나 다시 쓰지 않는다.

MVP 3.2는 전용 가족 이관 Command를 제공하지 않는다. 가족 변경은 기존 membership 제거 후
새 membership 추가를 각각 독립된 관리자 Command로 수행한다. 두 Command 사이에 가족이 없는
상태가 존재할 수 있고, 두 그룹을 함께 잠그는 원자적 이관과 transfer 전용 감사 action은 만들지 않는다.

### Coupon 후보와 선택

Coupon row와 원소유자는 바꾸지 않는다. 활성 가족 구성원은 같은 활성 그룹의 구성원이 소유한
Coupon 가운데 다음 조건을 모두 만족하는 행을 후보로 사용한다.

- 예약 클래스와 Coupon 종류가 일치한다.
- Coupon이 active 상태다.
- `remainingCount > heldCount`다.
- 수업일 기준으로 유효하다.
- 그룹 가입 전 발급 Coupon도 위 조건을 만족하면 포함한다.

후보는 다음 순서로 결정한다.

```sql
ORDER BY (expires_at IS NULL) ASC,
         expires_at ASC,
         created_at ASC,
         id ASC
```

만료일이 있는 Coupon, 더 빨리 만료되는 Coupon, 먼저 생성된 Coupon, 작은 Coupon ID 순이다.
MySQL의 기본 NULL 정렬에 의존하지 않으며 무기한 Coupon은 마지막이다. FamilyGroup과 Coupon의
실제 잠금 쿼리·인덱스·트랜잭션은 M32-03과 M32-04에서 확정한다.

신규 가족 Coupon 예약은 기존 예약 잠금 문맥을 획득한 뒤, 예약 회원의 active FamilyGroup을
현재 읽기로 확인하고 해당 FamilyGroup 행을 `FOR UPDATE`로 잠근 다음 결정된 Coupon 한 행을
잠근다. M32-02의 구성원 추가·제거와 그룹 해제도 같은 FamilyGroup 행을 가장 먼저 잠그므로
가족 후보 판정과 membership 변경은 한쪽이 먼저 완료되는 순서로 직렬화된다. 잠금 대기 뒤
그룹이 이미 해제되었거나 membership이 종료되었다면 해당 가족 Coupon은 신규 후보가 아니다.
예약 트랜잭션이 가족 없음 상태를 먼저 관찰한 뒤 `addMember`와 겹친 경우 해당 예약은 가입 전
권리로 진행하며, 새 membership은 다음 예약부터 후보 범위를 확장한다. 이미 진행 중인 예약의
Coupon 선택을 가입 완료 뒤 소급해서 다시 시작하지 않는다.

Coupon 후보 ID 계산은 정렬된 `LIMIT 1` derived 결과로 분리하고, 외부 locking read가 그 PK의
현재 상태를 다시 확인해 한 Coupon 행만 잠근다. 후보 탐색 중 접근할 수 없는 선행 Coupon이나
정렬상 후순위 Coupon에는 행 잠금을 남기지 않는다.

기존 hold의 반환·확정·사용과 Coupon 만료는 현재 membership을 다시 잠그지 않고 Reservation에
snapshot된 원 Coupon 행을 잠근다. 따라서 membership 변경은 과거 권리를 다시 판정하지 않으며,
반환과 만료가 겹쳐도 Coupon 행 잠금으로 `remainingCount`와 `heldCount` 전이를 직렬화한다.

### 기존 Reservation과 역사적 snapshot

membership 제거와 그룹 해제는 이후 신규 예약의 후보 권한만 바꾼다.

- 기존 Reservation은 유지한다.
- 기존 hold와 선택 Coupon은 유지하며 다른 Coupon으로 교체하지 않는다.
- 취소·반환·완료는 Reservation에 원래 연결된 Coupon을 사용한다.
- Coupon 원소유자는 바뀌지 않는다.
- 과거 이력은 당시의 예약 회원, Coupon 원소유자, 실제 Coupon, FamilyGroup, Reservation,
  행위, 시각과 actor를 구분해 snapshot으로 보존한다.

따라서 현재 membership은 현재 사용 권리의 근거이고 snapshot은 과거 행위의 근거다.
구체적인 Entity와 column은 M32-03의 책임이다.

### 감사

그룹 생성, 구성원 추가·제거와 그룹 해제는 관리자, 시각, 전후 상태와 필수 사유를
append-only 감사 이력으로 남긴다. 현재 상태 변경이 과거 membership·Coupon 사용 이력과
감사를 소급 수정하지 않는다.

## 불변식

- 회원별 active membership은 최대 하나다.
- ACTIVE FamilyGroup의 구성원 수는 0일 수 있다.
- Coupon의 물리적 소유자는 가족 변경으로 바뀌지 않는다.
- Reservation에 hold된 Coupon은 가족 변경으로 교체되지 않는다.
- 과거 가족 Coupon 사용은 현재 membership이 아니라 사용 시점 snapshot으로 해석한다.
- 후보 정렬의 마지막 tie-breaker는 Coupon ID다.

## 결과와 후속

- M32-01은 FamilyGroup, 단일 active membership과 append-only 감사의 DB 불변식을 구현한다.
- M32-02는 ADMIN 생성·추가·제거·해제 Command를 구현하며 transfer API는 만들지 않는다.
- M32-03은 후보 선택, 한 Coupon 잠금과 사용 snapshot을 구현한다.
- M32-04는 예약·membership 변경·반환·만료 경쟁을 검증한다.
- M32-05는 빈 그룹과 명시적 해제를 포함한 관리자 UX를 구현한다.
