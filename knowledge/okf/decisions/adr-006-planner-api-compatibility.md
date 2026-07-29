---
type: ArchitectureDecision
title: ADR-006 Planner API Compatibility During Migration
description: Maintains planner commitment API behavior while persistence internals migrate.
resource: adr/ADR-006.md
tags: [okf, adr, api, migration]
timestamp: 2026-07-27T00:00:00Z
---

# ADR-006 Planner API Compatibility During Migration

## Decision
Keep planner API behavior and commitment endpoint contract stable while storage routes to monthly_commitments and transactions internally.

## Constraints
- API compatibility takes precedence over immediate redesign.
- Any redesign must be additive and versioned.

## Linked Concepts
- [Commitments](../domain/commitments.md)
- [Planner CRUD Workflow](../workflows/planner-crud.md)
- [ADR-005 Commitments Persistence Split](adr-005-commitments-split.md)

## Source Anchors
- [ADR-006](../../../adr/ADR-006.md)
