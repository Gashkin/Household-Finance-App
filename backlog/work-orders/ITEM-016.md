# ITEM-016: Persist load batch state durably for summary, rejections, and rollback.

## Objective
Ensure workbook load batch records survive app restarts by persisting post-load summary, rejected rows, and rollback snapshot as durable artifacts.

## Checklist
- [x] Persist post-load summary by execution id.
- [x] Persist rejected-row payload by execution id.
- [x] Persist planner rollback snapshot by execution id.
- [x] Read latest summary from durable storage (not in-memory).
- [x] Read rejected rows from durable storage.
- [x] Execute rollback from durable snapshot and persist rollback status.
- [x] Validate backend tests and frontend build after persistence changes.

## Suggested files
- backend/src/main/java/com/householdfinance/repository/MigrationArtifactRepository.java
- backend/src/main/java/com/householdfinance/service/MigrationArtifactService.java
- backend/src/main/java/com/householdfinance/service/WorkbookIngestionService.java
- backend/src/main/java/com/householdfinance/controller/WorkbookIngestionController.java
- backend/src/test/java/com/householdfinance/WorkbookIngestionControllerTest.java
