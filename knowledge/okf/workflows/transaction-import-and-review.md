---
type: Workflow
title: Transaction Import and Review Workflow
description: CSV import preview, dedupe review, acceptance, suggested matching, and optional mapping to future commitments.
resource: backend/src/main/java/com/householdfinance/service/TransactionImportService.java
tags: [okf, workflow, import, reconciliation]
timestamp: 2026-11-16T00:00:00Z
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

## Integrity and bounds
- Use the shared quoted CSV parser with the exact `date,description,amount,account` header, at most 100,000 characters and 500 complete rows.
- Reject invalid ISO dates, missing account/description, malformed or non-finite amounts and fractional cents; do not silently replace bad amounts with zero.
- Duplicate preview keys include account identity. Repeated selection, repeated acceptance and concurrent acceptance of the same import row create at most one history item.
- Save the acceptance receipt before publishing in-memory history. Resolve each history item at most once; commit its balance and commitment writes together before publishing resolution state.
- This legacy history cache remains process-local; it is not the durable reviewed ledger. Restarting does not restore the cache from backup. The durable manual/CSV/bank activity journey is documented in [Reviewed money position](../../../product/stories/reviewed-money-position.md).
- Database writes are also subject to [Workspace recoverability](../services/workspace-recoverability.md).

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
