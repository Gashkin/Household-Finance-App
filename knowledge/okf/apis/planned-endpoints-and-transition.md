---
type: API
title: Planned Endpoints and Transition Contract
description: Captures planned endpoint surface and migration-time planner API compatibility constraints.
resource: ops/pass3/api-contracts.md
tags: [okf, api, planner, migration]
timestamp: 2026-07-27T00:00:00Z
---

# Planned Endpoints and Transition Contract

## Planned Endpoints
- GET /accounts
- GET /commitments
- POST /commitments
- GET /transactions
- POST /imports
- GET /reports/monthly-overview

## Transition Contract
- /planner and planner commitment CRUD endpoints remain stable while storage migrates.
- Recurring payloads map to monthly_commitments.
- Month-labeled one-off payloads map to transactions with txn_type = ONE_OFF_FUTURE.
- Future API versioning should split recurring-definition endpoints from transaction-instance endpoints after frontend migration.

## Linked Concepts
- [ADR-006 Planner API Compatibility During Migration](../decisions/adr-006-planner-api-compatibility.md)
- [Commitments](../domain/commitments.md)
- [Transactions](../domain/transactions.md)

## Source Anchors
- [API Contracts](../../../ops/pass3/api-contracts.md)
- [PlannerController](../../../backend/src/main/java/com/householdfinance/controller/PlannerController.java)
- [TransactionImportController](../../../backend/src/main/java/com/householdfinance/controller/TransactionImportController.java)
