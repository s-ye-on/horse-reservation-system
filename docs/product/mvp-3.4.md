# MVP 3.4: 정규 시간표 lifecycle 정합성

## 목표

관리자가 정규 시간표를 사용자-facing `삭제`로 운영 종료하고 같은 요일·시각에 다시 운영할 수 있게
한다. 과거 운영 사실과 개별 TimeSlot 정원 보정을 보존하면서 미래 Template occurrence는 현재
Template 설정을 따르게 한다.

## 포함 범위

- `active=false`를 사용하는 정규 시간표 삭제와 신규 예약 유입 차단
- 미래 점유 Reservation의 `예약 정리 필요` 목록과 기존 관리자 취소 흐름 연결
- 동일 요일·시각 inactive Template의 내부 재사용과 재운영
- slot-level 정원 provenance와 미래 TEMPLATE occurrence 정원 동기화
- 점유 하한 충돌 시 Template 변경 전체 거부
- 설정 동기화 중 회원 예약 가능 조회의 fail-closed 보완
- 미래 점유 Reservation impact query 정합성 보완
- Backend, OpenAPI/generated client와 관리자 Web 동기화

## 제외 범위

- Template, TimeSlot, Reservation과 감사 이력의 Hard Delete
- 별도 `DELETED`, `ARCHIVED`, `PAUSED` lifecycle 상태
- 기존 Reservation 자동 취소와 새 취소 subsystem
- Template 운영 시작일·종료일, 계절 운영 기간과 yearly recurrence
- 필드별 capacity override와 범용 schedule override framework
- 과거 occurrence 또는 Reservation 상태 재작성

## 완료 조건

- 삭제 직후 미래 Template TimeSlot과 새 occurrence의 신규 예약 유입이 차단되고 기존 Reservation은
  그대로 유지된다.
- 미래 점유 Reservation을 정확히 확인해 기존 관리자 취소 흐름으로 이동할 수 있으며 0건인 inactive
  Template은 일반 운영 화면에서 숨겨진다.
- 같은 요일·시각 생성은 inactive Template을 현재 설정으로 재사용하고 기존 Reservation 상태를
  변경하지 않는다.
- Template 수정·재운영은 미래 비 override TEMPLATE occurrence에 같은 설정 version으로 완결되며
  점유 하한 충돌 또는 독립 마감 원인 훼손이 없다.
- Backend runtime, OpenAPI/generated client, 관리자 Web과 전용 Gate가 같은 계약을 검증한다.

상세 제품 정책은 [운영 정책](policies.md)의 `정규 시간표와 운영일`, 아키텍처 결정은
[ADR-024](../architecture/adr-024-regular-schedule-template-retirement-and-future-capacity-sync.md)를 따른다.
