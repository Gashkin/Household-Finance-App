## Current Item
ITEM-021: Migrate workbook ingestion load/rollback/snapshot paths from legacy commitments storage to monthly_commitments and transactions.
Status: Ready for Review

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

## Blocked Items
(none)

## Next Item
None (ordered backlog currently ends at ITEM-021)
