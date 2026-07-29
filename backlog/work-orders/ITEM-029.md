# ITEM-029: Full Open Knowledge Format (OKF) Adoption

## Objective
Adopt Open Knowledge Format across the repository so domain and operational knowledge is portable, agent-readable, and versioned with consistent metadata.

## Scope
- Define and document repository OKF conventions:
  - Required frontmatter fields: type, title, description, resource, tags, timestamp.
  - Concept type taxonomy for this project (DomainEntity, Service, Workflow, Rule, Runbook, ArchitectureDecision, WorkItem, API).
- Create an OKF bundle structure under the repository with index-based navigation and cross-links.
- Convert and/or map existing knowledge from spec, ops, adr, and backlog into OKF concept files.
- Add a migration map that records source document to OKF concept traceability.
- Add a lightweight OKF validation check for required metadata and link hygiene.
- Align prompt files to use OKF as the primary context source where applicable.

## Non-goals
- Rewriting product behavior, calculations, or persistence logic.
- Replacing source code documentation inside Java/TypeScript files.
- Removing existing docs before parity is confirmed.

## Inputs
- spec/zachman.md
- ops/pass1/domain-rules.md
- ops/pass1/service-boundaries.md
- adr/ADR-001.md
- backlog/ordered-backlog.md
- Prompts/PROCESS.md
- Prompts/CHANGE-PROCESS.md
- https://cloud.google.com/blog/products/data-analytics/how-the-open-knowledge-format-can-improve-data-sharing/

## Checklist
- [x] OKF folder structure exists with top-level index files.
- [x] Core concepts for accounts, categories, commitments, transactions, planner workflow, import workflow, and monthly summary are represented as OKF docs.
- [x] ADR knowledge is represented as linked OKF ArchitectureDecision concepts.
- [x] Backlog concepts are linked to relevant domain/rule/workflow concepts.
- [x] Required YAML frontmatter fields are present across OKF concept files.
- [x] Validation command reports pass with zero missing required fields.
- [x] Prompt files that generate or review artifacts are updated to reference OKF index and concept paths.

## Deliverables
- knowledge/okf/index.md
- knowledge/okf/domain/index.md
- knowledge/okf/workflows/index.md
- knowledge/okf/rules/index.md
- knowledge/okf/decisions/index.md
- knowledge/okf/work-items/index.md
- knowledge/okf/migration-map.md
- AnalysisScripts/validate_okf.py
- Prompts/PROCESS.md
- Prompts/CHANGE-PROCESS.md
- backlog/ordered-backlog.md
- backlog/work-orders/ITEM-029.md

## Evidence
- Date: 2026-07-27
- Status: Completed
- Commands run:
  - .venv/Scripts/python.exe AnalysisScripts/validate_okf.py
- Key outputs:
  - 27 OKF markdown files scanned
  - 0 validation errors and 0 warnings
  - Prompt files aligned to OKF-first context contract
