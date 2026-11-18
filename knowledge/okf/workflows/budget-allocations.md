---
type: Workflow
title: Budget Allocations
description: Preserved period plans compared with reviewed ledger category actuals.
resource: knowledge/okf/workflows/budget-allocations.md
tags: [okf, budgets, allocations, ledger]
timestamp: 2026-11-18T00:00:00Z
---

# Budget allocations

Financial editors save reusable regular-income allocation templates and instantiate non-overlapping calendar or pay-day periods. The backend preserves original budgets separately from current allocations and derives actuals from confirmed booked, owned ledger activity. Linked refunds follow their own activity dates and the original purchase category. Allocations never create cash or bank movements.

Changes require an expected revision and reason. Selected/forward template application, accumulating/reset rollovers and explicit income changes preserve historical originals and actor-aware audit evidence. Carry is a creation-time snapshot; subsequent predecessor changes produce warnings. Authenticated owner scope applies to every read, mutation, history and report.

Templates/periods use the existing ledger review record store, version-7 encrypted backup manifest, transaction-lifetime mutation guard and capacity admission. Restart/restore must retain plans, originals and audit alongside reviewed actuals.

## Source anchors

- [Operator workflow and scope](../../../product/stories/budgets.md)
- backend/src/main/java/com/householdfinance/ledger/BudgetService.java
- backend/src/main/java/com/householdfinance/ledger/LedgerController.java
- frontend/src/components/BudgetWorkspace.tsx
- backend/src/test/java/com/householdfinance/ledger/BudgetTest.java
- backend/src/test/java/com/householdfinance/workspace/BudgetRecoveryTest.java
- frontend/e2e/budget.spec.ts
