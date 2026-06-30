# ITEM-006: Implement spreadsheet migration import and reconciliation support.

## Objective
Support controlled migration from spreadsheet baseline by previewing differences and recording reconciliation decisions.

## Checklist
- [x] Add backend migration preview endpoint for spreadsheet baseline CSV.
- [x] Add backend reconcile endpoint to accept reviewed migration rows.
- [x] Add backend controller tests for migration preview and reconcile paths.
- [x] Add planner UI card to preview spreadsheet baseline variance.
- [x] Add planner UI action to reconcile selected migration rows.
- [ ] Persist reconciled migration history (future item).

## Suggested files
- backend/src/main/java/com/householdfinance/service/SpreadsheetMigrationService.java
- backend/src/main/java/com/householdfinance/controller/SpreadsheetMigrationController.java
- backend/src/test/java/com/householdfinance/SpreadsheetMigrationControllerTest.java
- frontend/src/components/PlannerWorkspace.tsx
- frontend/src/styles.css
