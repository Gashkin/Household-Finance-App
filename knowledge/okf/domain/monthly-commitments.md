---
type: DomainEntity
title: Monthly Commitments
description: Recurring payment definitions stored as monthly planner-owned commitments.
resource: backend/src/main/java/com/householdfinance/entity/MonthlyCommitmentEntity.java
tags: [okf, domain, recurring, commitments]
timestamp: 2026-07-27T00:00:00Z
---

# Monthly Commitments

## Schema
- id: string
- title: string
- amount: number
- dayOfMonth: integer
- category: string or null

## Constraints
- Represents recurring payment definitions only.
- Edits are performed through planner commitment APIs using dueDate = Recurring.

## Linked Concepts
- [Commitments](commitments.md)
- [Transactions](transactions.md)
- [ADR-005 Commitments Split](../decisions/adr-005-commitments-split.md)

## Source Anchors
- [MonthlyCommitmentEntity](../../../backend/src/main/java/com/householdfinance/entity/MonthlyCommitmentEntity.java)
- [Planner Service](../../../backend/src/main/java/com/householdfinance/service/PlannerService.java)
- [ADR-005](../../../adr/ADR-005.md)
