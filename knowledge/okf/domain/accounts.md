---
type: DomainEntity
title: Accounts
description: Financial account records used as opening balance and reconciliation anchors.
resource: backend/src/main/java/com/householdfinance/entity/AccountEntity.java
tags: [okf, domain, accounts]
timestamp: 2026-07-27T00:00:00Z
---

# Accounts

## Schema
- id: string
- name: string
- type: string
- balance: number

## Usage
- Planner reads and updates accounts through planner APIs.
- Monthly summary uses account balances as starting balance input.

## Linked Concepts
- [Categories](categories.md)
- [Commitments](commitments.md)
- [Monthly Summary](monthly-summary.md)

## Source Anchors
- [AccountEntity](../../../backend/src/main/java/com/householdfinance/entity/AccountEntity.java)
- [Planner Service](../../../backend/src/main/java/com/householdfinance/service/PlannerService.java)
- [Zachman](../../../spec/zachman.md)
