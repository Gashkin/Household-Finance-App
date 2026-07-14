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
