---
type: Rule
title: Domain Invariants and Calculations
description: Core invariants and calculation rules for planner, ledger, and consumer views.
resource: ops/pass1/domain-rules.md
tags: [okf, rules, invariants, calculations]
timestamp: 2026-07-27T00:00:00Z
---

# Domain Invariants and Calculations

## Invariants
- Commitment data must remain attributable to account/category context in planner logic.
- Transactions must reconcile to account and date semantics.
- Consumer view is read-only.
- Imported rows must be reviewed before ledger acceptance.
- Recurring commitments persist as monthly definitions with day_of_month.
- One-off future entries persist as dated transactions with txn_type = ONE_OFF_FUTURE.
- Snapshot calculations aggregate transaction instances.

## Calculation Rules
- Current balance derives from account activity and commitments.
- Allowable spend derives from planned income, committed outgoings, and available balance.
- Planned-vs-actual reconciliation compares imported transactions and planned commitments.
- Current month snapshot totals use transactions in period window.

## Linked Concepts
- [Monthly Summary](../domain/monthly-summary.md)
- [Transactions](../domain/transactions.md)
- [Planner CRUD Workflow](../workflows/planner-crud.md)

## Source Anchors
- [Domain Rules](../../../ops/pass1/domain-rules.md)
- [Zachman](../../../spec/zachman.md)
