# ITEM-007: Implement direct .xlsx workbook ingestion and sheet extraction pipeline.

## Objective
Allow the planner to upload an Excel workbook directly and inspect sheet-level extraction results.

## Checklist
- [x] Add backend endpoint to accept workbook file upload (.xlsx).
- [x] Parse workbook sheets and return headers plus sample rows.
- [x] Add backend controller test for workbook preview endpoint.
- [x] Add planner UI section for workbook upload and preview.
- [ ] Add persisted import tracking for workbook previews (future item).

## Suggested files
- backend/src/main/java/com/householdfinance/service/WorkbookIngestionService.java
- backend/src/main/java/com/householdfinance/controller/WorkbookIngestionController.java
- backend/src/test/java/com/householdfinance/WorkbookIngestionControllerTest.java
- frontend/src/components/PlannerWorkspace.tsx
- frontend/src/styles.css
