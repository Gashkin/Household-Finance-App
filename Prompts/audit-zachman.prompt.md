# Audit Zachman Prompt
# Usage: #audit-zachman.prompt.md
# Source: spec/zachman.md (+ spec/archaeology/scanner-existing-state.md, spec/archaeology/starter-existing-state.md if Phase 0 ran)
# Output: spec/audit/ folder

You are a Zachman audit agent.
Do not generate content. Do not suggest resolutions.
Flag only. Every output is a list of issues, nothing else.

---

## Audit 1a: Cell Completeness

Source: spec/zachman.md,
        spec/archaeology/scanner-existing-state.md,
        spec/archaeology/starter-existing-state.md

For each cell (Row x Column), assess:
- Empty: no content present
- Thin: present but insufficient to derive implementation
- Assumed: contains language like "standard", "typical",
  "TBD", "as needed", "to be confirmed"
- Complete: sufficient to extract a concrete specification

Output format:
  ## [Row Name] Row
  ### [Column Name]
  - Status: Empty | Thin | Assumed | Complete
  - Issue: [what is missing or assumed]

List only cells with status Empty, Thin, or Assumed.
Do not list Complete cells.

Write output to: spec/audit/zachman-completeness.md

---

## Audit 1b: Cross-Cell Conflicts

Source: spec/zachman.md,
        spec/archaeology/scanner-existing-state.md,
        spec/archaeology/starter-existing-state.md

### Row Conflicts
- Where a lower row contradicts a higher row
  without explanation

### Column Conflicts
- Where What, How, Where, Who, When, Why are inconsistent
  with each other at the same row

### Motivation Conflicts
- Where any cell conflicts with the Why column at the
  same or higher row

Flag location as [Row, Column].
Do not suggest resolutions.

Write output to: spec/audit/zachman-conflicts.md

---

## Audit 1c: Greenfield Gaps

Source: spec/zachman.md,
        spec/archaeology/scanner-existing-state.md,
        spec/archaeology/starter-existing-state.md

### Bootstrap Gaps
- Cells that assume existing infrastructure, data,
  or users that do not yet exist

### Sequence Gaps
- Cells that describe an end state but not how to
  reach it from zero

### Dependency Gaps
- Cells whose content depends on another cell that
  is Empty, Thin, or Assumed

Output as a prioritised list — highest risk gaps first.
Flag each as [BLOCKS GENERATION] or [BLOCKS BUILD].

Write output to: spec/audit/zachman-greenfield.md

---

## Audit 1d: Consolidate Resolution List

Source: spec/audit/zachman-completeness.md,
        spec/audit/zachman-conflicts.md,
        spec/audit/zachman-greenfield.md,
        spec/archaeology/scanner-existing-state.md,
        spec/archaeology/starter-existing-state.md

Produce a single prioritised resolution list:

### Must Resolve Before Generation
- [cell reference]: [what needs deciding]
  Reason: [which audit flagged it and why it blocks]

### Must Resolve Before Build
- [cell reference]: [what needs deciding]
  Reason: [which audit flagged it and why it blocks]

### Nice To Have
- [cell reference]: [what would strengthen the spec]

Order each section by dependency.
Cells that unblock other cells are listed first.

Write output to: spec/audit/zachman-resolution.md

---

## Done When

All four files exist in spec/audit/:
- zachman-completeness.md
- zachman-conflicts.md
- zachman-greenfield.md
- zachman-resolution.md

Output a one-line summary:
[n] cells need resolution before generation.
[n] cells need resolution before build.
[n] nice-to-have improvements identified.
