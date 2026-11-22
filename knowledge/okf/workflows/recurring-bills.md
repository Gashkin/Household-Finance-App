---
type: Workflow
title: Recurring Bills and Cash Flow
description: Durable bill and payroll expectations matched explicitly to reviewed actuals and projected by account.
resource: knowledge/okf/workflows/recurring-bills.md
tags: [okf, commitments, payroll, forecast, ledger]
timestamp: 2026-11-18T00:00:00Z
---

# Recurring bills and cash flow

Financial editors maintain owner-scoped one-off, weekly, fortnightly, calendar-monthly and income-linked expectations in Plan ahead. Effective-dated versions retain past rules; nominal occurrence identity survives date shifts. Fixed/estimated amounts, overrides and payroll calendars remain forecast inputs, never fabricated ledger transactions. Explicit approved-actual/pending links prevent duplication against dated starting evidence; corrections require renewed review. Account risk and conservative uncertainty remain separate from household totals and [budget allocations](budget-allocations.md).

Atomic row/batch/CSV changes use revisions, durable operation identities, actor/reason audit and the existing workspace transaction/recoverability guard. The typed COMMITMENTS record is part of existing encrypted workspace backups. Prior dated/manual forecast plans remain intact in their own saved-plan view, with a twelve-calendar-month maximum horizon. Unsaved shortfall comparisons move no money and persist nothing.

See [operator workflow and verification](../../../product/stories/recurring-bills.md), [review workflow](transaction-import-and-review.md) and [workflow index](index.md).

## Source anchors

- backend/src/main/java/com/householdfinance/ledger/CommitmentService.java
- backend/src/main/java/com/householdfinance/forecast/CommitmentController.java
- frontend/src/components/PlanAheadWorkspace.tsx
- backend/src/test/java/com/householdfinance/ledger/CommitmentTest.java
- backend/src/test/java/com/householdfinance/workspace/CommitmentRecoveryTest.java
- frontend/e2e/commitments.spec.ts
