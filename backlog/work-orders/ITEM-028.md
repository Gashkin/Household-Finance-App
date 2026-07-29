# ITEM-028: Add Credit Card Repayment Affordability and Forecast Layout Changes

## Objective
Add a Credit Card Repayment affordability working panel to Forecast and Current Month, and adjust forecast layout/working tables for clearer month-end decision support.

## Scope
- Forecast and Current Month:
  - Add Credit Card Repayment card in middle column.
  - Use formula:
    - Monthly Ongoing Commitment Income
    - minus Monthly EOM
    - minus Monthly Ongoing Commitment Becky
    - minus Monthly Ongoing Commitment Morgage
    - minus Monthly Ongoing Commitment CU
    - plus 1000 overdraft
    - minus 75 life insurance
- Layout:
  - Stack Categories card below Accounts card in left column.
  - Keep Commitments in right column.
- Simplification:
  - Remove verbose working descriptions for Total incomes and Total expenses in Accounts working table.
  - Remove Working column from Categories table.

## Inputs
- spec/zachman.md
- ops/pass3/component-patterns.md
- backlog/work-orders/ITEM-027.md
- frontend/src/components/PlannerWorkspace.tsx
- frontend/src/styles.css

## Checklist
- [x] Credit Card Repayment value is calculated and shown in forecast area.
- [x] Accounts + Categories are stacked in the left column.
- [x] Credit Card Repayment card is in the middle column.
- [x] Categories table no longer has a Working column.
- [x] Total incomes and Total expenses rows no longer show long-form working descriptions.
- [x] Frontend build passes.

## Deliverables
- frontend/src/components/PlannerWorkspace.tsx
- frontend/src/styles.css
- spec/zachman.md
- ops/pass3/component-patterns.md
- backlog/ordered-backlog.md
- backlog/work-orders/ITEM-028.md
- progress.md

## Evidence
- Date: 2026-07-14
- Status: Completed
- Commands run:
  - npm run build
- Key outputs:
  - tsc -b && vite build
  - build completed successfully
