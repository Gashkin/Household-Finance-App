# Decisions

- The consumer experience is a monthly overview for read-only reporting.
- Transaction intake uses file import with review rather than direct bank sync.
- The implementation is browser-based and locally hosted.
- Planner and consumer roles are separate.
- Commitments persistence is split into `monthly_commitments` (recurring definitions) and `transactions` (dated instances).
- Transaction instances are typed as `ACTUAL`, `MONTHLY_INSTANCE_FUTURE`, or `ONE_OFF_FUTURE`.
- Planner API contract remains commitment-oriented during migration to avoid frontend breakage.
