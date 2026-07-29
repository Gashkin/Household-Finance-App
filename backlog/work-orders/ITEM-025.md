# ITEM-025: Add CSV download/upload for Monthly and Once Off commitment grids

## Objective
Enable CSV download and upload workflows for both Monthly Commitments and Once Off Commitments grids so planners can perform bulk edits externally and re-import updates.

## Scope
- Monthly Commitments grid:
  - Download current rows as CSV.
  - Upload CSV for batch create/update.
- Once Off Commitments grid:
  - Download current rows as CSV.
  - Upload CSV for batch create/update.
- Keep inline row CRUD and page-level Save All behavior intact.
- Validate CSV headers and field formats before applying changes.

## Inputs
- spec/zachman.md
- ops/pass3/component-patterns.md
- backlog/work-orders/ITEM-024.md
- frontend/src/components/PlannerWorkspace.tsx

## Checklist
- [x] Add monthly CSV download action.
- [x] Add monthly CSV upload (file select + apply) action.
- [x] Add one-off CSV download action.
- [x] Add one-off CSV upload (file select + apply) action.
- [x] Validate CSV header format before apply.
- [x] Preserve existing inline CRUD and page Save All workflows.
- [x] Verify frontend build passes.

## Deliverables
- frontend/src/components/PlannerWorkspace.tsx
- frontend/src/styles.css
- spec/zachman.md
- ops/pass3/component-patterns.md
- backlog/ordered-backlog.md
- backlog/work-orders/ITEM-025.md
- progress.md

## Verification Evidence
- `npm run build` passes.
- Both commitment grids expose CSV download and upload controls.

## Notes
- CSV upload supports both updates (when `id` provided) and creates (when `id` blank).
- Monthly CSV expected header: `id,title,amount,dayOfMonth,category`.
- One-off CSV expected header: `id,title,amount,dueDate,category`.

## Evidence
- Date: 2026-07-14
- Status: Completed
- Commands run:
  - `npm run build`
- Key outputs:
  - `tsc -b && vite build`
  - `✓ built`
- Files changed:
  - `frontend/src/components/PlannerWorkspace.tsx`
  - `frontend/src/styles.css`
  - `spec/zachman.md`
  - `ops/pass3/component-patterns.md`
  - `backlog/ordered-backlog.md`
  - `backlog/work-orders/ITEM-025.md`
  - `progress.md`

## Review Input

- Item: ITEM-025
- Work order path: backlog/work-orders/ITEM-025.md
- Session date: 2026-07-14
- Reviewer: GitHub Copilot

## Scope Validation

- Only files in scope modified: Yes
- Any out-of-scope changes detected: No

## Acceptance Criteria Check

1. Criterion: CSV download/upload available for Monthly grid.
- Status: Pass
- Evidence: Monthly toolbar includes Download CSV, file input, and Upload CSV actions.

2. Criterion: CSV download/upload available for Once Off grid.
- Status: Pass
- Evidence: Once-off toolbar includes Download CSV, file input, and Upload CSV actions.

3. Criterion: Bulk apply supports create and update.
- Status: Pass
- Evidence: Upload handler updates rows when `id` present and creates rows when `id` empty.

4. Criterion: Field/header validation enforced.
- Status: Pass
- Evidence: Header checks and payload validation applied before submission.

5. Criterion: Build passes.
- Status: Pass
- Evidence: Frontend build successful.

## Verdict

- Verdict: Pass
- Reason: CSV bulk workflows implemented and validated without breaking existing grid features.
