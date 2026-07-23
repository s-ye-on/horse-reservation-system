# ADR-014: 동일 회원 활성 예약의 DB 불변식

## 상태

ADR-016으로 부분 대체

## 맥락

하나의 회원 계정은 한 명의 실제 기승자를 나타내므로 동일 회원은 같은 고정 TimeSlot에
활성 예약을 두 개 이상 가질 수 없다. Application 조회만으로는 직접 SQL과 동시 요청을
막을 수 없고, 모든 상태 이력을 UNIQUE 대상으로 삼으면 정상적인 취소 후 재예약도 막힌다.

## 결정

- 중복 키는 `member_id`, `lesson_date`, `start_time`이다.
- 활성 상태는 `pending_admin_approval`, `pending_payment`, `confirmed`다.
- 활성 상태면 `1`, 종료 상태면 `NULL`인 MySQL generated marker를 `reservations`에 둔다.
- marker를 포함한 UNIQUE key로 활성 행 하나만 허용하고 종료 이력의 중복은 보존한다.
- 생성·변경·입금복구는 이미 잠근 TimeSlot과 활성 Reservation 목록을 이용해 저장 전 같은
  도메인 정책을 검사한다.
- ADR-016 적용 전 정상 API는 `RESERVATION_DUPLICATE_ACTIVE_TIME_SLOT`을 반환한다.
  보정 후에는 V15를 최종 방어로 유지하되 외부 오류를
  `RESERVATION_OVERLAPPING_ACTIVE_RESERVATION`으로 대체한다.
- migration 전 preflight는 중복 그룹과 예약 ID를 출력한다. 중복이 있으면 자동 정리하지
  않고 migration을 중단한다.

## 검토한 대안

### Application 조회만 사용

현재 TimeSlot 잠금 경로에서는 직렬화되지만 다른 쓰기 경로나 직접 SQL을 방어하지 못한다.

### 모든 예약 상태에 일반 UNIQUE 적용

취소·반려·만료 후 같은 회원이 다시 예약할 수 없고 과거 이력을 삭제해야 하므로 제품
정책과 충돌한다.

### 별도 활성 예약 테이블

현재 Reservation이 점유 SSOT이므로 상태 전이와 별도 테이블을 동기화하는 중복 모델이 된다.

## 결과

고정 슬롯 모델에서는 DB가 활성 중복을 원자적으로 차단한다. 수업 duration이나 겹치는
구간이 도입되면 이 UNIQUE를 확장하지 않고 별도 구간 중복 ADR과 migration을 작성한다.

ADR-016은 45분 수업과 서로 다른 시작 시각의 구간 중복을 도입한다. V15 exact-start
UNIQUE는 부분집합 최종 방어로 유지하지만 외부 오류는
`RESERVATION_OVERLAPPING_ACTIVE_RESERVATION`으로 통합한다.
