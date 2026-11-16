---
type: Workflow
title: Workbook Ingestion Workflow
description: Workbook preview, mapping, validation, execution, and post-load summary flow.
resource: backend/src/main/java/com/householdfinance/service/WorkbookIngestionService.java
tags: [okf, workflow, workbook, ingestion]
timestamp: 2026-11-16T00:00:00Z
---

# Workbook Ingestion Workflow

## Steps
1. Preview workbook sheets and sample rows.
2. Map sheets into accounts, categories, commitments, and transactions.
3. Validate mapped results and produce confirmation token.
4. Execute merge or replace load after validation.
5. Emit post-load summary and rejected transaction rows.

## Constraints
- Only OOXML XLSX up to 8 MiB is accepted. Shared preflight runs before preview, mapping and validation; ambiguous duplicate/case-equivalent ZIP parts, unsafe paths and external XML entities are rejected before POI.
- Intake bounds: 2,048 parts, 8 MiB per expanded part, 32 MiB total expanded content, 48 sheets, 10,000 rows per XML document, 256 columns, 100,000 cells, 200,000 XML nodes, depth 64 and 2,000 text characters per cell/shared-string item. Sparse row/cell references are checked, too. Preflight has a five-second deadline; this is not an absolute CPU/heap deadline for the later POI parser.
- `WorkbookUploadSecurityTest` exercises the actual HTTP/controller/parser path with synthetic duplicate parts, extreme sparse references, expansion and XML entity cases. Its existence is not evidence of a passing execution; consult story evidence.
- Transactions from workbook mapping are currently skipped for persistence in this phase.
- Replace mode clears planner persistence tables before apply.
- Legacy whole-planner rollback lacks a revision boundary and pending validations have no expiry. These remain explicit unresolved security findings in the [audit disposition](../../../product/stories/security-audit-disposition.md); no production approval is implied.

## Linked Concepts
- [Accounts](../domain/accounts.md)
- [Categories](../domain/categories.md)
- [Commitments](../domain/commitments.md)

## Source Anchors
- [WorkbookIngestionService](../../../backend/src/main/java/com/householdfinance/service/WorkbookIngestionService.java)
- [Ordered Backlog](../../../backlog/ordered-backlog.md)
