## Core Entities
- Accounts and balances
- Income sources
- Expenses and bills
- Transactions
- Categories and tags
- Monthly time periods
- Planned vs actual financial position

## Entity Attributes
- Accounts: name, current balance, type, ownership, and the information needed to understand what is available to spend
- Income: source, amount, timing, and whether it is recurring or one-off
- Expenses: category, amount, due date, frequency, and whether it is fixed or variable
- Transactions: date, description, amount, account, category, and status
- Categories: label and grouping for reporting and analysis
- Monthly periods: month and year as the main planning horizon
- Planned vs actual: expected amount, actual amount, and variance

The most important derived facts are:
- current cash position
- upcoming outgoings
- planned versus actual spending
- safe-to-spend amount
- discretionary spending
- trends over time
- category breakdowns
- variance and exceptions
- forecasted balance

## Entity Lifecycle
The main lifecycle is that planned transactions become actual transactions, and they rarely match exactly. Today, I update planned transactions and the spreadsheet recalculates, but I would like the system to import actual transactions and then update the plan accordingly while recording actual events and deviation from plan.

- Accounts are monitored over time and updated as transactions are recorded or imported
- Income and expenses are planned and then compared against what actually happens
- Transactions are created, categorized, and reconciled against planned amounts
- Monthly periods are used to review what happened and what is coming next

## Invariant Rules
- The numbers must add up
- We must be able to trust that everything is in sync
- If the system says we have enough cashflow for the expenses we do, that must be true
- The system should reflect real financial position rather than optimistic forecasts
- Transactions should not be missed or silently omitted
- The same financial reality should be visible to both household members in a way that is understandable

## Calculations and Derivations
- Recurring commitments are input by me
- One-off commitments are input by me
- Actual transactions are either entered by me or sourced directly
- Safe-to-spend, discretionary spending, and trend views are reporting-level derived concepts rather than core entities
- Variance between planned and actual amounts is a derived view used for understanding financial health

## Known Failure Modes
- Large unexpected transactions
- Missing transactions can break trust
- Overly complex views can make the system inaccessible to the other household member
- Forecasts that are too optimistic can create a false sense of security
