---
type: Workflow
title: Workbook Ingestion Workflow
description: Workbook preview, mapping, validation, execution, and post-load summary flow.
resource: backend/src/main/java/com/householdfinance/service/WorkbookIngestionService.java
tags: [okf, workflow, workbook, ingestion]
timestamp: 2026-07-27T00:00:00Z
---

# Workbook Ingestion Workflow

## Steps
1. Preview workbook sheets and sample rows.
2. Map sheets into accounts, categories, commitments, and transactions.
3. Validate mapped results and produce confirmation token.
4. Execute merge or replace load after validation.
5. Emit post-load summary and rejected transaction rows.

## Constraints
- Transactions from workbook mapping are currently skipped for persistence in this phase.
- Replace mode clears planner persistence tables before apply.

## Linked Concepts
- [Accounts](../domain/accounts.md)
- [Categories](../domain/categories.md)
- [Commitments](../domain/commitments.md)

## Source Anchors
- [WorkbookIngestionService](../../../backend/src/main/java/com/householdfinance/service/WorkbookIngestionService.java)
- [Ordered Backlog](../../../backlog/ordered-backlog.md)
