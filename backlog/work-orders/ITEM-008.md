# ITEM-008: Implement workbook-to-domain mapping for accounts, categories, commitments, and transactions.

## Objective
Transform extracted workbook sheet rows into normalized domain structures ready for load execution.

## Checklist
- [x] Add backend mapping endpoint for workbook domain mapping.
- [x] Implement mapping logic for accounts, categories, commitments, and transactions.
- [x] Capture mapping warnings for skipped/invalid rows.
- [x] Add backend controller test for mapping response contract.
- [x] Add planner UI support to invoke workbook mapping and display mapping outputs.

## Suggested files
- backend/src/main/java/com/householdfinance/service/WorkbookIngestionService.java
- backend/src/main/java/com/householdfinance/controller/WorkbookIngestionController.java
- backend/src/test/java/com/householdfinance/WorkbookIngestionControllerTest.java
- frontend/src/components/PlannerWorkspace.tsx
- frontend/src/styles.css
