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
- Data layer: a relational database such as PostgreSQL for the first version, with tables for accounts, categories, commitments, transactions, imports, and user roles.
- Import pipeline: file ingestion from CSV or spreadsheet-style exports, field mapping, duplicate detection, review queue, and acceptance into the ledger.
- Access model: the planner role can create and edit financial data; the consumer role can view reports and summaries only.
- Deployment shape: a browser-based application hosted locally on a household machine or small home server, with local-first data handling and optional future cloud or backup enhancements.

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

## Migration backlog
- Inventory the existing workbook sheets, tabs, and formulas that must be preserved.
- Define the canonical data model for accounts, categories, commitments, transactions, and monthly summaries.
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
