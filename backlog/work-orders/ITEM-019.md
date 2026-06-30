# ITEM-019: Create one-off loader scripts for workbook ingestion

## Objective
Build script-based loaders to import workbook data directly into persisted planner components for one-time migration, without depending on a dedicated Migrater runtime workflow.

## Scope
- One-time ingestion of workbook data into accounts, categories, and commitments.
- Targeted loading rules: `Monthly Budget` and tabs ending in `26` only.
- Repeatable local execution with clear operator commands and output.

## Checklist
- [ ] Create a loader entrypoint script for local execution.
- [ ] Add script options for source workbook path and load mode (MERGE or REPLACE).
- [ ] Implement parsing and normalization for target tabs only.
- [ ] Implement account balance load behavior for `Jun26` bank rows.
- [ ] Implement recurring commitment load from `Monthly Budget`.
- [ ] Implement one-off commitment load from tabs ending in `26`.
- [ ] Emit structured load report (applied counts, skipped rows, warnings, errors).
- [ ] Add safe failure handling with non-zero exit code on hard errors.
- [ ] Add dry-run mode to validate and preview before commit.
- [ ] Document usage examples and expected outputs.
- [ ] Add tests for script parsing and load classification behavior.

## Deliverables
- Script(s) under backend tools/scripts path.
- Loader usage documentation.
- Test coverage for parsing and classification rules.

## Suggested files
- backend/
- Docs/
- backlog/work-orders/
- progress.md
