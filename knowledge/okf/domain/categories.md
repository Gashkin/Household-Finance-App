---
type: DomainEntity
title: Categories
description: Budget categories used to classify commitments and transactions.
resource: backend/src/main/java/com/householdfinance/entity/CategoryEntity.java
tags: [okf, domain, categories]
timestamp: 2026-07-27T00:00:00Z
---

# Categories

## Schema
- id: string
- name: string
- budget: string

## Usage
- Planner maintains categories and budget labels.
- Commitments and transactions may carry a category reference.

## Linked Concepts
- [Accounts](accounts.md)
- [Commitments](commitments.md)
- [Transactions](transactions.md)

## Source Anchors
- [CategoryEntity](../../../backend/src/main/java/com/householdfinance/entity/CategoryEntity.java)
- [Planner Service](../../../backend/src/main/java/com/householdfinance/service/PlannerService.java)
- [Domain Rules](../../../ops/pass1/domain-rules.md)
