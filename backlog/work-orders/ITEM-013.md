# ITEM-013: Add Migrater persona and remove migration steps from planner workspace.

## Objective
Introduce a dedicated Migrater role/workspace and move migration-specific flows out of Planner so planner is focused on planning operations only.

## Checklist
- [x] Add `migrater` persona to app role switcher and route to a dedicated workspace.
- [x] Create a `MigraterWorkspace` component and move migration-specific UI from planner into it.
- [x] Move workbook upload/preview/mapping UI from planner to migrater workspace.
- [x] Move migration baseline preview/reconcile/report/history UI from planner to migrater workspace.
- [x] Keep planner workspace scoped to accounts/categories/commitments + transaction import/review only.
- [x] Ensure both personas render cleanly and preserve existing API integrations.

## Suggested files
- frontend/src/App.tsx
- frontend/src/components/PlannerWorkspace.tsx
- frontend/src/components/MigraterWorkspace.tsx
- frontend/src/styles.css
