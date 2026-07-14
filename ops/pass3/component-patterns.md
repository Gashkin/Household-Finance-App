# Component Patterns

## UI patterns
- Planner workspace uses table-driven forms and review panels.
- Planner workspace uses a tabbed menu structure to isolate planner slices by workflow stage.
- Minimum planner tabs: Forecast and Current Month, Monthly Commitments, Once Off Commitments, Transaction Import.
- Monthly Commitments tab renders recurring commitment definitions grid and does not perform recurring-to-transaction conversion in UI.
- Monthly Commitments tab supports inline add/save/delete actions against recurring commitment definitions with numeric amount/day validation.
- Once Off Commitments tab supports inline add/save/delete actions and page-level save-all against future one-off transaction rows with month-label due dates.
- Consumer dashboard uses summary cards and simple charts.
- Import workflow uses a staged review screen.
