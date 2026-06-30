# Existing State Archaeology

## Part 1: Structural Decisions

### Architecture Pattern
- Pattern observed: The workbook is a spreadsheet-based household budget system with monthly sheets that are manually maintained and cross-referenced by formulas, rather than a database-backed application.
- Evidence: The workbook contains many month-specific tabs such as Jan_19, Jan_24, Jan_25, Jan_26, plus a central summary sheet named MonthlyBudget and supporting sheets such as Becky2024, TaxCalcs, and Renovation Financing.
- Implication: The structure is highly spreadsheet-centric and depends on sheet-specific formulas, copied layouts, and manual updates. Replacing or modernizing it would require re-encoding both the calculations and the human-maintained conventions embedded in the tabs.

### Layer Boundaries
- How layers are separated and enforced: There is no formal application layer separation. The workbook mixes raw transaction data, budgeting assumptions, calculations, and reporting in the same file.
- Where boundaries are violated and how often: The same workbook combines monthly budget planning, historical data, tax calculations, renovation financing, and credit-card transaction imports. The same sheet often carries both operational inputs and derived totals.

### Module Organisation
- Principle used to group code: The workbook is organized by time period and purpose. Monthly sheets group one month of budgeting activity, while supporting sheets hold special-case calculations and financing scenarios.
- Exceptions to that principle: The workbook has a central MonthlyBudget sheet, a separate Becky2024 sheet, a TaxCalcs sheet, a Renovation Financing sheet, and a CC Txns sheet, which do not align strictly to a single monthly cadence.

### External Dependencies
- Excel/Office: the workbook clearly relies on Microsoft Excel-compatible spreadsheet semantics and formulas.
- Credit card transaction data: the CC Txns sheet appears to import or mirror transaction history, suggesting an external source of financial activity.
- Cost to replace: Replacing Excel as the execution engine would be moderate to high because formulas, date-based assumptions, and human-maintained sheet conventions are embedded in the workbook itself.

## Part 2: Domain Decisions

### Domain Model
- Core entities identified in code: income sources, expenses, loans, grants, savings buckets, categories, months, and transactions.
- Relationships between them: Monthly budgets roll forward from prior months; renovation financing combines cash flow items, grants, and spending lines; tax calculations derive thresholds and credits from taxable pay and associated offsets.
- Business rules embedded in code vs configuration: The spreadsheet encodes rules such as monthly salary assumptions, recurring expense categories, tax-credit thresholds, and financing allocations through formulas and text labels rather than a separate rules engine.

### State Management
- What holds state and where: Monthly sheet cells hold current-month balances and categories. Historical months are captured in dedicated tabs. Supporting sheets hold scenario-specific balances and accumulated totals.
- How state is mutated: Values are primarily changed manually in cells and then propagated by formulas into downstream cells.
- What is and is not persisted: The workbook persists all visible state in the spreadsheet file. There is no separate database or API layer, and there is no clear versioned workflow beyond the workbook revision metadata.

### Naming Conventions
- Patterns used consistently: Sheet names are mostly month-based and terse, such as Jan_24, Jan_25, Jan_26, and recurring budget labels such as Salary, Groceries, Mortgage, and Utilities.
- Inconsistencies that suggest ownership changes or eras of development: The workbook mixes older names such as Oct_18 and Jan_19 with newer names such as Jan_24, Jan_25, Jan_26, and special-purpose sheets like Becky2024 and Renovation Financing. There are also inconsistent naming styles, including spaces and abbreviations like CU, SOM, and EOM.

## Part 3: Debt Classification

### Load-Bearing Complexity
- The monthly budget history is complex but intentional; it appears to represent a multi-year household financial model with recurring categories and evolving assumptions.
- The renovation financing sheet is also load-bearing complexity because it uses running totals and grant application logic to track a specific project.

### Accidental Complexity
- The workbook appears to contain duplicated or repeated structures across many month sheets, which likely reflect manual copying rather than a formally modeled process.
- The CC Txns sheet contains apparent merged or repeated transaction structures and inconsistent formatting that suggest imported data was adapted by hand.

### Abandoned Patterns
- The presence of many historical monthly sheets suggests that earlier versions of the budgeting model were carried forward over time, rather than replaced by a single stable schema.
- The workbook has a sheet named BeckyDublins that appears to be a one-off scheduling or event list rather than part of the core budget model.

### Workarounds
- The workbook uses formulas that point across sheets, especially in MonthlyBudget and Renovation Financing, indicating that the author used spreadsheet mechanics to simulate relationships that would normally be modeled as data relationships.
- The tax sheet uses hard-coded formulas and offsets for credits and thresholds, suggesting manual adjustment rather than a general-purpose calculation layer.

## Part 4: Risk Surface

### Fragile Areas
- Cross-sheet formula dependencies are fragile because breaking or renaming a source sheet or cell would cascade through the workbook.
- The historical monthly sheets may drift from the current budget model if the layout or labels change without careful propagation.

### Undocumented Assumptions
- The workbook assumes a recurring monthly budgeting structure and a stable set of categories across time.
- It also assumes that tax credits, household expenses, and financing categories can be represented as simple numeric offsets and formulas.

### Integration Points
- The workbook has a likely external touchpoint in the CC Txns sheet, where card transaction data appears to be brought in and formatted.
- There is also a likely external financial context in the renovation financing and tax sheets where amounts and assumptions are compared to actual household events.
- Failure handling is minimal; there is no explicit validation layer, no reconciliation workflow, and no clear audit trail beyond the spreadsheet itself.

## Summary

### Codebase Summary
- Apparent age and evolution stages: The workbook spans many years of financial planning, with early monthly sheets from 2018/2019 and later sheets extending into 2026. It looks like a long-lived, progressively evolved personal finance model rather than a cleanly designed system.
- Core architectural pattern: Spreadsheet-based budgeting with sheet-per-period organization and formula-driven calculations.
- Estimated number of implicit decisions found: At least 12 major implicit decisions around budgeting structure, tracking conventions, financial categories, and cross-sheet computation.
- Highest risk areas: Cross-sheet formula dependencies, historical sheet drift, and weak separation between model, input, and reporting.
- Recommended first actions before adding new work: Document the current sheet purpose and formulas, identify the authoritative source sheets, and decide whether the spreadsheet should be preserved as-is or migrated to a structured data model.
