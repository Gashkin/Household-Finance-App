---
type: Workflow
title: Transaction Import and Review Workflow
description: CSV import preview, dedupe review, acceptance, suggested matching, and optional mapping to future commitments.
resource: backend/src/main/java/com/householdfinance/service/TransactionImportService.java
tags: [okf, workflow, import, reconciliation]
timestamp: 2026-07-27T00:00:00Z
---

# Transaction Import and Review Workflow

## Steps
1. Import CSV content to preview rows.
2. Normalize and parse date, description, amount, account.
3. Mark row-level duplicates using dedupe key.
4. Accept selected rows into transaction history.
5. Surface suggested ONE_OFF_FUTURE commitment matches for unmapped rows.
6. Optionally map accepted history rows to ONE_OFF_FUTURE commitments (manual select or one-click apply suggestion).

## Inputs
- CSV content
- accepted row id list
- history id to future commitment id mapping

## Outputs
- ImportPreview
- ImportAcceptResult
- Transaction history entries
- Suggested match candidate per unmapped history row (UI-computed)

## Linked Concepts
- [Transactions](../domain/transactions.md)
- [Commitments](../domain/commitments.md)
- [Domain Invariants and Calculations](../rules/domain-invariants-and-calculations.md)

## Source Anchors
- [TransactionImportService](../../../backend/src/main/java/com/householdfinance/service/TransactionImportService.java)
- [Data Flow](../../../ops/pass1/data-flow.md)
