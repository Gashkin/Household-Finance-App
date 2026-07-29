---
type: WorkItem
title: Prompt to OKF Traceability Matrix
description: Defines which OKF indexes and concept families each prompt must consult.
resource: Prompts/
tags: [okf, prompts, traceability]
timestamp: 2026-07-27T00:00:00Z
---

# Prompt to OKF Traceability Matrix

## Rule
Every prompt session starts at [OKF Root Index](../index.md), then resolves phase-specific context from linked concept indexes.

## Matrix

| Prompt File | Primary OKF Context | Secondary OKF Context | Source Anchor |
|---|---|---|---|
| Prompts/PROCESS.md | [Work Item Concepts](index.md) | [Workflow Concepts](../workflows/index.md) | [Prompts/PROCESS.md](../../../Prompts/PROCESS.md) |
| Prompts/CHANGE-PROCESS.md | [Work Item Concepts](index.md) | [Decision Concepts](../decisions/index.md) | [Prompts/CHANGE-PROCESS.md](../../../Prompts/CHANGE-PROCESS.md) |
| Prompts/copilot-instructions.md | [Work Item Concepts](index.md) | [Rules Concepts](../rules/index.md) | [Prompts/copilot-instructions.md](../../../Prompts/copilot-instructions.md) |
| Prompts/E2E-SESSION-CHECKLIST.md | [Work Item Concepts](index.md) | [Workflow Concepts](../workflows/index.md) | [Prompts/E2E-SESSION-CHECKLIST.md](../../../Prompts/E2E-SESSION-CHECKLIST.md) |
| Prompts/archaeology.prompt.md | [Workflow Concepts](../workflows/index.md) | [Decision Concepts](../decisions/index.md) | [Prompts/archaeology.prompt.md](../../../Prompts/archaeology.prompt.md) |
| Prompts/elicit-domain.prompt.md | [Domain Concepts](../domain/index.md) | [Rules Concepts](../rules/index.md) | [Prompts/elicit-domain.prompt.md](../../../Prompts/elicit-domain.prompt.md) |
| Prompts/populate-zachman.prompt.md | [Domain Concepts](../domain/index.md) | [Workflow Concepts](../workflows/index.md) | [Prompts/populate-zachman.prompt.md](../../../Prompts/populate-zachman.prompt.md) |
| Prompts/validate-zachman.prompt.md | [Rules Concepts](../rules/index.md) | [Decision Concepts](../decisions/index.md) | [Prompts/validate-zachman.prompt.md](../../../Prompts/validate-zachman.prompt.md) |
| Prompts/audit-zachman.prompt.md | [Decision Concepts](../decisions/index.md) | [Rules Concepts](../rules/index.md) | [Prompts/audit-zachman.prompt.md](../../../Prompts/audit-zachman.prompt.md) |
| Prompts/complete-zachman.prompt.md | [Decision Concepts](../decisions/index.md) | [API Concepts](../apis/index.md) | [Prompts/complete-zachman.prompt.md](../../../Prompts/complete-zachman.prompt.md) |
| Prompts/generate-ops.prompt.md | [Workflow Concepts](../workflows/index.md) | [API Concepts](../apis/index.md) | [Prompts/generate-ops.prompt.md](../../../Prompts/generate-ops.prompt.md) |
| Prompts/generate-backlog.prompt.md | [Work Item Concepts](index.md) | [Workflow Concepts](../workflows/index.md) | [Prompts/generate-backlog.prompt.md](../../../Prompts/generate-backlog.prompt.md) |
| Prompts/audit-backlog.prompt.md | [Work Item Concepts](index.md) | [Rules Concepts](../rules/index.md) | [Prompts/audit-backlog.prompt.md](../../../Prompts/audit-backlog.prompt.md) |
| Prompts/execute-item-soft.prompt.md | [Work Item Concepts](index.md) | [API Concepts](../apis/index.md) | [Prompts/execute-item-soft.prompt.md](../../../Prompts/execute-item-soft.prompt.md) |
| Prompts/review-item-soft.prompt.md | [Rules Concepts](../rules/index.md) | [Work Item Concepts](index.md) | [Prompts/review-item-soft.prompt.md](../../../Prompts/review-item-soft.prompt.md) |
| Prompts/coverage-audit.prompt.md | [Rules Concepts](../rules/index.md) | [Workflow Concepts](../workflows/index.md) | [Prompts/coverage-audit.prompt.md](../../../Prompts/coverage-audit.prompt.md) |

## Validation Intent
Use this matrix when reviewing prompt edits and when verifying that new prompt files are linked to OKF context paths.
