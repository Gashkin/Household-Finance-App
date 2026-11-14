# Household Financial Planning Platform

## Product vision

Help households understand their complete financial position, make informed decisions across competing priorities, and turn long-term family goals into practical, affordable plans.

This is not an expense tracker with a chatbot. It is a household financial operating system built around a trusted financial record, deterministic financial models, explicit goals and decisions, and an AI companion that makes the system easier to understand and use.

## Product thesis

A household's financial decisions are connected. Debt repayment affects liquidity; liquidity affects pension contributions; pension contributions affect retirement outcomes and tax; mortgage choices compete with savings and home investment. Existing tools commonly expose fragments of this picture.

The product brings those fragments into one coherent household model and helps users understand the consequences of decisions before acting.

## Target customer and initial market

- Initial proving ground: one household with multiple income sources, dependants, a mortgage, consumer debt, employment-linked pension benefits, insurance and home-investment goals.
- Initial market: households in Ireland, with euro currency, Irish income/tax/pension context and local financial product terminology.
- Expansion: other household types and jurisdictions through localised rules, tax parameters, data connectors and product configuration—not hard-coded country assumptions.

## Core product experience

1. Establish a household financial baseline from manual entry, spreadsheets and source documents.
2. Reconcile income, spending, accounts, assets, liabilities, commitments and benefits.
3. Set household goals and priorities with target amounts, dates, constraints and dependencies.
4. Explore scenarios using deterministic financial calculations.
5. Record options, assumptions, evidence, risks, decisions and review dates.
6. Receive understandable, evidence-linked explanations and alerts about material changes.
7. Review progress periodically and update the plan as circumstances change.

## Core capabilities

### Household financial record
A multi-person household model covering members, income sources, accounts, transactions, recurring commitments, assets, liabilities, pensions, insurance, properties, benefits and supporting evidence. Every material value carries an as-at date, source, confidence/status and ownership.

### Budget and cash-flow
Budget periods, planned versus actual, recurring and one-off commitments, account-level balances, available-to-spend and multi-period cash-flow forecasts.

### Financial planning domains
- Family protection and insurance
- Debt strategy and repayment sequencing
- Mortgage strategy and affordability
- Pension and retirement planning
- Savings and emergency reserves
- Household tax and income treatment
- Property and home-energy investments
- Major purchases and life-event planning

Domains share a common planning lifecycle and data model, with specialist calculation modules where required.

### Scenario and projection engine
Reproducible, versioned calculations for cash flow, debt amortisation, savings, mortgage repayment and retirement projections. Scenarios are isolated from the live baseline and never mutate source financial data. Inputs, assumptions, calculation version and outputs are retained for audit and comparison.

### Evidence and document management
Import statements, payslips, policy schedules, pension documents, mortgage letters and spreadsheets. Extracted values remain proposed until reviewed or reconciled. Preserve source references and extraction confidence.

### Goals, decisions and review
Goals have measurable outcomes, target dates, priority, funding needs and dependencies. Plans record current position, options, assumptions, scenario results, risks, recommendation status, user decision, actions and next review. A recommendation is never silently treated as an authorised action.

## AI strategy

AI is an assistive layer, not the financial system of record and not the calculation authority.

Use AI for:
- Document classification and extraction into validated structured proposals.
- Natural-language interaction and explanation.
- Finding inconsistencies, missing information and stale evidence.
- Synthesising financial context and identifying questions worth investigating.
- Translating user questions into typed requests to approved calculation tools.
- Producing traceable summaries of scenarios and decisions.

Do not use AI for:
- Arithmetic, ledger balances, amortisation or projection calculations.
- Direct writes to financial facts without validation and user confirmation.
- Autonomous movement of money, changing policies, changing pension contributions or submitting applications.
- Unsupported claims that a recommendation is regulated financial advice or guaranteed to be suitable.

All model access is server-side through a provider-neutral gateway. Route tasks by capability and risk: deterministic rules first, lightweight models for extraction/classification, stronger reasoning models only for complex synthesis. Apply least-privilege context retrieval, tenant isolation, redaction, usage budgets, caching, structured output validation and model/version traceability. Core product workflows must remain usable when model access is unavailable.

## Standardisation and extensibility

Standardise the following shared primitives:
- Household, member, ownership and permissions
- Financial account and instrument
- Income, expense, transaction and commitment
- Asset, liability and valuation
- Evidence source, observation date, confidence and verification state
- Goal, plan, assumption, scenario, projection and decision
- Review schedule, notification and audit event

Standardise a planning lifecycle:
1. Define objective and constraints.
2. Establish current position and evidence.
3. Identify missing information.
4. Build options and assumptions.
5. Run reproducible scenarios.
6. Compare outcomes, affordability, liquidity and risks.
7. Present findings and recommendation status.
8. Capture the household's explicit decision.
9. Track actions and review triggers.

Keep jurisdiction-specific rules, tax treatment, pension rules, product constraints and integrations behind versioned domain modules. Never assume Irish rules apply to another market.

## Architecture direction

Retain and reuse the existing Household-Finance-App domain knowledge, budgeting concepts, import workflows and deterministic calculations where they are sound. Do not assume the current local-first architecture is sufficient for a commercial multi-household product.

Target architecture:
- Responsive web application.
- API-first backend with explicit household/tenant boundaries.
- Relational database with tenant-scoped access controls and auditable financial records.
- Domain services for ledger, cash flow, goals, debt, mortgage, pension and scenario calculations.
- Document ingestion pipeline with review queues.
- Provider-neutral AI gateway and constrained tool execution.
- Background jobs for imports, scheduled reviews and notifications.
- Integration adapters for bank data and external providers, added only where legal, commercial and reliability requirements are satisfied.

A private, local-first family deployment can remain a useful proving ground. Commercial multi-tenant hosting, secure identity, tenant isolation, backups, data export/deletion, operational monitoring and disaster recovery are required before broad public launch.

## Trust, privacy and safety

- Treat financial data as highly sensitive.
- Encrypt data in transit and at rest; isolate tenants and enforce authorisation server-side.
- Minimise data collection and model disclosure.
- Keep immutable audit events for important changes and decisions.
- Preserve provenance and distinguish confirmed, user-entered, imported, estimated and AI-extracted values.
- Make assumptions and uncertainty visible in projections.
- Never present forecasts as guarantees.
- Require explicit user confirmation for consequential changes.
- Provide data export and deletion mechanisms.
- Do not sell household financial data or use it for advertising.

## Regulatory product boundary

The initial product is a financial organisation, education, calculation and decision-support tool. It must not cross into regulated investment, insurance or mortgage intermediation/advice by implication or by AI-generated wording.

Before enabling personalised product recommendations, arranging products, facilitating transactions or offering regulated advice, obtain specialist Irish/EU regulatory advice and determine the required authorisation, permissions, controls and operating model. Open-banking account access must also be assessed against applicable payment-services authorisation requirements and partner models.

Product language, feature gates, model prompts and user journeys must preserve this boundary. Disclaimers alone are not a substitute for analysing the actual service.

## Product sequencing

### Foundation
- Establish a reliable household financial record and provenance model.
- Deliver reconciled budget, account positions, commitments and cash-flow forecasts.
- Deliver reproducible debt, mortgage and savings calculations.
- Support manual and spreadsheet/document-assisted onboarding.
- Implement household access control, audit and backup/export.

### Planning
- Deliver goals, priorities, planning lifecycle and review management.
- Add debt, mortgage, protection and pension planning domains.
- Add scenario comparison across competing household priorities.
- Add evidence-linked financial insights.

### AI assistance
- Add document extraction with review and reconciliation.
- Add conversational financial context and explanation.
- Add natural-language scenario requests routed to deterministic tools.
- Add proactive insight generation with clear evidence and user controls.

### Scale
- Add bank-data integrations through appropriately authorised providers.
- Add jurisdiction packs and configurable financial rules.
- Add secure multi-household SaaS operations, subscription management and customer support.
- Consider adviser collaboration only after the regulatory and commercial model is established.

## Explicit non-goals for the initial product

- Autonomous transactions or movement of money.
- Unlicensed personalised investment, insurance or mortgage product recommendations.
- A general-purpose chatbot with unrestricted access to customer financial data.
- A complex marketplace or affiliate-led product funnel.
- Global tax and pension coverage at launch.
- Replacing qualified financial, tax or legal professionals.

## Product success test

A household can build a trustworthy picture of its finances, understand what is uncertain, compare realistic options against its actual constraints, agree priorities, and revisit decisions with the evidence and assumptions intact—without needing an AI model for the underlying numbers.
