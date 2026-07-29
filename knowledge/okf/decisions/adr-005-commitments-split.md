---
type: ArchitectureDecision
title: ADR-005 Commitments Persistence Split
description: Splits recurring commitments and dated transaction instances into separate persistence models.
resource: adr/ADR-005.md
tags: [okf, adr, commitments, persistence]
timestamp: 2026-07-27T00:00:00Z
---

# ADR-005 Commitments Persistence Split

## Decision
Persist:
- recurring planner commitments in monthly_commitments with day_of_month
- dated financial instances in transactions with txn_type values ACTUAL, MONTHLY_INSTANCE_FUTURE, ONE_OFF_FUTURE

## Constraints
- Snapshot calculations aggregate from transactions.
- Monthly commitments grid writes only monthly definitions.
- One-off commitments grid writes only transactions with txn_type = ONE_OFF_FUTURE.

## Linked Concepts
- [Commitments](../domain/commitments.md)
- [Monthly Commitments](../domain/monthly-commitments.md)
- [Transactions](../domain/transactions.md)

## Source Anchors
- [ADR-005](../../../adr/ADR-005.md)
