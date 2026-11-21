---
type: Workflow
title: Savings Goals and Irregular Costs
description: Explicit cash reservations and contribution choices with single linked bill expectations.
resource: knowledge/okf/workflows/savings-goals.md
tags: [okf, goals, savings, commitments, budgets]
timestamp: 2026-11-20T00:00:00Z
---

# Savings goals and irregular costs

Editors maintain owner-scoped goal targets, dates, priorities, reserved existing cash, planned monthly contributions and optional owned savings accounts. Reservations and contributions create no assets, transactions or budget mutations. Existing cash is counted once; pending debits reduce it once. Competing reservations require global and linked-account cash backing on increases; later shortfalls warn while deliberate releases remain possible.

An annual or irregular bill goal owns one dated commitment expectation. Revision-checked cost/date changes and cancellation update goal and expectation atomically. Existing explicit actual matching changes the forecast once, preserves booked facts and exposes cost variance without rewriting budgets. Matched/overridden/past bill facts cannot be replaced by a goal edit. Savings targets are allocations rather than additional cash expenses.

Goal state, idempotent operation receipts and actor/reason history use the existing ledger review records, transaction-lifetime write admission and version-7 encrypted recovery tables. Unknown response handling checks operation status before any new save. Owner access and account scope follow budgets and commitments; access loss clears private UI state.

Approved matched bills release derived remaining funding without erasing original decisions; explicit unlink restores unpaid reservations. Existing owned retired categories remain usable for funding-only edits. Semantic changes in linked schedules/occurrences flag retained targets as illustrative and preserve the external change. Cancellation combined with changed bill details rejects atomically; unchanged cancellation/reactivation preserves matching goal/schedule facts and one future expectation. These interactions must survive restart and encrypted restore with payments, balances and audits preserved.

## Linked concepts

- [Budget allocations](budget-allocations.md)
- [Recurring bills and cash flow](recurring-bills.md)
- [Operator workflow and limitations](../../../product/stories/goals.md)

## Source anchors

- backend/src/main/java/com/householdfinance/ledger/GoalService.java
- frontend/src/components/GoalsWorkspace.tsx
- backend/src/test/java/com/householdfinance/ledger/GoalTest.java
- backend/src/test/java/com/householdfinance/workspace/GoalRecoveryTest.java
- frontend/e2e/goals.spec.ts
