---
type: Runbook
title: Testing Strategy
description: Required testing levels and definition-of-done conditions.
resource: ops/pass2/testing-strategy.md
tags: [okf, testing, runbook]
timestamp: 2026-07-27T00:00:00Z
---

# Testing Strategy

## Levels
- Unit tests for calculations, normalization, and role checks.
- Integration tests for import workflow and persistence.
- UI tests for planner and consumer behavior.

## Definition of Done
- Required tests pass.
- Import review flow works end-to-end.
- Consumer view remains read-only.

## Linked Concepts
- [Domain Invariants and Calculations](../rules/domain-invariants-and-calculations.md)
- [Transaction Import and Review Workflow](../workflows/transaction-import-and-review.md)

## Source Anchors
- [Testing Strategy](../../../ops/pass2/testing-strategy.md)
