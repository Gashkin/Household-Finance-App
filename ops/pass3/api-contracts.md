# API Contracts

## Planned endpoints
- GET /accounts
- GET /commitments
- POST /commitments
- GET /transactions
- POST /imports
- GET /reports/monthly-overview

## Transition contract notes
- `/planner` and planner commitment CRUD endpoints remain stable while storage migrates.
- Recurring payloads map to `monthly_commitments`.
- Month-labeled one-off payloads map to `transactions` with `txn_type = ONE_OFF_FUTURE`.
- Future API versioning should separate recurring-definition endpoints from transaction-instance endpoints once frontend migration is complete.
