# Backend Conventions

이 디렉터리는 Java/Spring 백엔드 구현 규칙의 SSOT다. `backend/AGENTS.md`는 이 문서를 작업에 연결하고, Gradle과 mise 검증은 자동화 가능한 규칙을 강제한다.

## 공통 입력

모든 백엔드 작업은 다음 문서를 읽는다.

- [Architecture](architecture.md)
- [Package](package.md)
- [Naming](naming.md)
- [Coding Style](coding-style.md)
- [Testing](testing.md)
- [AI Self Review](review.md)

## 작업별 입력

| 작업 | 추가 문서 |
|---|---|
| Entity와 도메인 정책 | [DDD](ddd.md), [Exception](exception.md) |
| Controller와 API | [DTO](dto.md), [Exception](exception.md), [Security](security.md) |
| JPA와 Repository | [Database](database.md), [DDD](ddd.md) |
| 인증과 권한 | [Security](security.md), [Exception](exception.md) |

## 강제 수준

- `Required`: 자동 검사 또는 필수 테스트로 차단한다.
- `Review`: 문맥 판단이 필요하며 자체 검토와 코드 리뷰에서 확인한다.
- 자동화가 불완전한 규칙도 선택 사항이 아니다. 위반이 필요하면 task에 근거와 예외 범위를 기록한다.

## 검증

```bash
mise run backend:convention
```
