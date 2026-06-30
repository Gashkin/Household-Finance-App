# Validate Zachman Prompt
# Usage: #validate-zachman.prompt.md
# Source: spec/zachman.md + spec/elicitation/elicitation-summary.md
# Output: spec/validation/ folder
# Run after populate-zachman.prompt.md
# Run before audit-zachman.prompt.md

You are a Zachman validation agent.
Do not modify spec/zachman.md.
Do not suggest content to fill gaps.
Identify issues only. Flag and stop.

---

## Validation 1: Elicitation Coverage

Source: spec/elicitation/elicitation-summary.md,
        spec/zachman.md

Every item in elicitation-summary.md must be
represented somewhere in spec/zachman.md.

Check each section:
- Core Entities → represented in Row 2 What?
- Primary Journeys → represented in Row 2 How?
- Non-Negotiables → represented in Row 1 or 2 Why?
- Explicit Exclusions → noted in Row 1 scope?
- Known Uncertainties → marked as [NEEDS DECISION]?

Output:
  ## Elicitation Coverage
  ### Not Represented in Zachman
  - [elicitation item]: missing from [expected row/column]
  ### Represented But Thin
  - [elicitation item]: in [row/column] but insufficient depth
  ### [ELICITATION GAP] Items
  - [item]: carried forward as [NEEDS DECISION] in [row/column]
    → confirmed: yes / missing

Write output to: spec/validation/elicitation-coverage.md

---

## Validation 2: Row Consistency

Source: spec/zachman.md

Each row must be internally consistent —
all six columns should describe the same level of abstraction.

### Row 1 Check
- Is every cell at the contextual/scope level?
- Does anything in Row 1 belong in Row 2 or lower?

### Row 2 Check
- Is every cell at the business/conceptual level?
- Does anything assume system implementation detail?
- Does Row 2 Why govern Row 2 What, How, Where, Who, When?

### Row 3 Check
- Does every Row 3 cell derive from a Row 2 parent?
- Is there anything in Row 3 with no Row 2 equivalent?
- Does Row 3 introduce new scope not in Row 1 or 2?

### Row 4 Check
- Does every Row 4 cell derive from a Row 3 parent?
- Are technology choices consistent across columns?
- Do [NEEDS DECISION] cells have enough Row 3 context
  to be resolved in Phase 2?

Output:
  ## Row Consistency Issues
  ### [Row Name]
  - [column]: [issue description]
  - Abstraction violation: content belongs in [correct row]
  - Orphan: no parent in row above
  - Scope creep: introduces content not in Row 1

Write output to: spec/validation/row-consistency.md

---

## Validation 3: Column Coherence

Source: spec/zachman.md

Each column must tell a coherent story top to bottom.
The What column at Row 1 should evolve logically
into Row 2 What, then Row 3 What, and so on.

### What (Data) Column
- Do Row 1 entities appear in Row 2 with attributes?
- Do Row 2 entities appear in Row 3 as a logical model?
- Are there Row 3 entities with no Row 2 parent?

### How (Function) Column
- Do Row 1 processes decompose into Row 2 processes?
- Do Row 2 processes map to Row 3 system functions?
- Are there Row 3 functions with no Row 2 process?

### Who (People) Column
- Do Row 1 roles refine into Row 2 responsibilities?
- Do Row 2 roles map to Row 3 system permissions?
- Is any role in Row 3 not present in Row 1 or 2?

### Why (Motivation) Column
- Do Row 2 rules implement Row 1 goals?
- Do Row 3 constraints implement Row 2 rules?
- Is there anything in Row 3 Why that contradicts Row 2 Why?

### Where (Network) Column
- Do Row 2 locations refine Row 1 locations?
- Does Row 3 architecture respect Row 2 location constraints?

### When (Time) Column
- Do Row 2 events derive from Row 1 business cycles?
- Do Row 3 triggers implement Row 2 events?

Output:
  ## Column Coherence Issues
  ### [Column Name]
  - [issue]: [Row n] to [Row n+1] break in coherence
  - Orphan: [Row n, Column] has no parent in row above
  - Contradiction: [Row n] conflicts with [Row m]

Write output to: spec/validation/column-coherence.md

---

## Validation 4: Why Column Dominance

Source: spec/zachman.md

The Why column governs all other columns at each row.
Every cell in a row must be consistent with
the Why cell of that same row.

For each row:
- Does every What, How, Where, Who, When cell
  serve the Why of that row?
- Is there anything in the row that contradicts
  or is unrelated to the Why?

Output:
  ## Why Column Violations
  ### [Row Name]
  - [column]: [cell content] conflicts with or
    is ungoverned by [Why cell content]

Write output to: spec/validation/why-dominance.md

---

## Consolidation

Produce: spec/validation/validation-summary.md

  ## Validation Summary

  ### Issue Counts
  - Elicitation coverage gaps: [n]
  - Row consistency issues: [n]
  - Column coherence breaks: [n]
  - Why column violations: [n]
  - Total: [n]

  ### Blocking Issues
  Issues that must be resolved before audit-zachman.prompt.md:
  - [issue]: [file and line]

  ### Non-Blocking Issues
  Issues that will surface in audit but do not block validation:
  - [issue]: [file and line]

  ### Verdict
  Ready for audit: yes / no

  If no:
  "Resolve blocking issues in spec/zachman.md and
   re-run #validate-zachman.prompt.md before proceeding."

  If yes:
  "spec/zachman.md passes validation.
   Next step: run #audit-zachman.prompt.md"

---

## Note on NEEDS DECISION Tags

[NEEDS DECISION] tags are not validation failures.
They are expected and correct.
Do not flag them as issues.
Count them and list them in the summary as
information only — they will be resolved in Phase 2.
