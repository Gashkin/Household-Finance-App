# ITEM-014: Add load validation gate (dry-run, row-level errors, explicit confirmation).

## Objective
Introduce a safe pre-commit validation step in Migrater workspace before applying workbook data to persisted domain tables.

## Checklist
- [x] Add backend dry-run validation endpoint for workbook load execution.
- [x] Return row-level validation results (errors, warnings, and counts by component type).
- [x] Add explicit confirmation token/step required before commit execution.
- [x] Render validation report in migrater workspace with actionable per-row feedback.
- [x] Block commit action when validation has unresolved hard errors.
- [x] Add backend controller tests and frontend build validation for validation gate and confirmation behavior.

## Suggested files
- backend/src/main/java/com/householdfinance/service/WorkbookIngestionService.java
- backend/src/main/java/com/householdfinance/service/SpreadsheetMigrationService.java
- backend/src/main/java/com/householdfinance/controller/WorkbookIngestionController.java
- backend/src/test/java/com/householdfinance/WorkbookIngestionControllerTest.java
- frontend/src/components/MigraterWorkspace.tsx
- frontend/src/styles.css
