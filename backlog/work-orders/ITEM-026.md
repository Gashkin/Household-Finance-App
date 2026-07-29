# ITEM-026: Use singular due dates for Once Off Commitments

## Objective
Change Once Off Commitments due date handling from month-based labels to singular dates, so each one-off row stores and edits an exact due date (YYYY-MM-DD).

## Scope
- Backend planner commitment API:
  - Accept non-recurring dueDate as singular ISO date.
  - Return non-recurring dueDate as singular ISO date.
- Frontend once-off commitments grid:
  - Use date input controls.
  - Validate singular date format.
  - Keep inline/page-level save behavior.
- Keep recurring commitments behavior unchanged.

## Inputs
- spec/zachman.md
- ops/pass3/component-patterns.md
- backlog/work-orders/ITEM-025.md
- backend/src/main/java/com/householdfinance/service/PlannerService.java
- frontend/src/components/PlannerWorkspace.tsx

## Checklist
- [x] Backend parses non-recurring dueDate as ISO date.
- [x] Backend serializes non-recurring dueDate as ISO date.
- [x] Frontend once-off inputs changed to singular date fields.
- [x] Frontend validation updated from month-label to singular date format.
- [x] This-month filter includes one-offs due within current calendar month.
- [x] Verify frontend build passes.

## Deliverables
- backend/src/main/java/com/householdfinance/service/PlannerService.java
- frontend/src/components/PlannerWorkspace.tsx
- spec/zachman.md
- ops/pass3/component-patterns.md
- backlog/ordered-backlog.md
- progress.md
- backlog/work-orders/ITEM-026.md

## Evidence
- Date: 2026-07-14
- Status: Completed
- Commands run:
  - npm run build
- Key outputs:
  - tsc -b && vite build
  - build completed successfully
