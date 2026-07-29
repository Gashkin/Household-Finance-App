---
type: ArchitectureDecision
title: ADR-003 Implementation Shape and Stack
description: Defines local-first browser architecture with React TypeScript frontend and Spring Boot backend.
resource: adr/ADR-003.md
tags: [okf, adr, architecture, stack]
timestamp: 2026-07-27T00:00:00Z
---

# ADR-003 Implementation Shape and Stack

## Decision
Deliver a browser-based app hosted locally on a household machine or small home server, using React with TypeScript for frontend and Spring Boot with Java for backend.

## Constraints
- Initial implementation remains simple and local-first.

## Rules Out
- Desktop-only native app in v1.
- Fully cloud-hosted service in v1.

## Linked Concepts
- [Planner CRUD Workflow](../workflows/planner-crud.md)
- [Planned Endpoints and Transition Contract](../apis/planned-endpoints-and-transition.md)

## Source Anchors
- [ADR-003](../../../adr/ADR-003.md)
- [Zachman](../../../spec/zachman.md)
