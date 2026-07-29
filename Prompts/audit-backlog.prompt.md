# Audit Backlog Prompt

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
# Usage: #audit-backlog.prompt.md
# Source: backlog/ folder + all ops/ files
# Run after generate-backlog.prompt.md completes fully
# Output: backlog/audit/backlog-audit.md

You are a backlog audit agent.
Do not generate new items. Do not fix issues.
Identify and flag only.

---

## Audit 1: Slice Completeness

Source: ops/pass1/data-flow.md,
        backlog/slices.md

Every user journey in data-flow.md must map to a slice.
Every slice must have at least one item.

  ## Unmapped Journeys
  - [journey from data-flow.md]: no corresponding slice

  ## Empty Slices
  - [slice name]: exists in slices.md but has no items
    in ordered-backlog.md

---

## Audit 2: Item Integrity

Source: all backlog/work-orders/ITEM-NNN.md files,
        all ops/ files

For each item check:

  [ ] Has a spec reference pointing to a real ops/ file and section
  [ ] Inputs are defined
  [ ] Outputs are defined
  [ ] At least two acceptance criteria present
  [ ] Acceptance criteria are verifiable statements
      (no subjective language: "works correctly",
       "looks good", "is fast")
  [ ] Work order is self-contained
      (no "see spec for details" without quoting the relevant section)
  [ ] Do Not section is present

Flag failures as:
  [ITEM-NNN: INCOMPLETE â€” reason]

---

## Audit 3: Dependency Validity

Source: backlog/ordered-backlog.md,
        all backlog/work-orders/ITEM-NNN.md

### Circular Dependencies
- [ITEM-n] requires [ITEM-m] which requires [ITEM-n]

### Missing Inputs
- [ITEM-n]: input [x] is not produced by any earlier item

### Premature Items
- [ITEM-n]: could be unblocked earlier than currently ordered
  Reason: [its inputs are available at milestone n-1]

### Milestone Integrity
- Are all items in Milestone 1 dependent on ITEM-000?
- Does any item reference a slice that hasn't been built yet?

---

## Audit 4: Walking Skeleton Integrity

Source: backlog/walking-skeleton.md,
        backlog/work-orders/ITEM-000.md,
        agents/architecture.md

  ## Completeness
  - Does ITEM-000 prove the architecture end-to-end?
  - Is every service touched at least once?
  - Is there at least one data write and one data read?

  ## Minimality
  - Does ITEM-000 contain anything that belongs
    in a feature slice?
  - Flag: [thing in ITEM-000 that is feature scope]

  ## Acceptance Criteria
  - Are ITEM-000 criteria verifiable without
    any feature slice being built?

---

## Audit 5: Spec Coverage

Source: all backlog/work-orders/ITEM-NNN.md,
        all ops/ files

For each item, verify the spec reference is real:
- Does the referenced ops/ file exist?
- Does the referenced section exist in that file?
- Is the content in the work order consistent with
  the referenced section?


Flag as:
  [ITEM-NNN: SPEC INCOMPLETE â€” ops/[file] section [x]
   does not contain sufficient detail for this item]

---  
  
## Audit 6: Entity-Type Dispatch Completeness  
  
Source: all backlog/work-orders/ITEM-NNN.md files,  
        all source files in raas-core and raas-governance  
  
For each item that introduces a new entity type into an existing service:  
  
### Hardcoded Risk Check  
  For each file listed under "Inputs" or "Exact Files To Create Or Modify":  
  - Open the file and find ALL methods that reference the existing entity type (e.g., Rule)  
  - If ANY method contains hardcoded entity-specific logic (casts, findById on a specific repository,  
    entity-specific status transitions, entity-specific event emissions):  
    - Check whether the work order lists that method as requiring modification  
    - If not: [ITEM-NNN: HARDCODED RISK â€” method X in file Y is hardcoded for entityType=Rule  
      but item introduces entityType=Z without dispatch logic]  
  
### Stub Propagation Check  
  For each item that contains a stub or no-op deferred to a future item  
  (e.g., "for ScopeOverride: stub/no-op â€” ITEM-015"):  
  - Find the consuming item (ITEM-015 in the example)  
  - Verify the consuming item's file list includes the file containing the stub  
  - Verify the consuming item's acceptance criteria include un-stubbing that specific method  
  - If not: [ITEM-NNN: ORPHANED STUB â€” stub in method X deferred to ITEM-M,  
    but ITEM-M does not list file Y or include un-stub in AC]  
  
Flag failures as:  
  [ITEM-NNN: DISPATCH INCOMPLETE â€” reason] 

## Consolidation

Write output to: backlog/audit/backlog-audit.md

  ## Backlog Audit Summary

  ### Counts
  - Total items: [n]
  - Incomplete items: [n]
  - Dependency issues: [n]
  - Spec coverage gaps: [n]

  ### Blocking Issues
  Must fix before execution starts:
  - [issue]: [item reference]

  ### Non-Blocking Issues
  Can be resolved during execution if encountered:
  - [issue]: [item reference]

  ### Optimisations
  Reorderings or splits that would improve flow:
  - [suggestion]: [reason]

  ### Verdict
  Ready to execute: yes / no
  Blocking issue count: [n]

  If yes:
  "Seed progress.md with ITEM-000 and begin
   #execute-item.prompt.md"

  If no:
  "Resolve blocking issues and re-run
   #audit-backlog.prompt.md"

