# Domain Rules

## Invariants
- A commitment must belong to an account and a category.
- A transaction must reconcile to an account and a date.
- The consumer view must never allow edits to underlying financial data.
- Imported transactions must be reviewed before they become accepted ledger entries.
- Recurring planner commitments are stored as monthly definitions with `day_of_month`.
- One-off future planner entries are stored as dated transactions with `txn_type = ONE_OFF_FUTURE`.
- Snapshot calculations must use transaction instances rather than recurring definitions directly.

## Calculations
- Current balance is derived from account activity and commitments.
- Allowable spend is derived from planned income, committed outgoings, and available balance.
- Planned-versus-actual reconciliation is derived from imported transactions versus planned commitments.
- Current month snapshot totals are derived from `transactions` in the selected period window.
