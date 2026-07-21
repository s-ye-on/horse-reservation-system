# MVP 3.2 Backlog: 가족 쿠폰과 회원 클래스

| ID | 단일 작업 | 의존성 | 완료 신호 | 제외 범위 | 검증 명령 |
|---|---|---|---|---|---|
| M32-00 | 가족·클래스 SSOT와 ADR 계약 | M31-17 | 소유권·후보·override·이관 계약 검증 성공 | 구현 코드 | `mise run verify:m32-00` |
| M32-01 | 가족 그룹과 membership 스키마 | M32-00 | 단일 활성 그룹 DB 제약과 감사 원장 성공 | 관리자 API | `mise run verify:m32-01` |
| M32-02 | 관리자 가족 그룹 Command API | M32-01 | 생성·추가·제거·해제와 필수 사유 감사 성공 | 회원 직접 관리 | `mise run verify:m32-02` |
| M32-03 | 가족 쿠폰 후보와 사용 이력 | M32-02 | NULL 만료 마지막·결정 정렬·선택 Coupon 1행 잠금 성공 | Coupon 물리 병합 | `mise run verify:m32-03` |
| M32-04 | 가족 쿠폰 교차 동시성 | M32-03 | 동시 예약·제거·반환·만료 경쟁에서 수치와 이력 일치 | 부하 시험 | `mise run verify:m32-04` |
| M32-05 | 관리자 가족 그룹 웹 | M32-02 | 검색·구성원 변경·감사·320px UX 성공 | 모바일 관리자 화면 | `mise run verify:m32-05` |
| M32-06 | 일반 클래스 수동 override | M32-00 | calculated·manual·effective와 변경 감사 성공 | 기승 횟수 보정 | `mise run verify:m32-06` |
| M32-07 | 기승 횟수 조정 원장 | M32-06 | delta·전후 값·관리자·사유와 동시 보정 성공 | 과거 수업 이력 생성 | `mise run verify:m32-07` |
| M32-08 | 관리자 클래스·횟수 보정 웹 | M32-06, M32-07 | 지정·해제·보정·감사 UX 성공 | 회원 수정 기능 | `mise run verify:m32-08` |
| M32-09 | Phase B OpenAPI와 생성 Client | M32-04, M32-08 | 가족·클래스·쿠폰 계약과 웹 타입 동기화 성공 | 모바일 화면 | `mise run verify:m32-09` |
| M32-10 | Phase B 전체 품질 Gate | M32-05, M32-09 | 가족 교차 경쟁·전체 회귀·OpenAPI diff 승인 | MVP-4 구현 | `mise run verify:m32-10` |

모든 항목은 [실행 작업 템플릿](TEMPLATE.md)의 중단 조건을 상속한다.
