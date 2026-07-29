# E2E Session Checklist

## OKF-First Context Contract (Mandatory)

Before producing output, load context in this order:
1. knowledge/okf/index.md
2. Relevant OKF indexes under knowledge/okf/ (domain/, workflows/, ules/, decisions/, work-items/)
3. Legacy source docs (spec/, ops/, dr/, acklog/) only when needed for gaps or verification

Rules:
- Treat OKF as the primary context source.
- If required context is missing in OKF, fall back to legacy docs and record the gap in knowledge/okf/migration-map.md under Open Gaps.
- For generated outputs, include an OKF Traceability section listing consulted OKF concept paths.
- Do not contradict ADR decisions; reflect them through linked OKF decision concepts.

---

Use this at the start and end of every session to keep execution pinned to PROCESS.md and CHANGE-PROCESS.md.

## Session Start (mandatory)

1. Declare current phase and item.
- Current phase: C2 / C3 / C4 / C5 / C6 (or Phase 5 for normal execution).
- Current item: ITEM-### from progress.md.
- Allowed outputs this session: exact file list.

2. Confirm source-of-truth chain.
- Decisions: adr/
- Specification: spec/zachman.md
- Operational definitions: ops/pass1, ops/pass2, ops/pass3
- Execution scope: backlog/work-orders/ITEM-###.md
- State tracking: progress.md

3. Scope lock.
- Only one work order in scope.
- No edits outside item scope.
- If ambiguity appears, mark [BLOCKED] and stop.

4. Gate check before coding.
- If phase is C2: ADR must be produced.
- If phase is C3: Zachman must be updated with ADR traceability.
- If phase is C4: ops must be regenerated from Zachman.
- If phase is C5: delta backlog/work orders must be generated.
- If phase is C6/Phase 5: execute only current work order.

## During Session

1. Pre-flight declaration before changes.
- Item being executed.
- Files to be modified.
- Acceptance criteria being targeted.

2. Drift guards.
- Do not add features outside acceptance criteria.
- Do not start next item in the same session.
- Do not treat conversation history as state; use files as state.

3. Validation.
- Run required tests/build checks from testing strategy.
- Collect concrete evidence (counts, endpoint response, test output summary).

## Session End (mandatory)

1. Run review against the current work order.
- Verdict: Pass or Blocked.
- If Blocked, include exact blocker and required decision.

2. Update progress.md immediately.
- Current Item status: Ready for Review / In Progress / Blocked.
- Completed Items: add item only on Pass.
- Next Item: set explicitly.

3. Record change traceability.
- ADR references (if applicable).
- Zachman sections updated (if applicable).
- ops files updated/regenerated (if applicable).
- Work order updated/created (if applicable).

## Hard Rules

- One item per session.
- No direct ops editing unless in explicit C4 regeneration step.
- No skipping gates.
- No unresolved ambiguity silently implemented.
- If process conflict appears, stop and resolve at ADR/spec level first.

