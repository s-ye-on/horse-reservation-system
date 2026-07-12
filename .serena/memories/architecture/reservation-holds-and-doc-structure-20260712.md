# Architecture decisions

- Durable docs are split by responsibility: `docs/product` for requirements/policies/MVP scope, `docs/architecture` for system contracts/ADRs, and `docs/tasks` for implementation work after architecture handoff.
- Coupon reservations create a non-deducting temporary coupon hold at request time. The hold becomes confirmed on admin approval and actual deduction occurs on lesson completion. Reject/cancel-return releases the hold.
- Coupon first ride date is the lesson date of the first completed coupon reservation; expiration is three months from that date.
- Coupon admin approval does not expire and continues occupying capacity.
- Approval alerts: warning after 2 hours; critical after 24 hours or when lesson starts within 24 hours. Alerts do not change state or capacity and thresholds should be configurable.
- Expired pending-payment reservations can be restored only if capacity can be reacquired atomically.
- `TMP.md` remains as the original discussion source and is not deleted.