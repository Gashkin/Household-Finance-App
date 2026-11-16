---
type: Service
title: Workspace Recoverability
description: Transaction admission keeps committed application records within the complete encrypted backup budget.
resource: backend/src/main/java/com/householdfinance/workspace/WorkspaceMutationGuard.java
tags: [okf, workspace, backup, integrity]
timestamp: 2026-11-16T00:00:00Z
---

# Workspace Recoverability

## Boundary
Spring Boot registers `WorkspaceMutationGuard` as a transaction execution listener.
Every new application-managed read-write transaction acquires one fair process-local
lock before the database transaction begins. Participating calls share that root
transaction. Read-only transactions do not reserve write capacity.

A before-commit synchronization flushes pending JPA changes and validates the
entire database using the same fixed table manifest, per-table row limit,
conservative byte accounting and encoded-size limit as encrypted export. A failed
check rolls back the transaction, including related tables. Admission is released
after commit or rollback, including failures. Concurrent writes cannot independently
reserve the same remaining backup capacity. Existing plan-specific checks remain
compatible with this wider guard.

This is a single-process local application boundary. It does not police external
SQL tools, database-file tampering, other application processes, administrator access
or production identity isolation. It neither increases the archive budget nor prunes
accepted data. A download does not free database capacity. Independent release review
and actual-host/provider evidence remain required by the live Notion story.

## Verification
- `WorkspaceCapacityTest`: non-plan growth rejection, full rollback, concurrent
  admission, progress after a rejection and complete encrypted recovery.
- Existing plan journey tests continue to verify their history-growth and
  concurrent-plan boundaries.

## Linked Concepts
- [Transaction Import and Review](../workflows/transaction-import-and-review.md)
- [Service Boundaries](service-boundaries.md)
- [Production assessment evidence](../../../product/stories/production-security-assessment.md)
