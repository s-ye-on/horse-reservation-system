# MVP 3.2 Backlog: 가족 쿠폰과 회원 클래스

| ID | 단일 작업 | 의존성 | 완료 신호 | 제외 범위 | 검증 명령 |
|---|---|---|---|---|---|
| M32-00 | 가족·클래스 SSOT와 ADR 계약 | M31-17 | 소유권·후보·baseline·hold·이관 미지원 계약 검증 성공 | 구현 코드 | `mise run verify:m32-00` |
| M32-01 | 가족 그룹과 membership 스키마 | M32-00 | 단일 활성 그룹 DB 제약과 감사 원장 성공 | 관리자 API | `mise run verify:m32-01` |
| M32-02 | 관리자 가족 그룹 Command API | M32-01 | 생성·추가·제거·해제와 필수 사유 감사 성공 | 회원 직접 관리 | `mise run verify:m32-02` |
| M32-03 | 가족 쿠폰 후보와 사용 이력 | M32-02 | NULL 만료 마지막·결정 정렬·선택 Coupon 1행 잠금 성공 | Coupon 물리 병합 | `mise run verify:m32-03` |
| M32-04 | 가족 쿠폰 교차 동시성 | M32-03 | 동시 예약·제거·반환·만료 경쟁에서 수치와 이력 일치 | 부하 시험 | `mise run verify:m32-04` |
| M32-05 | 관리자 가족 그룹 웹 | M32-02 | 검색·구성원 변경·감사·320px UX 성공 | 모바일 관리자 화면 | `mise run verify:m32-05` |
| M32-06 | 일반 클래스 catalog·baseline·promotion hold | M32-00 | 전체 클래스 경계 전파·progression·특수 승인 상호 배타·effective와 감사 성공 | 기승 횟수 보정 | `mise run verify:m32-06` |
| M32-07 | Horse 기승 횟수 집계 오류 조정 원장 | M32-06 | Horse 관리 시작 이후 delta·전후 값·관리자·사유와 동시 보정 성공 | 시스템 도입 이전 이력 복원 | `mise run verify:m32-07` |
| M32-08 | 관리자 baseline·hold·횟수 보정 웹 | M32-06, M32-07 | 변경 전후 예상 class·상호 배타 안내·설정·해제·보정·감사 UX 성공 | 회원 수정 기능 | `mise run verify:m32-08` |
| M32-09 | Phase B OpenAPI와 생성 Client | M32-04, M32-08 | 가족·클래스·쿠폰 계약과 웹 타입 동기화 성공 | 모바일 화면 | `mise run verify:m32-09` |
| M32-10 | Phase B 전체 품질 Gate | M32-05, M32-09 | 가족 교차 경쟁·전체 회귀·OpenAPI diff 승인 | MVP-4 구현 | `mise run verify:m32-10` |

모든 항목은 [실행 작업 템플릿](TEMPLATE.md)의 중단 조건을 상속한다.

## M32-06 일반 클래스 전파 계약

구보초보와 구보 추가는 등급 계산 enum만 확장하는 작업이 아니다. M32-06은 명시적인 일반
클래스 catalog와 순서를 GeneralRidingGrade, 회원 예약 자격, 관리자 수업 구성, 정원 정책,
시간표·수업 생성, 관련 API, 관리자·회원 UI와 테스트에 일관되게 전파해야 완료된다.

Special Approval은 일반 progression과 별도다. 마장마술 또는 장애물 승인 하나라도 활성화된
회원은 promotion hold 대상이 아니며, 승인과 hold의 동시 활성 상태를 Command와 저장 불변식에서
거부한다. 기존 특수 승인은 일반 클래스 자격을 최소 대마장 속보까지 확장하고 해당 특수 클래스를
추가하지만, 그 자체로 구보초보·구보를 열지는 않는다.

## M32-07 보정 경계

M32-07은 회원별 Horse progression 관리 시작 이후 Horse가 관리한 완료 집계 오류만 정정한다.
기존 회원의 최초 baseline 승인 또는 신규 회원의 progression 초기화가 이 관리 시작 경계를
확정한다. M32-06은 경계를 클래스 progression 상태의 write-once 단일 원천으로 영속하고 최초
설정 감사를 남긴다. 시스템 도입 이전 경력은 시작 클래스 baseline으로 종결하며 과거
횟수·날짜·기간 복원, PRE_ANCHOR·POST_ANCHOR 입력과 Anchor 재기준화는 구현하지 않는다.
이후 baseline 교정·해제와 M32-07 보정은 관리 시작 경계를 다시 쓰지 않는다.
