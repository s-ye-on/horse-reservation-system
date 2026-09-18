# MVP 3.4 Backlog: 정규 시간표 lifecycle 정합성

| ID | 단일 작업 | 의존성 | 완료 신호 | 제외 범위 | 검증 명령 |
|---|---|---|---|---|---|
| M34-00 | 정규 시간표 삭제·재운영 SSOT와 ADR | M33-06 | 운영 종료, 예약 정리, 동일 key 재사용과 미래 정원 동기화 계약 확정 | production 코드·migration·Web | `mise run docs:check` |
| M34-01 | SYNCING 예약 가능 조회 fail-closed 보완 | M34-00 | 회원 예약 가능 TimeSlot 조회가 설정 동기화 중 기존 503 계약으로 차단됨 | Template lifecycle·Web | `mise run verify:m34-01` |
| M34-02 | Template별 미래 점유 Reservation read model | M34-00 | 날짜 범위·Template·`occupyingStatuses()`를 모두 적용한 건수·목록과 query 비용 검증 | 취소 Command·Web | `mise run verify:m34-02` |
| M34-02A | retired TEMPLATE TimeSlot 일반 조회 제외 | M34-02 | 회원 available-time과 관리자 일반 주간 캘린더에서 미래 retired occurrence를 제외하고 다른 마감 원인·과거 기록을 보존 | lifecycle API·Web·capacity provenance | `mise run verify:m34-02a` |
| M34-03 | TimeSlot 정원 provenance | M34-02A | `capacityOverridden` migration·보존 우선 backfill·개별 정원 변경·신규 occurrence 계약 검증 | Template 전파·Web | `mise run verify:m34-03` |
| M34-04 | 미래 Template occurrence 원자적 동기화 | M34-01, M34-02, M34-03 | 수정·재운영 정원 전파, override·마감 원인 보존과 점유 충돌 전체 거부 검증 | 삭제 API·Web | `mise run verify:m34-04` |
| M34-05 | 정규 시간표 삭제·동일 key 재운영 API | M34-02, M34-04 | inactive 운영 종료, 기존 예약 보존, 동일 key 재사용과 감사 기록 검증 | OpenAPI/client·Web | `mise run verify:m34-05` |
| M34-06 | lifecycle OpenAPI와 generated client | M34-05 | 삭제·재운영·예약 정리 계약의 runtime/OpenAPI/client 결정성 검증 | 관리자 Web | `mise run verify:m34-06` |
| M34-07 | 관리자 삭제·예약 정리·재운영 Web | M34-06 | 확인 후 삭제, 정리 대상 이동, 0건 숨김과 동일 시간 생성 UX의 반응형 E2E 성공 | 새 취소 subsystem·운영 종료 목록 | `mise run verify:m34-07` |
| M34-08 | MVP 3.4 전체 품질 Gate | M34-07 | Backend lifecycle·migration·OpenAPI diff·Web 회귀·핵심 E2E 승인 | MVP 4 구현 | `mise run verify:m34-08` |

모든 항목은 [실행 작업 템플릿](TEMPLATE.md)의 작업 크기 상한과 공통 중단 조건을 상속한다.
M34-01, M34-02와 M34-02A는 확정 정책을 구현하기 전에 기존 불일치를 제거하는 선행 fix다. M34-03은
기존 행을 자동 덮어쓰지 않고 provenance를 먼저 도입하며 M34-04가 정확한 미래 점유 read model과
provenance 위에서 수정·재운영에 같은 동기화 규칙을 적용한다. API, generated client와 Web은 그 뒤에
순서대로 진행한다.

## M34-00 계약

- 사용자-facing `삭제`는 Template `active=false`인 운영 종료이며 Hard Delete가 아니다.
- 삭제는 미래 TEMPLATE occurrence의 신규 유입을 차단하지만 기존 Reservation·쿠폰·결제 상태를
  자동 변경하지 않는다.
- `예약 정리 필요`는 해당 Template의 미래 `occupyingStatuses()` Reservation에서 파생하고 기존
  관리자 Reservation 취소 흐름으로 연결한다.
- 같은 `(요일, 시작 시각)` 생성은 inactive Template ID를 현재 입력 설정으로 갱신해 재사용한다.
- 수정·재운영은 아직 시작하지 않은 `capacityOverridden=false` TEMPLATE occurrence에 정원을
  전파한다. 점유 하한 충돌은 부분 적용 없이 전체 변경을 거부한다.
- 동기화는 `templateInactiveClosed`와 비 override 정원만 변경하며 다른 마감 원인, MANUAL occurrence,
  과거 TimeSlot과 모든 Reservation 상태를 보존한다.
- 기존 행은 provenance를 복원할 수 없으므로 보존 우선 backfill과 운영자 확인을 거친 명시적 Template
  기준 적용 절차를 사용한다.

상세 lifecycle, 원자성, rollout과 대안은
[ADR-024](../architecture/adr-024-regular-schedule-template-retirement-and-future-capacity-sync.md)를 따른다.
