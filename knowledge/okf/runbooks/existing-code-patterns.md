---
type: Runbook
title: Existing Code Patterns
description: Legacy workbook and model patterns that must be preserved during changes.
resource: spec/archaeology/existing-patterns.md
tags: [okf, archaeology, patterns]
timestamp: 2026-07-27T00:00:00Z
---

# Existing Code Patterns

## Must Follow
- Sheet-per-period budgeting model.
- Formula-visible aggregation style.
- Stable category-based budgeting vocabulary.

## Must Not Introduce
- Database-first model that discards monthly sheet structure.
- Hidden logic not reflected in visible formulas.
- Untracked category proliferation.

## Linked Concepts
- [Service Boundaries](../services/service-boundaries.md)
- [UI Component Patterns](ui-component-patterns.md)

## Source Anchors
- [Existing Patterns](../../../spec/archaeology/existing-patterns.md)
