# Ordered Backlog

1. Set up the application shell and authentication skeleton.
2. Implement planner workspace for accounts, categories, and commitments.
3. Implement transaction import and review workflow.
4. Implement calculation engine and monthly summary data.
5. Implement consumer dashboard view.
6. Implement spreadsheet migration import and reconciliation support.
7. Implement direct .xlsx workbook ingestion and sheet extraction pipeline.
8. Implement workbook-to-domain mapping for accounts, categories, commitments, and transactions.
9. Implement migration execution report with per-sheet totals, mismatches, and unresolved rows.
10. Introduce persistence layer foundation (JPA entities, repositories, and H2/local database configuration).
11. Persist planner domain data (accounts, categories, commitments) and switch planner service from in-memory to repository-backed reads/writes.
12. Persist migration artifacts (imports, workbook previews, mappings, execution reports) with traceable import history.
13. Add Migrater persona and move migration steps out of planner while preparing workbook load into persisted components.
14. Add load validation gate with dry-run, row-level errors, and user confirmation before commit.
15. Add post-load verification dashboard (load summary, applied counts, rejected rows, and rollback option).
16. Persist load batch state (summary, rejections, rollback snapshot) durably so post-load verification survives restarts.
17. Implement targeted upload classification: tabs ending in `26` are one-off commitments, `Monthly Budget` defines recurring commitments, and `Jun26` bank amounts are current balances.
18. Prep and execute targeted workbook load: convert xlsx to load-ready data and load only `Monthly Budget` and tabs ending in `26`.
19. Create one-off SQL insert scripts to load manually supplied balances, recurring commitments, current-month payments, and future one-off payments into persisted planner components without relying on Migrater UI workflow, workbook extraction, or JSON loading.
20. Split legacy commitments persistence into `monthly_commitments` and `transactions`, update planner persistence logic to use both tables, and keep planner API behavior stable during transition.
21. Migrate workbook ingestion load/rollback/snapshot paths from legacy `commitments` storage to `monthly_commitments` and `transactions` so ingest and rollback operate on the same model as planner persistence.
22. Build planner tabbed navigation with a dedicated Monthly Commitments grid slice while preserving current forecast and import workflows as separate tabs.
23. Make the Monthly Commitments tab fully editable with inline add/save/delete actions wired to planner commitment CRUD endpoints.
24. Add Once Off Commitments grid with inline add/edit/delete and page-level save-all against future one-off transaction rows.
25. Add CSV download/upload bulk-edit workflows for both Monthly Commitments and Once Off Commitments grids.
26. Change Once Off Commitments from month-based due labels to singular due dates (YYYY-MM-DD) across API and UI.
27. Convert Transaction Import tab to Account Management with account toggle (Current Account, Credit card, Revolut), balance view/edit, transaction import/history, and manual mapping of imported rows to future one-off transactions.
28. Add Credit Card Repayment affordability calculation to Forecast and Current Month, remove verbose Total Incomes/Expenses working descriptions, remove Categories working column, stack Categories under Accounts in left column, and place Credit Card Repayment in middle column.
29. Adopt Open Knowledge Format (OKF) across spec/ops/adr/backlog with standardized frontmatter, linked concept indexes, validation checks, and prompt alignment for OKF-first context.
30. Add README onboarding for OKF-first contributor flow, including direct links to OKF index/quickstart and validation command guidance.
31. Implement manual transaction loading workflows and robust matching of loaded history rows to future one-off commitments with visible match status and validation handling.
