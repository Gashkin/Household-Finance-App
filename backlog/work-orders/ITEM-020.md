# ITEM-020: Split planner persistence into monthly_commitments and transactions

## Objective
Replace legacy single-table commitments persistence with two explicit tables: recurring payment definitions in monthly_commitments and dated transaction instances in transactions, while preserving planner API behavior for the existing frontend.

## Scope
- Add new entities and repositories for monthly commitments and transactions.
- Update planner service reads/writes to route data by recurring vs one-off semantics.
- Keep planner endpoint contract stable so frontend behavior does not regress during transition.
- Update SQL seed scripts to write to the new tables.

## Inputs
- spec/zachman.md (Planner Screens in Zachman Structure + data model refinement)
- backlog/work-orders/ITEM-019.md

## Checklist
- [x] Add monthly_commitments entity and repository.
- [x] Add transactions entity, transaction type enum, and repository.
- [x] Refactor planner service to aggregate commitments from monthly_commitments and transactions.
- [x] Route create/update/delete commitment requests to the correct table.
- [x] Keep `/planner` response backward-compatible for current frontend usage.
- [x] Update SQL scripts to seed monthly_commitments and transactions.
- [x] Run backend test suite and confirm passing results.
- [x] Smoke test planner endpoint after migration and reseed.

## Deliverables
- backend/src/main/java/com/householdfinance/entity/MonthlyCommitmentEntity.java
- backend/src/main/java/com/householdfinance/entity/TransactionEntity.java
- backend/src/main/java/com/householdfinance/entity/TransactionType.java
- backend/src/main/java/com/householdfinance/repository/MonthlyCommitmentRepository.java
- backend/src/main/java/com/householdfinance/repository/TransactionRepository.java
- backend/src/main/java/com/householdfinance/service/PlannerService.java
- backend/sql/insert-recurring-commitments.sql
- backend/sql/insert-current-month-payments.sql
- backend/sql/insert-august-one-offs.sql

## Verification Evidence
- `mvn test` (backend): build success, tests passing.
- Reseed verification:
  - monthly_commitments row count: 22
  - transactions row count: 15
- Planner endpoint smoke check:
  - commitments aggregated from both tables and returned successfully.

## Notes
- Workbook ingestion service still includes legacy commitments-table paths and should be migrated in a follow-on item.
