# Planned Tasks

아직 실행하지 않는 상세 task 계약을 보관한다. 의존성이 끝난 task 하나만 `../active/`로 이동하며, 이동 전 대응하는 `mise run verify:<task-id>`가 등록되어 있어야 한다.

`planned/` 문서는 구현 입력이 아니라 다음 active task를 선택하기 위한 준비된 계약이다. 실행 중 발견한 정책 충돌은 계획 문서에 추측으로 반영하지 않고 현재 task의 중단 조건으로 보고한다.
