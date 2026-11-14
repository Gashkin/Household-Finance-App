# Household Financial Planning Platform

## Product vision

Help households understand their complete financial position, make informed decisions across competing priorities, and turn long-term family goals into practical, affordable plans.

This is not an expense tracker with a chatbot. It is a household financial operating system built around a trusted financial record, deterministic financial models, explicit goals and decisions, and an AI companion that makes the system easier to understand and use.

## Product discovery rule: greenfield functionality

This product is being designed from scratch at the functional level.

Existing repositories, boards, specifications, data models, backlogs, screens and workflows are reference material only. They may reveal useful domain knowledge, implementation patterns, constraints or reusable code, but they do not define the product, its feature set, user journeys, terminology, priorities or acceptance criteria.

Do not:
- Port existing features merely because they exist.
- Treat an existing backlog or board as the product roadmap.
- Preserve current workflows when a better household experience can be designed.
- Assume existing entities or service boundaries are the correct domain model.
- Let current technical limitations determine the product experience.

Instead, discover the actual household jobs, user needs, decisions, risks and desired outcomes. Design the coherent end-to-end product first. Then assess existing assets objectively for reuse, replacement or retirement. Reuse is an engineering optimisation, never a product requirement.

## Operational input: preserve the real household workflows as evidence

Greenfield functionality does not mean discarding the operational knowledge embedded in the existing Household Budget Manager (HBM) and Financial Planner board. These are important primary research inputs because they show how the household actually operates today.

Two workflows are especially valuable:

1. **Household expense management in HBM.** How expenses are captured, organised, reviewed and managed; how recurring commitments and irregular costs are understood; what needs attention; and how the household turns day-to-day financial activity into a reliable view of its position.
2. **Financial plan review in the Financial Planner board.** How the household reviews its financial plans, gathers and checks evidence, considers assumptions, evaluates options and trade-offs, records decisions, tracks follow-up actions and revisits plans as circumstances change.

Treat these workflows as observed operational behaviour and domain evidence—not as screens or feature specifications to clone. Extract the underlying jobs, decisions, information needs, hand-offs, pain points, workarounds, controls and successful outcomes. Preserve what genuinely works, challenge what creates friction, and redesign the experience where a more coherent product can do better.

The intended product should connect these two modes of household financial management: the ongoing operational reality of managing money, and the periodic/triggered work of reviewing and changing financial plans. They should not become disconnected applications or separate data silos. Actual household activity should inform planning; plans and decisions should in turn inform what the household monitors and manages.


## Human operational expertise and collaboration

The primary household operator is the operational user and subject-matter expert for the real family workflows. They are a source of operational evidence and clarification when needed—not the Product Owner, requirements analyst or solution designer.

The AI owns product discovery, synthesis, product decisions, roadmap, UX, architecture, engineering, testing and delivery. It should inspect available operational material and form its own hypotheses before asking questions. Ask the household SME only targeted questions where their lived experience, intent or tacit knowledge would materially change a product decision or resolve genuine ambiguity. Do not routinely transfer requirements definition, prioritisation or design decisions to them.

The SME may correct assumptions, explain exceptions, describe workarounds, identify friction and clarify why a current practice exists. Convert that input into generalisable product requirements while distinguishing:
- Household-specific policy, preference or circumstance.
- A broadly reusable household problem or workflow.
- A current workaround that should be improved rather than preserved.
- A regulatory, financial or operational constraint that must be respected.

Continue independently wherever evidence is sufficient. Make assumptions explicit where useful, validate them against available evidence, and revisit them when new operational input arrives.

## Product thesis

A household's financial decisions are connected. Debt repayment affects liquidity; liquidity affects pension contributions; pension contributions affect retirement outcomes and tax; mortgage choices compete with savings and home investment. Existing tools commonly expose fragments of this picture.

The product brings those fragments into one coherent household model and helps users understand the consequences of decisions before acting.

## Target customer and initial market

- Initial proving ground: a real family household with multiple income sources, dependants, a mortgage, consumer debt, employment-linked pension benefits, insurance and home-investment goals.
- Initial market hypothesis: households in Ireland, with euro currency, Irish income/tax/pension context and local financial product terminology. Validate this as a market choice; it is not a functional constraint.
- Expansion: other household types and jurisdictions through localised rules, tax parameters, data connectors and product configuration—not hard-coded country assumptions.

## Product discovery questions the product must answer

- What does a household need to know to feel in control of its finances?
- How do partners understand, agree and manage shared and individual finances?
- How should the product represent uncertainty, incomplete records and conflicting evidence?
- How do users express goals in life terms and translate them into financial plans?
- How should the product surface conflicts between goals, liquidity, risk and time?
- Which decisions need a guided workflow, and which are better served by a flexible workspace?
- What should the product proactively notice, and when should it stay quiet?
- How can users trust a calculation, recommendation or AI-generated explanation?
- What can be fully standardised across households, and what requires jurisdiction-specific rules or personal context?

## Candidate product capabilities

These are hypotheses to investigate and refine, not a fixed inherited feature list.

### Household financial picture
A coherent, explainable view of income, spending, cash, accounts, assets, debts, commitments, benefits and financial obligations. Every material value should have an owner, as-at date, source and confidence/verification state.

### Cash-flow and financial resilience
Understand what is coming in and going out, what is committed, what is genuinely available, how resilient the household is to shocks, and how the position may change over time.

### Family goals and life planning
Express goals in household language—security, education, home, experiences, flexibility, retirement—and connect them to funding, timing, trade-offs and measurable progress.

### Decision workspaces
Help a household investigate meaningful decisions such as debt repayment, pension contributions, mortgage choices, insurance adequacy, savings, home improvements and major purchases. Each workspace should adapt to the decision rather than forcing every problem into an identical form.

### Scenario and projection engine
Reproducible, versioned calculations for cash flow, debt amortisation, savings, mortgage repayment and retirement projections. Scenarios are isolated from the live baseline and never mutate source financial data. Inputs, assumptions, calculation version and outputs are retained for audit and comparison.

### Evidence and document intelligence
Import statements, payslips, policy schedules, pension documents, mortgage letters and spreadsheets. Extracted values remain proposed until reviewed or reconciled. Preserve source references, dates and extraction confidence.

### Household collaboration
Support appropriate visibility, ownership and decision-making between household members. Do not assume every household has the same structure or that every member should see or edit every record.

### Ongoing financial companion
Answer questions, explain changes, identify missing information, prepare reviews and help users revisit decisions as circumstances change. The companion must distinguish facts, estimates, assumptions and suggestions.

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

Standardise reusable primitives only after validating them against real household jobs and decision workflows. Likely shared concepts include:
- Household, person, ownership and permissions
- Financial account and instrument
- Income, expense, transaction and commitment
- Asset, liability and valuation
- Evidence source, observation date, confidence and verification state
- Goal, plan, assumption, scenario, projection and decision
- Review schedule, notification and audit event

A common planning lifecycle may be useful, but it must not become a rigid workflow imposed on every domain. Different decisions have different evidence needs, calculations, risks and approval steps.

Keep jurisdiction-specific rules, tax treatment, pension rules, product constraints and integrations behind versioned domain modules. Never assume Irish rules apply to another market.

## Architecture principles

Architecture should be selected and evolved to support the discovered product—not inherited from an existing repository.

Candidate principles:
- Responsive, accessible user experience.
- Explicit household/tenant security boundaries.
- Auditable financial records and reproducible calculations.
- Clear separation between source facts, derived values, assumptions and AI interpretations.
- Domain boundaries that reflect actual financial capabilities and change independently.
- Provider-neutral AI gateway with constrained tool execution.
- Reliable document ingestion and human review.
- Integration adapters for external providers, subject to legal, commercial and reliability requirements.
- Operational security, observability, backups, export, deletion and recovery.

Existing technical architecture may be reused where it meets these needs. Re-evaluate technology choices where product scale, security, deployment or user experience requires it. A local family deployment can be a proving ground, but it must not dictate the commercial architecture.

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

The initial product is a financial organisation, education, calculation and decision-support tool. It must not cross into regulated investment, insurance or mortgage product recommendations/intermediation by implication or by AI-generated wording.

Before enabling personalised product recommendations, arranging products, facilitating transactions or offering regulated advice, obtain specialist Irish/EU regulatory advice and determine the required authorisation, permissions, controls and operating model. Open-banking account access must also be assessed against applicable payment-services authorisation requirements and partner models.

Product language, feature gates, model prompts and user journeys must preserve this boundary. Disclaimers alone are not a substitute for analysing the actual service.

## Product development approach

1. Study the real HBM expense-management workflow and Financial Planner review workflow as operational research.
2. Extract the underlying household jobs, decisions, information, evidence, controls, friction and desired outcomes.
3. Discover adjacent needs and gaps that those current workflows do not address.
4. Design the end-to-end experience from scratch, connecting day-to-day money management with longer-term planning.
5. Identify the smallest coherent product that delivers meaningful household value.
6. Define the domain model, calculations, evidence and safety requirements needed for that experience.
7. Evaluate existing code and knowledge against the target; reuse, adapt, replace or discard each part deliberately.
8. Build and test complete user journeys with realistic data and edge cases.
9. Expand into broader household needs and markets based on validated value.

The roadmap is owned by product outcomes and dependencies, not by the structure of an inherited board or repository.

## Explicit non-goals for the initial product

- Autonomous transactions or movement of money.
- Unlicensed personalised investment, insurance or mortgage product recommendations.
- A general-purpose chatbot with unrestricted access to customer financial data.
- A complex marketplace or affiliate-led product funnel.
- Global tax and pension coverage at launch.
- Replacing qualified financial, tax or legal professionals.

## Product success test

A household can build a trustworthy picture of its finances, understand what is uncertain, compare realistic options against its actual constraints, agree priorities, and revisit decisions with the evidence and assumptions intact—without needing an AI model for the underlying numbers.

The product feels like one coherent system designed around how a family actually lives and makes decisions, not a collection of existing finance-app features joined together.
