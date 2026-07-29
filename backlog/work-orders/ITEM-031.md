# ITEM-031: Manual Transaction Loading and Commitment Matching

## Objective
Enable planner-led manual loading of transaction rows and reliable matching of those rows to future one-off commitments, with clear review and save behavior.

## Scope
- Add manual transaction row entry workflow in Account Management.
- Support bulk manual paste/import of transaction rows (date, description, amount, account).
- Persist accepted manual rows into transaction history using existing import pathways or equivalent persisted model.
- Add explicit matching workflow from imported/manual history rows to ONE_OFF_FUTURE commitments.
- Add visible match status on both transaction history and one-off commitment rows.
- Add validation and error messaging for invalid dates, non-numeric amounts, and missing required fields.

## Non-goals
- Automated bank feed integration.
- Full reconciliation engine redesign.
- Changes to recurring commitment storage model.

## Inputs
- frontend/src/components/PlannerWorkspace.tsx
- backend/src/main/java/com/householdfinance/service/TransactionImportService.java
- backend/src/main/java/com/householdfinance/controller/TransactionImportController.java
- backend/src/main/java/com/householdfinance/service/PlannerService.java
- ops/pass1/data-flow.md
- ops/pass2/error-handling.md
- knowledge/okf/workflows/transaction-import-and-review.md
- knowledge/okf/rules/error-handling-rules.md

## Checklist
- [ ] Planner can add one manual transaction row without CSV file upload.
- [ ] Planner can add multiple rows in one flow (bulk/manual paste).
- [ ] Accepted rows appear in transaction history after save.
- [x] History rows can be mapped to ONE_OFF_FUTURE commitments from the UI.
- [ ] Match state is visible (unmatched/matched target id) and refresh-stable.
- [ ] Validation blocks invalid row formats and shows clear actionable errors.
- [ ] Existing import preview/accept behavior remains functional.

## Deliverables
- frontend/src/components/PlannerWorkspace.tsx
- backend/src/main/java/com/householdfinance/service/TransactionImportService.java
- backend/src/main/java/com/householdfinance/controller/TransactionImportController.java
- backend/src/main/java/com/householdfinance/service/PlannerService.java (if required for lookup/match consistency)
- ops/pass2/error-handling.md (only if behavior contract update is required)
- knowledge/okf/workflows/transaction-import-and-review.md (traceability update)
- backlog/ordered-backlog.md
- backlog/work-orders/ITEM-031.md
- progress.md

## Evidence
- Date: 2026-07-27
- Status: In Progress (Slice 1 delivered)
- Commands run:
  - `cd frontend && npm run build`
- Key outputs:
  - Transaction history now shows a suggested future one-off match per unmapped row when confidence threshold is met.
  - One-click `Use suggestion` action maps a history row to the suggested future one-off commitment.
  - Existing manual select + `Map` flow remains available.

## Implementation Notes
- Slice 1 focused on faster matching workflow (suggest + one-click apply) in Account Management transaction history.
- Suggestions are currently computed client-side from existing history + future one-off data using description, amount, sign, and optional bracketed category-tag hints.
- Manual row-entry and bulk paste/import UX changes remain pending.
