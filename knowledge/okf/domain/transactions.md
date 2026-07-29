---
type: DomainEntity
title: Transactions
description: Dated financial instances for actual and future commitment rows.
resource: backend/src/main/java/com/householdfinance/entity/TransactionEntity.java
tags: [okf, domain, transactions, ledger]
timestamp: 2026-07-27T00:00:00Z
---

# Transactions

## Schema
- id: string
- txnDate: local-date
- title: string
- amount: number
- category: string or null
- txnType: enum (ACTUAL, MONTHLY_INSTANCE_FUTURE, ONE_OFF_FUTURE)
- sourceMonthlyCommitmentId: string or null

## Constraints
- One-off commitments persist as transactions with txnType = ONE_OFF_FUTURE.
- Snapshot calculations aggregate transaction instances.

## Linked Concepts
- [Commitments](commitments.md)
- [Monthly Commitments](monthly-commitments.md)
- [Transaction Import and Review Workflow](../workflows/transaction-import-and-review.md)

## Source Anchors
- [TransactionEntity](../../../backend/src/main/java/com/householdfinance/entity/TransactionEntity.java)
- [TransactionType](../../../backend/src/main/java/com/householdfinance/entity/TransactionType.java)
- [ADR-005](../../../adr/ADR-005.md)
