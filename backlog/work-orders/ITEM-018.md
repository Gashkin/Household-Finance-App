# ITEM-018: Prep workbook into load-ready data and execute targeted load.

## Objective
Convert uploaded workbook data into load-ready normalized records and load only sanctioned tabs: `Monthly Budget` and tabs ending in `26`.

## Checklist
- [x] Enforce targeted preprocessing rules: only `Monthly Budget` and tabs ending in `26` are transformed.
- [x] Ensure `Monthly Budget` rows become recurring commitments.
- [x] Ensure `Jun26` bank account rows become current balances.
- [x] Ensure `*26` non-bank rows become one-off commitments.
- [x] Ignore non-target sheets and emit diagnostics for ignored tabs.
- [x] Add automated tests confirming only targeted tabs are loaded.

## Suggested files
- backend/src/main/java/com/householdfinance/service/WorkbookIngestionService.java
- backend/src/test/java/com/householdfinance/WorkbookIngestionServiceTest.java
- frontend/src/components/MigraterWorkspace.tsx
