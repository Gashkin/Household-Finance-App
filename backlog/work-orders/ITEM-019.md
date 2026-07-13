# ITEM-019: Create one-off SQL insert scripts for manual balance, recurring commitment, current-month payment, and future one-off loading

## Objective
Build direct database scripts to load manually supplied balances into persisted planner components for one-time migration, without depending on a dedicated Migrater runtime workflow, workbook extractor, or JSON loader.

## Scope
- One-time insertion or replacement of manually supplied account balances, recurring commitments, current-month one-off payments, and future one-off payments.
- Repeatable local execution with a clear operator command and output.

## Checklist
- [x] Create direct SQL script(s) for local execution.
- [x] Use idempotent upsert semantics so the script can be rerun safely.
- [x] Load the provided AIB and Card balances into the accounts table.
- [x] Load the provided recurring monthly commitments into the monthly_commitments table.
- [x] Load the provided current-month one-off payments into the transactions table.
- [x] Load the provided future one-off August payments into the transactions table.
- [x] Document how to run the script against the local H2 database.

## Deliverables
- SQL script under a backend database or scripts path.
- Short usage note for local execution.

## Usage Note (local H2)
- Ensure backend has been started at least once so schema tables exist.
- Run commands in order:
	- `RUNSCRIPT FROM 'C:/Users/ger_o/Household-Finance-App/backend/sql/insert-current-balances.sql';`
	- `RUNSCRIPT FROM 'C:/Users/ger_o/Household-Finance-App/backend/sql/insert-recurring-commitments.sql';`
	- `RUNSCRIPT FROM 'C:/Users/ger_o/Household-Finance-App/backend/sql/insert-current-month-payments.sql';`
	- `RUNSCRIPT FROM 'C:/Users/ger_o/Household-Finance-App/backend/sql/insert-august-one-offs.sql';`

## Notes
- Persistence targets were updated during implementation from a single `commitments` table to `monthly_commitments` plus `transactions`.
- Follow-on schema and service migration work is tracked in ITEM-020.

## Suggested files
- backend/
- Docs/
- backlog/work-orders/
- progress.md
