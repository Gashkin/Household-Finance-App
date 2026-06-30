# Domain Rules

## Invariants
- A commitment must belong to an account and a category.
- A transaction must reconcile to an account and a date.
- The consumer view must never allow edits to underlying financial data.
- Imported transactions must be reviewed before they become accepted ledger entries.

## Calculations
- Current balance is derived from account activity and commitments.
- Allowable spend is derived from planned income, committed outgoings, and available balance.
- Planned-versus-actual reconciliation is derived from imported transactions versus planned commitments.
