---
type: ArchitectureDecision
title: ADR-001 Secondary Consumer Experience
description: Defines the simple read-only consumer monthly overview scope.
resource: adr/ADR-001.md
tags: [okf, adr, consumer, dashboard]
timestamp: 2026-07-27T00:00:00Z
---

# ADR-001 Secondary Consumer Experience

## Decision
Provide a monthly overview experience for the secondary consumer focused on:
- current balance
- upcoming commitments
- allowable spend

## Constraints
- Consumer view remains simple and read-only.
- Does not become a planner workflow in v1.

## Linked Concepts
- [Monthly Summary](../domain/monthly-summary.md)
- [Domain Invariants and Calculations](../rules/domain-invariants-and-calculations.md)

## Source Anchors
- [ADR-001](../../../adr/ADR-001.md)
