# Component Patterns

## UI patterns
- Planner workspace uses table-driven forms and review panels.
- Planner workspace uses a tabbed menu structure to isolate planner slices by workflow stage.
- Minimum planner tabs: Forecast and Current Month, Monthly Commitments, Once Off Commitments, Account Management.
- Monthly Commitments tab renders recurring commitment definitions grid and does not perform recurring-to-transaction conversion in UI.
- Monthly Commitments tab supports inline add/save/delete actions against recurring commitment definitions with numeric amount/day validation.
- Monthly Commitments tab supports CSV download/upload for bulk recurring updates (create/update).
- Once Off Commitments tab supports inline add/save/delete actions and page-level save-all against future one-off transaction rows with singular due dates.
- Once Off Commitments tab supports CSV download/upload for bulk future one-off updates (create/update).
- Forecast and Current Month includes a dedicated Credit Card Repayment affordability card with explicit working values.
- Forecast grid left column stacks Accounts and Categories cards, middle column hosts Credit Card Repayment, right column hosts Commitments.
- Accounts working rows for Total incomes and Total expenses should avoid verbose explanatory text.
- Categories table uses Category and Total columns only (no Working column).
- Account Management tab supports account toggle between Current Account, Credit card, and Revolut.
- Account Management tab supports in-place account balance view/edit and save.
- Account Management tab hosts transaction import preview/accept and a transaction history list.
- Account Management tab supports manual mapping of imported history rows to future one-off transactions.
- Consumer dashboard uses summary cards and simple charts.
- Import workflow uses a staged review screen.
