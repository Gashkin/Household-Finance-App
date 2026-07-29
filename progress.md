## Current Item
ITEM-031: Manual Transaction Loading and Commitment Matching.
Status: In Progress (Slice 1: suggested + one-click matching delivered)

## Completed Items
- Phase 0 archaeology completed
- Phase E elicitation completed
- Phase P population completed
- Phase V validation completed
- Phase 1 audit completed
- Phase 2 completion artifacts created
- Phase 3 operational specs generated
- Phase 4 backlog generated
- ITEM-001: Set up the application shell and authentication skeleton completed
- ITEM-002: Implement planner workspace for accounts, categories, and commitments completed
- ITEM-003: Implement transaction import and review workflow completed
- ITEM-004: Implement calculation engine and monthly summary data completed
- ITEM-005: Implement consumer dashboard view completed
- ITEM-006: Implement spreadsheet migration import and reconciliation support completed
- ITEM-007: Implement direct .xlsx workbook ingestion and sheet extraction pipeline completed
- ITEM-008: Implement workbook-to-domain mapping for accounts, categories, commitments, and transactions completed
- ITEM-009: Implement migration execution report with per-sheet totals, mismatches, and unresolved rows completed
- ITEM-010: Introduce persistence layer foundation (JPA entities, repositories, and H2/local database configuration) completed (delivered ahead of sequence)
- ITEM-011: Persist planner domain data (accounts, categories, commitments) and switch planner service from in-memory to repository-backed reads/writes completed (delivered ahead of sequence)
- ITEM-012: Persist migration artifacts (imports, workbook previews, mappings, execution reports) with traceable import history completed
- ITEM-013: Add Migrater persona and move migration steps out of planner while preparing workbook load into persisted components completed
- ITEM-014: Add load validation gate with dry-run, row-level errors, and user confirmation before commit completed
- ITEM-015: Add post-load verification dashboard (load summary, applied counts, rejected rows, and rollback option) completed
- ITEM-016: Persist load batch state (summary, rejections, rollback snapshot) durably so post-load verification survives restarts completed
- ITEM-017: Implement targeted upload classification for one-off and recurring commitments with Jun26 balance semantics completed
- ITEM-018: Prep and execute targeted workbook load for `Monthly Budget` and `*26` tabs completed
- ITEM-019: Create one-off SQL insert scripts for manual balance, recurring commitment, current-month payment, and future one-off loading completed
- ITEM-020: Split legacy commitments persistence into monthly_commitments and transactions, update planner persistence logic to use both tables, and keep planner API behavior stable during transition completed
- ITEM-021: Migrate workbook ingestion load/rollback/snapshot paths from legacy commitments storage to monthly_commitments and transactions completed
- ITEM-022: Build planner tabs and Monthly Commitments UI slice completed
- ITEM-023: Make the Monthly Commitments tab editable with inline CRUD actions completed
- ITEM-024: Add Once Off Commitments grid with inline CRUD and page-level save completed
- ITEM-025: Add CSV download/upload bulk-edit workflows for both commitment grids completed
- ITEM-026: Use singular due dates for Once Off Commitments completed
- ITEM-027: Convert Transaction Import tab to Account Management completed
- ITEM-028: Add Credit Card Repayment affordability working and forecast layout adjustments completed
- ITEM-029: Full Open Knowledge Format (OKF) Adoption completed
- ITEM-030: Add OKF Contributor Onboarding to README completed

## Blocked Items
(none)

## Next Item
ITEM-031: Continue manual row entry and bulk paste/import flows
