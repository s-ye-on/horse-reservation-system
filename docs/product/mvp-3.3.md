# MVP 3.3: 관리자 운영 가시성

## 목표

Horse가 이미 보존하는 예약 완료와 materialized TimeSlot을 관리자 운영 지표와 주간 시간표로
조회할 수 있게 한다. 새로운 예약·출석·progression 정책을 만들지 않고 기존 사실 데이터를 읽는다.

## 포함 범위

- 선택 월과 기승 종류별 `COMPLETED` Reservation 총 기승 횟수
- 선택 월과 기승 종류별 공동 최다 기승 회원과 각 완료 횟수
- 현재 월 기본 조회, 이전·다음 월 이동과 현재 월 복귀
- materialized TimeSlot을 기준으로 한 월요일부터 일요일까지의 관리자 주간 운영 캘린더
- 이전·다음 주 이동과 현재 주 복귀
- 예약이 없는 TimeSlot, 같은 TimeSlot의 여러 회원, RidingClass와 현재 Reservation 상태 표시
- 전용 Backend read model, OpenAPI/generated client와 관리자 Web 동기화

## 제외 범위

- 월별 mutable 통계 counter, snapshot table와 별도 analytics subsystem
- baseline, progression, 특수 승인 인정분, promotion hold 또는 회원 누적 횟수의 월간 통계 사용
- M32-07 기승 횟수 보정의 월 귀속, 실제 수업일 입력 또는 schema·Command·관리자 UX 변경
- Horse 운영 이전 경력과 예약으로 보존되지 않은 기승 이력 복원
- 매출, 강사·말·마장 배정, 자원 가동률과 범용 관리자 Dashboard
- 새로운 예약 상태, TimeSlot 생성·마감 또는 출석 상태 전이
- 모바일 관리자 기능과 MVP 4 회원 앱 구현

## 완료 조건

- 월간 통계가 `lessonDate`가 선택 월이고 상태가 `COMPLETED`인 Reservation만 집계한다.
- `ALL`, `GENERAL`, `DRESSAGE`, `JUMPING` 선택에 따라 총 횟수와 공동 최다 회원이 함께 바뀐다.
- 주간 캘린더가 선택한 월요일~일요일의 실제 TimeSlot을 예약 0건인 경우까지 표시한다.
- 주간 조회가 TimeSlot별 반복 HTTP 요청이나 회원·예약 N+1 없이 회원, RidingClass와 상태를 제공한다.
- Backend runtime, OpenAPI, generated client와 관리자 Web 계약이 일치하고 전용 Gate가 성공한다.

## 선행 Gate

- MVP 3.2 Phase B 전체 Gate인 `M32-10`이 완료되어야 한다.
- MVP 4는 MVP 3.3 최종 Gate 이후 시작한다.
