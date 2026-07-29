---
type: Rule
title: Error Handling Rules
description: Handling rules for import validation, calculations, access checks, and conflict review.
resource: ops/pass2/error-handling.md
tags: [okf, rules, errors]
timestamp: 2026-07-27T00:00:00Z
---

# Error Handling Rules

## Rules
- Validation errors during import are surfaced before acceptance.
- Calculation errors fail safely with clear messages.
- Access violations are blocked at service boundaries.
- Import conflicts are marked for review, never silently resolved.

## Linked Concepts
- [Transaction Import and Review Workflow](../workflows/transaction-import-and-review.md)
- [Service Boundaries](../services/service-boundaries.md)

## Source Anchors
- [Error Handling](../../../ops/pass2/error-handling.md)
