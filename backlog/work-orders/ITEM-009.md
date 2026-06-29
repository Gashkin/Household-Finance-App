# ITEM-009: Implement migration execution report with per-sheet totals, mismatches, and unresolved rows.

## Objective
Provide a reportable execution view of migration quality and unresolved variance after reconciliation.

## Checklist
- [x] Add backend execution report model with per-sheet totals and unresolved counts.
- [x] Add execution report endpoint by importId.
- [x] Add backend controller test for report endpoint contract.
- [x] Surface execution report in planner migration panel.

## Suggested files
- backend/src/main/java/com/householdfinance/service/SpreadsheetMigrationService.java
- backend/src/main/java/com/householdfinance/controller/SpreadsheetMigrationController.java
- backend/src/test/java/com/householdfinance/SpreadsheetMigrationControllerTest.java
- frontend/src/components/PlannerWorkspace.tsx
