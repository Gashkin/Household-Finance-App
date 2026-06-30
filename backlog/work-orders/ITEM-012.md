# ITEM-012: Persist migration artifacts and expose traceable history/report retrieval.

## Objective
Store migration workflow artifacts as durable records and expose report/history retrieval by import batch.

## Checklist
- [x] Add migration artifact entity and repository for type + importId indexed retrieval.
- [x] Add artifact service for payload serialization, history query, and latest-by-type lookup.
- [x] Record artifacts across import preview/accept, workbook preview/map, and migration report flows.
- [x] Add migration history endpoint and execution report endpoint integration.
- [x] Surface artifact history and execution report in planner migration UI.

## Suggested files
- backend/src/main/java/com/householdfinance/entity/MigrationArtifactEntity.java
- backend/src/main/java/com/householdfinance/repository/MigrationArtifactRepository.java
- backend/src/main/java/com/householdfinance/service/MigrationArtifactService.java
- backend/src/main/java/com/householdfinance/service/SpreadsheetMigrationService.java
- backend/src/main/java/com/householdfinance/service/WorkbookIngestionService.java
- backend/src/main/java/com/householdfinance/service/TransactionImportService.java
- backend/src/main/java/com/householdfinance/controller/SpreadsheetMigrationController.java
- frontend/src/components/PlannerWorkspace.tsx
