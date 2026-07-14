# ITEM-021: Migrate workbook ingestion persistence paths to monthly_commitments and transactions

## Objective
Align workbook ingestion execution, snapshot, and rollback flows with the new split data model so ingestion no longer depends on legacy commitments-only persistence.

## Scope
- Replace workbook load apply path for commitments with split writes:
  - recurring mapped rows -> monthly_commitments
  - one-off future mapped rows -> transactions (`txn_type = ONE_OFF_FUTURE`)
- Update ingestion snapshot and rollback payloads to persist/restore split tables.
- Keep existing workbook validation and operator flow stable.

## Inputs
- adr/ADR-005.md
- adr/ADR-006.md
- spec/zachman.md
- backlog/work-orders/ITEM-020.md

## Checklist
- [x] Update WorkbookIngestionService apply path to target split tables.
- [x] Update snapshot payload model to include monthly commitments and transaction instances.
- [x] Update rollback restore logic to restore split tables in consistent order.
- [x] Preserve existing validation/execution API contract for workbook load endpoints.
- [x] Update/extend tests for workbook ingestion service/controller to cover split persistence behavior.
- [x] Verify rollback returns planner data to pre-load state with split tables.

## Deliverables
- backend/src/main/java/com/householdfinance/service/WorkbookIngestionService.java
- backend/src/test/java/com/householdfinance/WorkbookIngestionServiceTest.java
- backend/src/test/java/com/householdfinance/WorkbookIngestionControllerTest.java
- Any required supporting repository/entity touchpoints

## Verification Evidence
- `mvn test` passes for backend.
- Workbook validation + execute + rollback flow succeeds on split schema.
- Planner endpoint remains functional after ingestion and rollback cycles.

## Notes
- This item supersedes any remaining legacy reliance on the `commitments` table inside ingestion internals.

## Evidence
- Date: 2026-07-13
- Status: Completed
- Commands run:
  - `mvn test`
  - `Invoke-RestMethod -Uri 'http://localhost:8080/planner' -Method Get`
- Key outputs:
  - `Tests run: 23, Failures: 0, Errors: 0, Skipped: 0`
  - `BUILD SUCCESS`
  - `accounts=2 categories=0 commitments=37 recurring=22`
- Files changed:
  - `backend/src/main/java/com/householdfinance/service/WorkbookIngestionService.java`
  - `backend/src/test/java/com/householdfinance/WorkbookIngestionServiceTest.java`
  - `backend/src/test/java/com/householdfinance/WorkbookIngestionControllerTest.java`
- Acceptance criteria mapping:
  - [x] Split persistence apply path implemented: recurring writes to `monthly_commitments`, one-off future writes to `transactions` (`ONE_OFF_FUTURE`).
  - [x] Snapshot/rollback payload now captures and restores `monthly_commitments` and `transactions`.
  - [x] API contract preserved for workbook load endpoints (controller contract tests remain green).
  - [x] Service tests extended with execute split persistence and rollback restore coverage.
  - [x] Planner endpoint smoke check remains functional post-change.

## Review Input

- Item: ITEM-021
- Work order path: backlog/work-orders/ITEM-021.md
- Session date: 2026-07-13
- Reviewer: GitHub Copilot

## Scope Validation

- Only files in scope modified: Yes
- Any out-of-scope changes detected: No
- Notes: All changes confined to service and requested test files.

## Acceptance Criteria Check

1. Criterion: Update WorkbookIngestionService apply path to target split tables.
- Status: Pass
- Evidence: `applyCommitments` now routes `Recurring` to `monthly_commitments` and month-label due dates to `transactions` with `txn_type=ONE_OFF_FUTURE`.

2. Criterion: Update snapshot payload model to include monthly commitments and transaction instances.
- Status: Pass
- Evidence: Snapshot payload includes `monthlyCommitments` and `transactions`; rollback restore rehydrates both.

3. Criterion: Update rollback restore logic to restore split tables in consistent order.
- Status: Pass
- Evidence: Restore clears and saves `transactions`, `monthly_commitments`, `categories`, and `accounts` in deterministic sequence.

4. Criterion: Preserve existing validation/execution API contract for workbook load endpoints.
- Status: Pass
- Evidence: `WorkbookIngestionControllerTest` passes all 7 endpoint contract tests.

5. Criterion: Update/extend tests for workbook ingestion service/controller to cover split persistence behavior.
- Status: Pass
- Evidence: Added service tests for execute split writes and rollback restore; controller expectations updated for refined transaction-row messaging.

6. Criterion: Verify rollback returns planner data to pre-load state with split tables.
- Status: Pass
- Evidence: Service rollback test verifies split-table delete/restore behavior from snapshot payload.

## Build and Test Evidence

- Build command(s): `mvn test`
- Build status: Success
- Test command(s): `mvn test`
- Test status: Passed
- Key output summary: `Tests run: 23, Failures: 0, Errors: 0, Skipped: 0`.

## Data/API Verification (if applicable)

- Endpoint checks performed: `GET /planner`
- DB/data checks performed: Planner response count check (accounts/categories/commitments/recurring).
- Observed results: `accounts=2 categories=0 commitments=37 recurring=22`.

## Drift and Risk Check

- Any feature added beyond item scope: No
- Any unresolved ambiguity implemented without decision: No
- Regression risk observed: Low; risk centered on date-label parsing for one-off writes, covered by test and existing parser contract.

## Verdict

- Verdict: Pass
- Reason: All ITEM-021 acceptance criteria satisfied with passing tests and runtime planner smoke verification.

## progress.md Update

- Current Item status set to: Ready for Review (ITEM-021)
- Completed Items updated: No
- Next Item set to: None (ordered backlog currently ends at ITEM-021)

## Traceability

- ADR references: `adr/ADR-005.md`, `adr/ADR-006.md`
- Zachman references: `spec/zachman.md` split persistence sections
- ops references: No additional ops doc edits in this execution item
- Work-order references: `backlog/work-orders/ITEM-021.md`
