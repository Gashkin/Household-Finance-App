---
type: Metric
title: Monthly Summary
description: Monthly overview projection derived from account balances, commitments, and observed transactions.
resource: backend/src/main/java/com/householdfinance/service/MonthlySummaryService.java
tags: [okf, metric, monthly-summary, forecast]
timestamp: 2026-07-27T00:00:00Z
---

# Monthly Summary

## Output Fields
- month
- startingBalance
- actualInflow
- actualOutflow
- committedOutgoings
- projectedEndBalance
- allowableSpend
- recentTransactions

## Calculation Outline
- startingBalance: sum of account balances
- committedOutgoings: sum of planner commitments
- projectedEndBalance: startingBalance + inflow - outflow - committedOutgoings
- allowableSpend: max(0, projectedEndBalance)

## Linked Concepts
- [Accounts](accounts.md)
- [Commitments](commitments.md)
- [Domain Invariants and Calculations](../rules/domain-invariants-and-calculations.md)

## Source Anchors
- [MonthlySummaryService](../../../backend/src/main/java/com/householdfinance/service/MonthlySummaryService.java)
- [Planner Service](../../../backend/src/main/java/com/householdfinance/service/PlannerService.java)
- [Domain Rules](../../../ops/pass1/domain-rules.md)
