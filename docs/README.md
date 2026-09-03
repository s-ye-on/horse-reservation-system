# 프로젝트 문서

이 디렉터리는 마장 예약 서비스의 지속 관리 문서다. `TMP.md`는 초기 논의 원본으로 유지하고, 확정된 내용은 아래 문서를 기준으로 구현한다.

## 문서 책임

- `product/`: 제품 목표, 사용자 기능, 운영 정책, MVP 범위
- `architecture/`: 시스템 경계, 상태 전이, 데이터 모델, API 계약, 기술 결정
- `tasks/`: 아키텍처가 확정된 뒤 작성하는 MVP별 구현 작업과 완료 조건

같은 내용이 충돌하면 제품 정책은 `product/policies.md`, 기술 계약은 `architecture/`, 구현 순서는 `tasks/`를 우선한다.

## 시작 지점

- [제품 요구사항](product/requirements.md)
- [운영 정책](product/policies.md)
- [MVP 1](product/mvp-1.md)
- [MVP 2](product/mvp-2.md)
- [MVP 3](product/mvp-3.md)
- [MVP 3.1 운영 안정화](product/mvp-3.1.md)
- [MVP 3.2 가족 쿠폰과 회원 클래스](product/mvp-3.2.md)
- [MVP 3.3 관리자 운영 가시성](product/mvp-3.3.md)
- [MVP 4 모바일 앱](product/mvp-4.md)
- [시스템 개요](architecture/system-overview.md)
- [MVP-3.1 안정화 계획](architecture/mvp-3-1-stabilization-plan.md)
- [예약 상태 전이](architecture/reservation-state-machine.md)
- [데이터 모델](architecture/domain-model.md)
- [API 초안](architecture/api.md)
- [기술 스택 ADR](architecture/adr-002-technology-stack.md)
- [관리자 운영 조회 ADR](architecture/adr-023-admin-operational-visibility-read-models.md)
- [구현 작업 보드](tasks/README.md)
- [실행 작업 템플릿](tasks/TEMPLATE.md)
- [검증 명령 계약](tasks/verification-contract.md)
