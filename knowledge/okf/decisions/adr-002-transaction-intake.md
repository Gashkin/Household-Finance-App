---
type: ArchitectureDecision
title: ADR-002 Transaction Intake Approach
description: Defines file-based transaction intake with normalization, deduplication, and review before acceptance.
resource: adr/ADR-002.md
tags: [okf, adr, import, intake]
timestamp: 2026-07-27T00:00:00Z
---

# ADR-002 Transaction Intake Approach

## Decision
Support transaction import from CSV or spreadsheet-style files with normalization, deduplication, and review before data acceptance.

## Constraints
- First release does not require direct bank API connectivity or automated feeds.

## Rules Out
- Fully automatic bank synchronization in v1.

## Linked Concepts
- [Transaction Import and Review Workflow](../workflows/transaction-import-and-review.md)
- [Transactions](../domain/transactions.md)

## Source Anchors
- [ADR-002](../../../adr/ADR-002.md)
- [TransactionImportService](../../../backend/src/main/java/com/householdfinance/service/TransactionImportService.java)
