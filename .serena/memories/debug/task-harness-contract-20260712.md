# Task harness diagnosis and fix

## Symptom
MVP task docs had measurable outcomes but no executable verification commands, per-task exclusions, stop conditions, or loop-size ceiling. Rejection and cancellation also shared a terminal state.

## Root cause
The docs were human-readable roadmap cards rather than executable AI task contracts. No bootstrap task existed to create a task runner and verification command namespace. The lack of source code explains why concrete test class names do not exist yet, but it does not prevent defining command contracts.

## Fix
- Locked stack: Java/Spring Boot/Spring Data JPA/MySQL/Flyway/Gradle; React/TypeScript/Vite web; React Native/TypeScript/Expo mobile; OpenAPI-generated client; pnpm workspace; mise root runner.
- Added `docs/tasks/TEMPLATE.md` with allowed scope, excluded scope, acceptance criteria, exact verification commands/evidence, stop conditions, and blocked/done formats.
- Added `docs/tasks/verification-contract.md` and M0-00 harness bootstrap.
- Split roadmap into 67 atomic backlog items; 66 use `mise run verify:<task-id>`, with M0-00 as the documented bootstrap exception.
- Added `rejected` distinct from `cancelled` across product, architecture, API, model, state machine, and tasks.
- Marked TMP.md as historical and docs as SSOT.

## Verification
Static scans found 67 task rows and 66 task-specific verifier targets, matching the one M0-00 exception. No empty docs were found. `mise` and `mise.toml` are currently absent, so executable harness validation remains intentionally blocked until M0-00 is implemented.