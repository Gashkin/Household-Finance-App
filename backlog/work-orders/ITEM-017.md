# ITEM-017: Implement targeted upload mapping rules for commitment/balance semantics.

## Objective
Apply deterministic workbook targeting rules so commitments and balances are interpreted correctly for the current month.

## Rules
- Tabs with names ending in `26` are treated as one-off commitments for that month.
- The `Monthly Budget` tab defines recurring monthly commitments.
- Current month is `Jun26`, so bank account amounts are interpreted as current account balances.

## Checklist
- [x] Add workbook tab targeting classifier for month-suffixed tabs (e.g., `Jun26`).
- [x] Add one-off commitment extraction for tabs ending in `26`.
- [x] Add recurring commitment extraction from `Monthly Budget`.
- [x] Add current-balance interpretation for bank account values in `Jun26` context.
- [x] Add row-level diagnostics when tab/row cannot be classified under these rules.
- [x] Add backend tests covering the targeting rules and ambiguous cases.
- [x] Surface targeting results and applied rules in Migrater UI summary.

## Suggested files
- backend/src/main/java/com/householdfinance/service/WorkbookIngestionService.java
- backend/src/main/java/com/householdfinance/controller/WorkbookIngestionController.java
- backend/src/test/java/com/householdfinance/WorkbookIngestionControllerTest.java
- frontend/src/components/MigraterWorkspace.tsx
