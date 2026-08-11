# 검증 하네스 자원 격리

## 목적

OpenAPI 검증, Playwright E2E, 백엔드 검증과 로컬 데모가 동시에 실행돼도 서로의
포트·프로세스·DB·산출물을 재사용하거나 종료하지 않게 한다. 제품 코드와 API 계약은
변경하지 않는다.

## 변경 전 자원 지도

| 검증 | 포트 | 서버와 종료 책임 | 임시 디렉터리·출력 | Gradle build·cache | DB·Docker | 종료 시 정리 |
|---|---|---|---|---|---|---|
| `api:check` | Spring `8080` 고정 | OpenAPI Gradle plugin이 Spring을 시작·종료 | `mktemp`의 spec·client 비교본 | `backend/build`, `backend/.gradle`, 사용자 Gradle cache 공유 | `horse-mysql`의 기본 DB 공유 | 비교 임시 디렉터리만 삭제 |
| Playwright E2E | Spring `8080`, Vite `5173` 고정 | Playwright `webServer`가 두 프로세스를 시작·종료 | 기본 `test-results`, trace·screenshot | E2E가 먼저 공용 `backend/build`에 JAR 생성 | `horse-mysql`의 기본 DB와 고정 fixture 공유 | Playwright가 webServer 종료 |
| 백엔드 검증 | 애플리케이션 포트 없음, Testcontainers 임의 포트 | Gradle과 Testcontainers가 소유 | `backend/build`의 test result·report | `backend/build`, `backend/.gradle`, 사용자 Gradle cache 공유 | Testcontainers 실행별 컨테이너 | Gradle/Testcontainers가 정리 |
| 웹 build | 없음 | 단기 Node 프로세스 | `frontend/apps/web/dist`, `node_modules/.tmp/*.tsbuildinfo` | 해당 없음 | 없음 | 프로세스만 종료, 산출물 유지 |
| 로컬 demo | backend `8081`, proxy `8080`, web `5173` | `launchctl` label을 `demo:up/down`만 소유 | `/tmp/horse-local-demo`, `backend/build` JAR | 일반 `backend/build`, Gradle cache | `horse-mysql`의 기본 DB | `demo:down`만 소유 프로세스 종료 |

고정 포트와 공용 `backend/build`가 핵심 충돌점이다. 특히 데모 proxy가 `8080`을
점유하면 OpenAPI plugin 또는 E2E backend가 시작하지 못하고, 서로 다른 Gradle 검증이
같은 test result와 class 파일을 동시에 갱신한다. E2E fixture도 기본 DB를 공유하므로
동일 E2E의 병렬 실행은 데이터까지 충돌한다.

## 검토한 대안

### 고정 포트 잠금과 검증 직렬화

구현은 작지만 대기 시간이 길고 병렬 검증 요구를 충족하지 못한다. stale lock 복구와
로컬 프로세스 소유권 판별도 별도로 필요하다.

### 실행별 자원 격리

각 실행이 OS 할당 포트, 임시 build·결과 디렉터리와 임시 DB를 소유한다. 초기 구현은
더 크지만 기존 서버를 재사용하거나 종료할 필요가 없고 실패·중단 cleanup의 책임이
명확하다.

## 결정

실행별 자원 격리를 사용한다.

| 검증 | 격리 계약 | 공유하는 자원 | 소유자와 cleanup |
|---|---|---|---|
| OpenAPI 생성·검증 | 실행별 backend 포트, 임시 DB, build, project cache, spec·client 비교 디렉터리 | 사용자 Gradle dependency cache, `horse-mysql` 컨테이너 | `generate-api-client.sh`가 생성한 DB와 임시 디렉터리를 `EXIT`에서 삭제하고 Gradle plugin이 Spring을 종료 |
| Playwright E2E | 실행별 backend·web 포트, 임시 DB, backend build·project cache, Playwright output | 사용자 Gradle·pnpm·Playwright browser cache, `horse-mysql` 컨테이너 | `run-playwright.mjs`가 child process group, 임시 DB와 runtime 디렉터리를 소유·정리 |
| 백엔드 검증 | 실행별 Gradle build와 project cache | 사용자 Gradle dependency cache, Testcontainers image cache | `run-isolated-gradle.sh`가 종료 상태를 보존한 채 임시 디렉터리 삭제 |
| 웹 build + E2E | web build는 기존 정식 `dist`, E2E는 Vite dev와 실행별 Playwright output 사용 | 소스와 pnpm cache | 각 명령이 자신이 시작한 프로세스만 종료하며 서로의 출력 경로를 쓰지 않음 |
| 로컬 demo | 기존 고정 포트와 `launchctl` label 유지 | `horse-mysql`, 정식 backend JAR | 검증은 demo PID·label을 읽거나 종료하지 않고 `demo:down`만 종료 권한 보유 |

Gradle 사용자 전역 cache는 의존성 재사용을 위해 공유한다. 실행 중 변경되는 project cache와
build 디렉터리만 분리한다. 정식 OpenAPI와 TypeScript Client는 `api:generate`만 저장소 위치에
기록하며 `api:check`는 실행별 임시 결과와 비교한다.

격리된 E2E의 공식 진입점은 `pnpm --dir frontend/apps/web test:e2e -- <spec>`이다.
IDE나 직접 `playwright test` 실행은 기존 로컬 기본 포트와 DB를 사용하는 호환 경로이며 병렬
격리 계약에 포함하지 않는다. OpenAPI 문서의 canonical 서버 URL은 실제 검증 포트와 분리해
`http://localhost:8080`으로 고정하고, Gradle 직접 생성과 `api:generate`가 같은 계약을 만든다.

## 품질 시나리오

- 데모 또는 임의 서버가 `8080`을 사용해도 `api:check`와 E2E가 성공하고 기존 PID가 유지된다.
- OpenAPI와 E2E, 두 백엔드 검증, web build와 E2E가 병렬 실행돼도 각각의 결과가 정확하다.
- 같은 `api:check`를 동시에 실행해도 포트·DB·생성 결과가 충돌하지 않는다.
- 같은 E2E를 동시에 실행해도 각 실행의 포트·DB·runtime이 분리된다.
- 한 실행의 실패나 `SIGTERM`이 다른 실행을 종료하지 않으며 자신이 만든 child·DB·임시 파일만 정리한다.
- 자동 timeout은 종료 코드 `124`를 반환하고 소유한 child·DB·포트·runtime을 정리한다.
- 병렬 검증 전후 M31-16 범위 밖 Git 상태가 동일하고 OpenAPI 산출물에 diff가 생기지 않는다.

## 남은 경계

- `horse-mysql` 컨테이너와 사용자 Gradle dependency cache는 도구 자체의 잠금·동시성 계약을
  신뢰한다. 컨테이너 자체를 실행별로 복제하지 않고 DB schema만 분리한다.
- 정식 `api:generate`와 정식 `web:build`는 저장소 산출물을 만드는 명령이므로 동시에 여러 번
  실행하는 운영은 지원하지 않는다. 병렬 품질 검증은 임시 출력만 사용한다.
- OS 할당 포트는 빈 포트를 확인한 직후 Spring·Vite가 다시 바인드한다. 실행 사이의 매우 짧은
  선점 가능성은 남지만, strict bind 실패로 종료되어 다른 서버를 잘못 재사용하지 않는다.
