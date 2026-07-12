# MVP roadmap

- MVP 0: stack/runtime contract, auth/RBAC, DB schema/migrations, OpenAPI contract, monorepo quality gates.
- MVP 1: end-to-end operational reservation flow including class eligibility, capacities, coupon registration and temporary holds, admin approval, 2-hour payment expiry and capacity-checked restore, completion/no-show, member/admin UIs, concurrency and E2E tests.
- MVP 2: change/cancel policy engine, admin coupon override and mandatory memo, member/admin change/cancel UIs, boundary and concurrency regression tests.
- MVP 3: dashboard/query APIs and UI, bulk completion/coupon expiry jobs, audit/export APIs and UI, monitoring and full regression.
- Each task has one owner, priority, dependencies, scope, complexity, and measurable acceptance criteria under `docs/tasks/`.
- Open decisions are listed in `docs/tasks/README.md`, including stack/auth/deployment and edge cases for coupons that have no first-use expiration yet.