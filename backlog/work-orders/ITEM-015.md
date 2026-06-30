# ITEM-015: Add post-load verification dashboard with summary, rejects, and rollback path.

## Objective
Provide immediate visibility and control after load execution: what was applied, what failed, and how to recover.

## Checklist
- [x] Add post-load summary endpoint including applied/rejected counts per component.
- [x] Add rejected-row details endpoint payload including reason codes and source row references.
- [x] Add rollback operation for the latest load batch (or selected load batch) with clear safety constraints.
- [x] Add migrater dashboard card for applied totals, rejected totals, and status by component.
- [x] Add UI for viewing rejected rows and invoking rollback.
- [x] Add backend/frontend tests for summary and rollback flows.

## Suggested files
- backend/src/main/java/com/householdfinance/service/WorkbookIngestionService.java
- backend/src/main/java/com/householdfinance/controller/WorkbookIngestionController.java
- backend/src/test/java/com/householdfinance/WorkbookIngestionControllerTest.java
- frontend/src/components/MigraterWorkspace.tsx
- frontend/src/styles.css
