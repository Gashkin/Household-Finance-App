# Operational discovery: household money to financial decisions

Status: Evidence-based initial discovery; HBM interaction details still to validate.

## Source boundaries

- Household Finance product direction (merged PR #4): identifies HBM expense management and Financial Planner review as primary operational inputs.
- Financial Planning — Project Workstreams (Notion): records evidence-first pension, debt, mortgage, insurance and energy-upgrade investigations, and explicit approval boundaries.
- Product Build workspace: separates household operations from product engineering.

This document distinguishes **observed** operational patterns from **hypotheses**. It does not turn existing software screens into product requirements.

## Journey A — Maintain a reliable household operating picture

**Trigger:** Regular household money management and changing commitments.

1. **Capture** income, spending, recurring commitments and irregular costs. [Observed at workflow-category level from HBM product-direction evidence; exact interaction steps unverified.]
2. **Organise** transactions and obligations into a useful household view. [Observed at workflow-category level.]
3. **Review** what changed, what needs attention and whether the current position is reliable. [Observed at workflow-category level.]
4. **Understand availability** of cash after commitments and expected irregular spending. [Product hypothesis requiring validation.]
5. **Feed planning** with dated, traceable cash-flow facts rather than manual duplicate entry. [Product hypothesis.]

**Desired outcome:** A household can answer what money is committed, what is available, and how confident it should be in those numbers.

**Unknowns:** Current HBM import methods; reconciliation mechanics; household member responsibilities; category and account structures; treatment of cash, transfers, refunds and irregular bills; how exceptions are corrected.

## Journey B — Investigate and decide a meaningful financial change

**Trigger:** A benefit opportunity, higher-cost debt, renewal, coverage review or home-investment option.

1. **Frame the decision.** Example: pension AVC opportunity versus higher-cost debt repayment. [Observed in household workstream priorities.]
2. **Collect dated evidence.** Statements, scheme rules, provider responses and actual cash-flow figures. [Observed.]
3. **Separate certainty.** Confirmed facts, assumptions, estimates and unanswered questions. [Observed.]
4. **Compare options.** Affordability, liquidity, cost, benefits, constraints and downside risk. [Observed.]
5. **Record recommendation and decision separately.** [Observed.]
6. **Require explicit authorisation** before payroll, policy, payment or investment changes. [Observed.]
7. **Follow up and verify** execution with a payslip, lender confirmation or revised statement. [Observed.]
8. **Revisit** when evidence or circumstances change. [Observed.]

**Desired outcome:** A decision is explainable and traceable, with missing evidence visible and no accidental financial action.

**Unknowns:** How the existing board represents dependencies, exceptions, multiple reviewers and evidence revisions; frequency of review; what is most painful to maintain.

## Cross-journey synthesis

| Need | Evidence | Initial product hypothesis |
| --- | --- | --- |
| Reliable baseline | HBM handles expense/commitment review | Typed household facts with source and as-at date |
| Traceability | Planning workstreams demand statements and written confirmation | Evidence links on material facts and decisions |
| Honest uncertainty | Confirmed vs assumed vs open distinction | First-class verification state, never fake precision |
| Connected priorities | Pension, loan, mortgage and cash liquidity compete | Compare scenarios against a shared baseline |
| Safe execution | Financial actions need explicit approval | Product records decisions but never executes money movement by default |
| Review continuity | Provider confirmations and later verification are tracked | Follow-ups and evidence freshness are part of the workflow |

## Candidate first journey

**Compare an extra debt repayment with retaining household cash.**

Why: bounded financial arithmetic, explicit liquidity trade-off, clear source requirements and no mandatory LLM call. This is a hypothesis pending fuller HBM workflow inspection.

Minimum input: balance, annual rate, regular payment, extra payment amount, current liquid cash, minimum cash reserve, and evidence/assumption status.

Minimum output: revised payoff schedule and interest estimate, impact on cash reserve, comparison with baseline, assumptions and warnings. Scenarios must never overwrite source facts.

## Verification and next research

- Inspect HBM operational artifacts and actual repository workflows, not just the product vision.
- Validate one concrete end-to-end transaction-to-cashflow example and one planning decision-to-follow-up example.
- Identify which fields and steps are household-specific and which generalise.
- Do not mark the discovery story Done until two evidence-linked workflows and open questions are verified.
