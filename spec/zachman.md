# Zachman Framework

## Row 1: Scope (Contextual)

### Why
A household finance application should replace the current spreadsheet by giving the primary planner a trustworthy way to manage commitments and transactions while giving the secondary consumer a simple view of cashflow and allowable spend.

### What
The system tracks household financial position, including balances, commitments, income, expenses, transactions, and a monthly overview reporting experience for the secondary consumer.

### How
The system is used by entering or importing transactions, managing commitments, and showing the resulting household financial picture.

### Where
The system is used in personal household finance management.

### Who
Primary planner: manages the core financial data and commitments.
Secondary consumer: uses the simplified reporting view.

### When
The system is used continuously as new transactions occur and commitments change.

## Row 2: Business (Conceptual)

### Why
The business purpose is to provide a reliable, low-friction picture of household finances that supports day-to-day planning and household decision-making.

### What
Core business concepts include accounts, income, expenses, commitments, transactions, planned vs actual, and allowable spend.

### How
The business process is to plan commitments, review imported transaction files, reconcile differences, and present a current financial picture.

### Where
The business boundary is the household finance process, not broader tax or event-specific planning.

### Who
The primary planner owns the financial data and acts on changes. The secondary consumer consumes reporting views.

### When
The business process is triggered by new commitments, imported transactions, and regular reviews.

## Row 3: System (Logical)

### Why
The system should make financial reality visible and trustworthy rather than optimistic or overly complex.

### What
The system should support accounts, commitments, transactions, categories, a monthly overview for the secondary consumer, and reconciliation of planned versus actual activity.

### How
The system should allow entry of recurring and one-off commitments, support import of transaction files with deduplication and format handling for multiple account sources, and calculate balances, upcoming outgoings, and allowable spend.

### Where
The logical boundary is the household finance domain within a single application.

### Who
The system should support two roles: planner and consumer.

### When
The system should update when commitments change or transactions are imported.

## Row 4: Technology (Physical)

### Why
The implementation should be simple, reliable, and usable by both household members.

### What
The initial implementation should be a browser-based application hosted locally on a household machine or small home server, with a monthly overview for the secondary consumer and a planner-oriented data entry experience.

### How
The implementation should use a browser-based local application approach rather than a spreadsheet and should support import of transaction files from multiple bank-account formats with deduplication.

### Where
The implementation is a household finance application served locally within the home environment.

### Who
The primary planner manages the planner-oriented data entry experience; the secondary consumer uses the monthly overview reporting experience.

### When
Initial delivery focuses on core budgeting and transaction reconciliation rather than advanced automation or tax planning.

## Rows 5-6

### Row 5: Detailed Representation
The detailed representation should include a planner-facing desktop experience for entering and editing commitments and transactions, a consumer-facing monthly overview dashboard, an import pipeline for transaction files with normalization, mapping, deduplication, and review, and a local data model that stores accounts, commitments, categories, transaction history, and import metadata.

#### Concrete architecture outline
- Presentation layer: a web-based application with two primary views, one for planner data entry and one for consumer reporting.
- Domain services: calculation services for balances, upcoming commitments, allowable spend, planned-versus-actual reconciliation, and import validation.
- Data layer: a relational database such as PostgreSQL for the first version, with tables for accounts, categories, monthly_commitments, transactions, imports, and user roles.
- Import pipeline: file ingestion from CSV or spreadsheet-style exports, field mapping, duplicate detection, review queue, and acceptance into the ledger.
- Access model: the planner role can create and edit financial data; the consumer role can view reports and summaries only.
- Deployment shape: a browser-based application hosted locally on a household machine or small home server, with local-first data handling and optional future cloud or backup enhancements.

#### Data model refinement (commitments split)
- monthly_commitments: planner-maintained recurring payment definitions.
- Required monthly_commitments fields: id, title, category, amount, day_of_month.
- transactions: dated financial instances used by reporting and snapshot calculations.
- Required transactions fields: id, txn_date, title, category, amount, txn_type, source_monthly_commitment_id (nullable).
- txn_type values:
	- ACTUAL: past bank/card transactions.
	- MONTHLY_INSTANCE_FUTURE: future generated instances from monthly_commitments.
	- ONE_OFF_FUTURE: known future one-off payments.
- Current Month Snapshot aggregates from transactions for the selected month window.
- Monthly Commitments Grid edits monthly_commitments only.
- One Off Commitments Grid edits transactions where txn_type = ONE_OFF_FUTURE.

Decision traceability:
- ADR-005 defines the commitments persistence split and transaction typing model.
- ADR-006 defines planner API compatibility constraints during migration.

#### Simple component breakdown
- App shell: starts the app, hosts the main navigation, and switches between planner and consumer experiences.
- Planner workspace: screen for managing commitments, categories, accounts, and transaction review.
- Consumer dashboard: screen for monthly summary, current balance, upcoming commitments, and allowable spend.
- Transaction import module: accepts uploaded files, normalizes them, detects duplicates, and presents a review list.
- Calculation engine: produces balances, forecasts, reconciliation results, and summary metrics.
- Data store: persists financial records and import history in PostgreSQL.
- Auth and roles: enforces planner edit rights and consumer read-only access.

#### Preferred implementation stack
- Frontend: React with TypeScript
- Backend: Spring Boot with Java
- Messaging: Kafka only if asynchronous import processing or future integrations become necessary
- Database: PostgreSQL
- Deployment: local home-hosted deployment first, with a simple single-instance setup

### Row 6: Functioning Enterprise
The system operates as a shared household finance capability in which the primary planner maintains the financial data and the secondary consumer uses a read-only monthly overview to understand the current position, upcoming commitments, and allowable spend.

## Planner Screens in Zachman Structure (Living Specification)

This section is part of the normal Zachman structure by explicitly mapping each planner screen to matrix cells.

### Screen Catalog (Current Scope)
- Planner Dashboard
- Monthly Commitments Grid
- One Off Commitments Grid
- Current Month Snapshot
- Payment Classification
- Configuration Page

### Delivery Priority (Grid-First)
- Priority 1: Monthly Commitments Grid
- Priority 2: One Off Commitments Grid
- Priority 3: Current Month Snapshot
- Priority 4: Payment Classification
- Priority 5: Configuration Page
- Priority 6: Planner Dashboard polish

Rationale:
- The two grid screens are the operational data-entry and correction surface.
- Current Month Snapshot is a derived view and should consume normalized, categorized, sign-correct, and month-scoped grid data.
- Backlog and ops generation should treat Snapshot criteria as dependent on grid contracts.

### Zachman Cell Mapping Summary
- Planner Dashboard: Row 5 How (UI composition and navigation), Row 5 Who (planner usage), Row 6 When (day-to-day operation)
- Current Month Snapshot: Row 5 What (monthly balances and commitment totals), Row 5 How (on-screen calculations), Row 5 When (current month cut)
- Monthly Commitments Grid: Row 5 What (month-scoped commitments), Row 5 How (filter and presentation behavior), Row 5 Who (planner review)
- One Off Commitments Grid: Row 5 What (non-recurring commitments), Row 5 How (sign conventions and display), Row 5 When (month-specific planning)
- Payment Classification: Row 5 How (import pipeline interaction), Row 5 What (preview rows, duplicates, categories), Row 5 Who (planner accept/reject)
- Configuration Page: Row 5 Why (planning consistency), Row 5 What (rules/dictionaries/defaults), Row 5 How (configuration controls)

### Screen 1: Planner Dashboard

Mapped cells:
- Row 5 Why: central planning surface for household finance control and visibility
- Row 5 How: top-level screen composition and navigation to planner work areas
- Row 5 Who: planner persona as the primary actor
- Row 6 When: used during ongoing planning and review cycles

Behavior contract:
- Provides a top-level control surface with key totals and entry points.
- Contains Current Month Snapshot and links/sections for commitments, classification, and configuration.
- Planner workspace uses tabbed navigation to separate Forecast/Current Month, Monthly Commitments, Once Off Commitments, and Transaction Import.
- Uses full-window layout of the planner shell.
- Preserves boxed-card/table containment and readable financial working presentation.

### Screen 2: Current Month Snapshot

Mapped cells:
- Row 5 What: opening balances, visible commitment totals, and end-of-period position
- Row 5 How: formula-driven monthly calculations rendered on screen
- Row 5 When: current month selection and timing semantics
- Row 6 Why: operational trust through explicit financial working

Behavior contract:
- Forecast period supports This month.
- Uses Monthly Commitments Grid and One Off Commitments Grid as source inputs for visible commitment totals.
- This month filtering includes commitments where dueDate equals the current month label (for example Jul26).
- Recurring commitments are excluded from This month because they are treated as already represented elsewhere.
- End-of-period total is calculated on screen as opening account balances + total incomes + total expenses.
- Total incomes are the sum of positive visible commitment amounts.
- Total expenses are the sum of negative visible commitment amounts.
- Forecast presentation is ledger/trial-balance style with explicit working rather than summary-only values.

### Screen 3: Monthly Commitments Grid

Mapped cells:
- Row 5 What: month-scoped commitment records and categories
- Row 5 How: month filter application and grid rendering rules
- Row 5 Who: planner review and reconciliation workflow

Persistence contract:
- Writes to monthly_commitments.
- Requires day_of_month for each monthly payment definition.
- Does not directly write ACTUAL transactions.

Behavior contract:
- Monthly Commitments Grid is an editable planner grid for recurring monthly household payments.
- Each row represents one monthly payment commitment.
- Grid columns include at minimum: payment description, category, and amount.
- Grid includes recurring schedule context (day_of_month) when available.
- Planner can edit payment description, category, and amount inline.
- Planner can create new recurring rows and delete existing recurring rows in-grid.
- Save updates are persisted through planner commitment upsert endpoints with dueDate fixed to Recurring.
- Rows remain visually boxed and contained within their card/table layout.
- Category is displayed per commitment, including inferred category when source category is missing.
- Recurring commitments remain definitions only; monthly conversion into transaction instances is performed by a monthly batch job outside this grid.

Minimum readiness criteria before Snapshot backlog items:
- Month filter behavior is deterministic and testable.
- Category output is present for every row (source or inferred, otherwise Uncategorized).
- Amount sign semantics are validated for incomes vs expenses.

Acceptance criteria:
- Planner can add, edit, and save monthly commitment rows without leaving the grid.
- Every visible row displays payment description, category, and amount.
- Amount field validates numeric entry and preserves financial sign conventions.
- Category can be selected/updated for each row; unresolved category falls back to Uncategorized.
- Saved grid changes are reflected in data used by Current Month Snapshot.

### Screen 4: One Off Commitments Grid

Mapped cells:
- Row 5 What: non-recurring commitment set for the selected planning period
- Row 5 How: sign-consistent financial display and grid behavior
- Row 5 When: month-specific planning horizon

Persistence contract:
- Writes to transactions where txn_type = ONE_OFF_FUTURE.
- Each row is a dated one-time transaction instance that does not recur.
- Rows may be edited until converted to actual posted outcomes.

Behavior contract:
- One Off Commitments Grid is an editable planner grid for one-time household payments that do not recur.
- Each row represents one one-off payment commitment.
- Grid columns include at minimum: payment description, category, and amount.
- Planner can edit payment description, category, and amount inline.
- Planner can add and delete one-off rows in-grid.
- Planner can save all pending one-off row edits at page level in a single action.
- One-off rows include category values.
- Expense values are represented with negative signs for financial consistency.
- Due month label is required for one-off rows and uses month token format (for example Jul26).
- Grid formatting follows row boxing/containment rules used by planner tables.

Minimum readiness criteria before Snapshot backlog items:
- One-off inclusion/exclusion rules are explicit and testable.
- Row amounts and categories are complete enough to be aggregated.
- Duplicate or ambiguous one-off rows are visible for planner correction.

Acceptance criteria:
- Planner can add, edit, and save one-off commitment rows without leaving the grid.
- Every visible row displays payment description, category, and amount.
- One-off rows are not treated as recurring commitments in downstream calculations.
- Amount field validates numeric entry and preserves financial sign conventions.
- Saved grid changes are reflected in data used by Current Month Snapshot.

### Screen 5: Payment Classification

Mapped cells:
- Row 5 How: preview, select, and accept workflow for transaction import
- Row 5 What: imported rows, duplicate flags, and category assignment outputs
- Row 5 Who: planner as classification decision maker

Behavior contract:
- Planner can paste CSV input for preview.
- Preview request posts CSV text to import preview endpoint.
- Preview list supports row-level selection and duplicate visibility.
- Accept action sends selected row IDs for import acceptance.
- Missing category values are inferred from title mapping where possible; unresolved values remain Uncategorized.

### Screen 6: Configuration Page

Mapped cells:
- Row 5 Why: maintain consistency and reduce classification ambiguity
- Row 5 What: category dictionary, fallback rules, and planner defaults
- Row 5 How: configuration and policy management controls

Behavior contract:
- Manages planning configuration and defaults that influence classification and planner calculations.
- Initial configuration scope includes category dictionary and fallback mapping rules, forecast period defaults and display preferences, and import/classification behavior options.
- Configuration capabilities are partially implemented via code-level defaults; UI controls may be staged incrementally.

## Planner Screen Change Log

- Split data model intent from a single commitments table to monthly_commitments plus transactions, with explicit transaction typing for actual, generated monthly future, and one-off future entries.
- Prioritized screen delivery in grid-first order so Monthly Commitments Grid and One Off Commitments Grid are upstream dependencies for Current Month Snapshot.
- Added explicit input dependency from Current Month Snapshot to both commitment grids.
- Reframed planner specification from a single workspace description into six explicit screens.
- Established initial boundaries for Planner Dashboard, Current Month Snapshot, Monthly Commitments Grid, One Off Commitments Grid, Payment Classification, and Configuration Page.
- Added forecast selector with `This month` period.
- Added ledger/trial-balance style forecast working panel.
- Added category rollup based on summed visible commitments.
- Added end-of-period total derived from account opening balances and filtered commitment totals.
- Added fallback category inference for commitments with missing category values.
- Excluded recurring commitments from `This month` calculations.
- Sorted category rollup by amount descending.
- Updated planner table styling so rows remain boxed and inside visual card boundaries.
- Updated planner layout to stretch and use full window width.

## Migration backlog
- Inventory the existing workbook sheets, tabs, and formulas that must be preserved.
- Define the canonical data model for accounts, categories, commitments, transactions, and monthly summaries.
- Replace legacy commitments persistence with monthly_commitments plus transactions while preserving planner API behavior during transition.
- Add mapping rules for migrating recurring rows into monthly_commitments with day_of_month.
- Add mapping rules for migrating one-off/future rows into transactions with txn_type = ONE_OFF_FUTURE.
- Add generation rules for MONTHLY_INSTANCE_FUTURE rows from monthly_commitments.
- Create a spreadsheet export step that produces structured CSV or JSON files for migration.
- Build an import workflow that maps spreadsheet data into the new domain model.
- Add a review-and-accept step so imported rows can be validated before becoming live data.
- Reconcile migrated balances and commitments against the spreadsheet baseline.
- Add a cutover plan that switches the household from spreadsheet use to the new system once confidence is high.

## Managing drift while development is ongoing
- Keep the spreadsheet as the source of truth until the new system has been validated.
- Treat the migration as a controlled, reviewable import rather than a one-time copy.
- Maintain a small list of spreadsheet-derived rules that must be preserved in the new system.
- Reconcile the new app with the spreadsheet at regular checkpoints during development.
- If a rule is ambiguous, capture it explicitly in the spec rather than silently implementing a guess.
- Use the migration backlog as the place to record any differences discovered between the old workbook and the new system.
