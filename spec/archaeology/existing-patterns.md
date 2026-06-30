# Existing Patterns

## Patterns Agents Must Follow
- Sheet-per-period budgeting: monthly tabs are the main organizing unit for historical data and current planning; preserve this concept if the spreadsheet is extended.
- Formula-driven aggregation: calculations that roll up totals or track running balances should remain explicit and visible in the workbook.
- Category-based budgeting: recurring expense and income categories such as Salary, Groceries, Rent, and Utilities are the stable vocabulary of the model.

## Patterns Agents Must Not Introduce
- A database-first model without preserving the spreadsheet’s historical monthly structure: this would conflict with the workbook’s existing time-based organization.
- Hidden logic in code or macros that is not reflected in visible sheet formulas: this would conflict with the workbook’s transparent, cell-based calculation style.
- Untracked category proliferation: adding new labels without a clear mapping to existing categories would conflict with the current budgeting conventions.
