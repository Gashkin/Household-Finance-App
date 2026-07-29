---
type: ArchitectureDecision
title: ADR-004 Planner and Consumer Access Model
description: Defines planner edit permissions and consumer read-only reporting permissions.
resource: adr/ADR-004.md
tags: [okf, adr, access, roles]
timestamp: 2026-07-27T00:00:00Z
---

# ADR-004 Planner and Consumer Access Model

## Decision
Use separate roles with planner able to edit financial data and consumer restricted to read-only reporting views.

## Constraints
- Consumer interface remains simple and non-editable.

## Rules Out
- Shared editing permissions for consumer in v1.

## Linked Concepts
- [ADR-001 Secondary Consumer Experience](adr-001-consumer-experience.md)
- [Domain Invariants and Calculations](../rules/domain-invariants-and-calculations.md)

## Source Anchors
- [ADR-004](../../../adr/ADR-004.md)
- [Monthly Summary](../domain/monthly-summary.md)
