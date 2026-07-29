---
type: Workflow
title: Planner CRUD Workflow
description: End-to-end planner workflow for maintaining accounts, categories, and commitments.
resource: backend/src/main/java/com/householdfinance/service/PlannerService.java
tags: [okf, workflow, planner, crud]
timestamp: 2026-07-27T00:00:00Z
---

# Planner CRUD Workflow

## Steps
1. Planner requests current planner data.
2. Planner creates/updates/deletes accounts and categories.
3. Planner creates/updates/deletes commitments.
4. Service routes recurring and one-off commitments to the correct persistence model.
5. Updated planner data is returned for rendering and forecast calculations.

## Inputs
- AccountUpsertRequest
- CategoryUpsertRequest
- CommitmentUpsertRequest

## Outputs
- PlannerData
- updated Account, Category, Commitment records

## Linked Concepts
- [Commitments](../domain/commitments.md)
- [Monthly Commitments](../domain/monthly-commitments.md)
- [Transactions](../domain/transactions.md)

## Source Anchors
- [PlannerService](../../../backend/src/main/java/com/householdfinance/service/PlannerService.java)
- [Data Flow](../../../ops/pass1/data-flow.md)
