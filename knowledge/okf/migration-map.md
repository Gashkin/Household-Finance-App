---
type: MigrationMap
title: Legacy Docs to OKF Migration Map
description: Tracks source-to-concept mapping status and remaining gaps for full OKF adoption.
resource: knowledge/okf/migration-map.md
tags: [okf, migration, traceability]
timestamp: 2026-07-27T00:00:00Z
---

# OKF Migration Map

## Mapping Table

| Source Path | Target OKF Concept Path | Status | Notes |
|---|---|---|---|
| spec/zachman.md | knowledge/okf/domain/index.md | Converted | Seeded domain and workflow concept files; continue deeper cell-by-cell extraction. |
| ops/pass1/service-boundaries.md | knowledge/okf/services/service-boundaries.md | Converted | Service ownership and data boundary model captured as service concept. |
| ops/pass1/domain-rules.md | knowledge/okf/rules/domain-invariants-and-calculations.md | Converted | Invariants and calculations captured as rule concept. |
| ops/pass1/data-flow.md | knowledge/okf/workflows/planner-crud.md | Converted | Planner and import flow expressed through workflow concepts. |
| adr/ADR-001.md | knowledge/okf/decisions/adr-001-consumer-experience.md | Converted | Decision concept added with links to monthly summary. |
| adr/ADR-002.md | knowledge/okf/decisions/adr-002-transaction-intake.md | Converted | Transaction intake decision captured and linked to import workflow. |
| adr/ADR-003.md | knowledge/okf/decisions/adr-003-implementation-shape-and-stack.md | Converted | Implementation shape and stack decision captured and linked to API/workflow concepts. |
| adr/ADR-004.md | knowledge/okf/decisions/adr-004-planner-consumer-access-model.md | Converted | Planner/consumer role boundary captured and linked to rules/summary concepts. |
| adr/ADR-005.md | knowledge/okf/decisions/adr-005-commitments-split.md | Converted | Commitments split decision captured and linked to persistence concepts. |
| adr/ADR-006.md | knowledge/okf/decisions/adr-006-planner-api-compatibility.md | Converted | Planner API compatibility decision captured and linked to transition contract. |
| backlog/ordered-backlog.md | knowledge/okf/work-items/ordered-backlog-roadmap.md | Converted | Roadmap concept created and linked to item 29. |
| ops/pass2/out-of-scope.md | knowledge/okf/rules/out-of-scope-v1.md | Converted | Version-1 exclusions captured as scope rule concept. |
| ops/pass2/error-handling.md | knowledge/okf/rules/error-handling-rules.md | Converted | Error behavior captured as explicit rule concept. |
| ops/pass2/testing-strategy.md | knowledge/okf/runbooks/testing-strategy.md | Converted | Testing levels and definition-of-done captured as runbook concept. |
| ops/pass3/api-contracts.md | knowledge/okf/apis/planned-endpoints-and-transition.md | Converted | API endpoint surface and migration transition notes captured as API concept. |
| ops/pass3/events.md | knowledge/okf/apis/events-catalog.md | Converted | Planned events captured as API event catalog concept. |
| ops/pass3/component-patterns.md | knowledge/okf/runbooks/ui-component-patterns.md | Converted | UI composition and interaction patterns captured as runbook concept. |
| spec/archaeology/existing-patterns.md | knowledge/okf/runbooks/existing-code-patterns.md | Converted | Legacy preservation patterns captured as runbook concept. |
| Prompts/*.md | knowledge/okf/work-items/prompt-okf-traceability.md | Converted | Prompt-to-OKF context matrix created for all prompt files. |

## Open Gaps
- None currently recorded.

## Update Rule
When a source is converted, set Status to Converted and add a note with the concept file path.
