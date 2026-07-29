---
type: Service
title: Service Boundaries
description: Defines core services, ownership boundaries, and data ownership detail.
resource: ops/pass1/service-boundaries.md
tags: [okf, services, ownership]
timestamp: 2026-07-27T00:00:00Z
---

# Service Boundaries

## Core Services
- Planner workspace service
- Consumer reporting service
- Import service
- Calculation service

## Ownership
- Planner service owns editable financial data.
- Consumer service owns read-only reporting views.
- Import service owns imported transaction processing.
- Calculation service owns derived financial metrics.

## Data Ownership
- Planner owns monthly_commitments recurring definitions.
- Planner and import services write transaction instances according to flow responsibility.
- Calculation reads transactions for snapshot and period aggregates.

## Linked Concepts
- [Planner CRUD Workflow](../workflows/planner-crud.md)
- [Transaction Import and Review Workflow](../workflows/transaction-import-and-review.md)
- [Domain Invariants and Calculations](../rules/domain-invariants-and-calculations.md)

## Source Anchors
- [Service Boundaries](../../../ops/pass1/service-boundaries.md)
