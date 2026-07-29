---
type: DomainEntity
title: Commitments
description: Planner commitment view combining recurring monthly definitions and future one-off transactions.
resource: backend/src/main/java/com/householdfinance/service/PlannerService.java
tags: [okf, domain, commitments, planner]
timestamp: 2026-07-27T00:00:00Z
---

# Commitments

## Concept
The planner-facing commitment model is a unified API abstraction across two persistence targets:
- recurring rows in monthly_commitments
- dated one-off rows in transactions

## Fields
- id
- title
- amount
- dueDate
- category
- dayOfMonth (for recurring rows)

## Constraints
- dueDate accepts Recurring or an explicit date.
- Contract is stable during migration to split persistence model.

## Linked Concepts
- [Monthly Commitments](monthly-commitments.md)
- [Transactions](transactions.md)
- [Planner CRUD Workflow](../workflows/planner-crud.md)
- [ADR-006 Planner API Compatibility](../decisions/adr-006-planner-api-compatibility.md)

## Source Anchors
- [Planner Service](../../../backend/src/main/java/com/householdfinance/service/PlannerService.java)
- [Ordered Backlog](../../../backlog/ordered-backlog.md)
- [Zachman](../../../spec/zachman.md)
