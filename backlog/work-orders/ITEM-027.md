# ITEM-027: Convert Transaction Import tab to Account Management

## Objective
Replace the Transaction Import tab with Account Management so planners can manage balances and import reconciliation in one place.

## Scope
- Planner tab/navigation:
  - Rename/replace Transaction Import tab with Account Management.
- Account operations:
  - Toggle between Current Account, Credit card, and Revolut.
  - View and update selected account balance.
- Import operations:
  - Keep CSV preview and accept flow.
  - Record accepted rows in transaction history.
- Mapping operations:
  - Allow manual mapping of imported history rows to future one-off transactions when auto-mapping is missing.

## Inputs
- spec/zachman.md
- ops/pass3/component-patterns.md
- backlog/work-orders/ITEM-026.md
- frontend/src/components/PlannerWorkspace.tsx
- backend/src/main/java/com/householdfinance/controller/TransactionImportController.java
- backend/src/main/java/com/householdfinance/service/TransactionImportService.java

## Checklist
- [x] Account Management tab replaces Transaction Import tab in planner navigation.
- [x] Account toggle includes Current Account, Credit card, and Revolut.
- [x] Selected account balance can be viewed and saved.
- [x] Import preview and accept remain functional in Account Management.
- [x] Accepted import rows are visible in transaction history.
- [x] Manual mapping from transaction history row to future one-off transaction is supported.
- [x] Backend tests pass.
- [x] Frontend build passes.

## Deliverables
- frontend/src/components/PlannerWorkspace.tsx
- frontend/src/styles.css
- backend/src/main/java/com/householdfinance/controller/TransactionImportController.java
- backend/src/main/java/com/householdfinance/service/TransactionImportService.java
- spec/zachman.md
- ops/pass3/component-patterns.md
- backlog/ordered-backlog.md
- backlog/work-orders/ITEM-027.md
- progress.md

## Execution Notes
- This work order is being created now to restore E2E governance ordering before proceeding further with implementation and verification.

## Evidence
- Date: 2026-07-14
- Status: Completed
- Commands run:
  - mvn test -q
  - npm run build
- Key outputs:
  - Backend tests exit code: EXIT:0
  - Frontend build output: tsc -b && vite build, built successfully
