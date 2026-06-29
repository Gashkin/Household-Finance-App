# ITEM-003: Implement transaction import and review workflow.

## Objective
Allow the planner to import transaction CSV data, review detected duplicates, and accept selected rows.

## Checklist
- [x] Add backend endpoint for import preview from CSV payload.
- [x] Add backend endpoint to accept selected imported rows.
- [x] Add backend tests for preview and accept endpoints.
- [x] Add planner UI section for CSV import preview and row selection.
- [x] Add planner UI action to accept selected rows and show result.
- [ ] Add persistence for accepted transactions (future item).

## Suggested files
- backend/src/main/java/com/householdfinance/controller/TransactionImportController.java
- backend/src/main/java/com/householdfinance/service/TransactionImportService.java
- backend/src/test/java/com/householdfinance/TransactionImportControllerTest.java
- frontend/src/components/PlannerWorkspace.tsx
- frontend/src/styles.css
