---
type: Runbook
title: UI Component Patterns
description: Planner and consumer UI composition patterns and interaction constraints.
resource: ops/pass3/component-patterns.md
tags: [okf, ui, patterns]
timestamp: 2026-07-27T00:00:00Z
---

# UI Component Patterns

## Planner Patterns
- Tabbed workflow: Forecast and Current Month, Monthly Commitments, Once Off Commitments, Account Management.
- Grid-first editing for recurring and one-off commitments with inline CRUD.
- CSV bulk-edit support for both commitment grids.
- Account Management includes import preview/accept, history, and manual mapping.

## Forecast Patterns
- Credit Card Repayment affordability card is explicit and visible.
- Left column stacks Accounts and Categories, middle column is Credit Card Repayment, right column is Commitments.

## Consumer Pattern
- Consumer dashboard uses summary cards and simple charts.

## Linked Concepts
- [Planner CRUD Workflow](../workflows/planner-crud.md)
- [Transaction Import and Review Workflow](../workflows/transaction-import-and-review.md)

## Source Anchors
- [Component Patterns](../../../ops/pass3/component-patterns.md)
