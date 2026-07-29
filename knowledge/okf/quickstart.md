---
type: Runbook
title: OKF Usage Quickstart
description: Day-to-day workflow for reading, updating, and validating the project OKF bundle.
resource: knowledge/okf/quickstart.md
tags: [okf, runbook, quickstart]
timestamp: 2026-07-27T00:00:00Z
---

# OKF Usage Quickstart

## Purpose
Use this guide for routine team work so project context stays consistent and agent-readable.

## Daily Read Flow
1. Start at [OKF Root Index](index.md).
2. Open one or more concept indexes based on task type:
   - Domain: [Domain Index](domain/index.md)
   - Workflows: [Workflow Index](workflows/index.md)
   - Rules: [Rules Index](rules/index.md)
   - Decisions: [Decisions Index](decisions/index.md)
   - API: [API Index](apis/index.md)
   - Work Items: [Work Items Index](work-items/index.md)
3. Follow links to concept files before consulting legacy docs.

## Updating Concepts
1. Edit the relevant concept file first.
2. Keep frontmatter complete:
   - type
   - title
   - description
   - resource
   - tags
   - timestamp
3. Add or update linked concepts when relationships change.
4. If source docs are used because context is missing, record the gap in [Migration Map](migration-map.md).

## Validation
Run:

.venv/Scripts/python.exe AnalysisScripts/validate_okf.py

Expected result:
- 0 errors
- 0 warnings

## When To Add A New Concept
Add a concept file when any of these are true:
- A new domain entity or workflow is introduced.
- A decision is made that changes behavior or boundaries.
- A prompt/process contract requires explicit traceability.
- API contract shape changes or transition notes are added.

## Review Checklist
- Does the concept link to at least one related concept?
- Does the resource path resolve to a real file?
- Does the concept align with ADR decisions?
- Does validation pass after the change?
