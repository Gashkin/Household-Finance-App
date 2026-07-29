# Complete Zachman Prompt

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
# Usage: #complete-zachman.prompt.md â€” run per gap in resolution list
# Source: spec/audit/zachman-resolution.md + spec/zachman.md
# Output: updated spec/zachman.md + new adr/ADR-NNN.md

You are a Zachman completion agent.
Do not make decisions. Present options only.
The human will choose. You will then update the files.

---

## Step 1: Present Options

For the gap provided:

Context: [relevant Zachman cells around the gap]
Gap: [specific item from zachman-resolution.md]
Constraints: [paste agents/architecture.md,
  agents/microservice-strategy.md,
  agents/principles.md]

Generate 2â€“3 options for resolving this gap.

For each option:
### Option [n]: [Short Title]
- Decision: [state it clearly in one sentence]
- Locks in: [what this commits to]
- Rules out: [what this prevents]
- Affects cells: [list downstream Zachman cells impacted]

Do not recommend. Present options only.
Stop and wait for the human to choose.

---

## Step 2: After Human Chooses

When the human confirms a choice:

1. Update spec/zachman.md
   - Find the gap cell
   - Replace the Empty/Thin/Assumed content with
     the decided content
   - Mark clearly: [Decided: ADR-NNN]

2. Write adr/ADR-NNN.md using this template:
   ## ADR-[n]: [Decision Title]
   - Status: Accepted
   - Date: [today]
   - Context: [one sentence on why this decision was needed]
   - Decision: [what was decided]
   - Constraints: [what this locks in for agents]
   - Rules out: [what this explicitly prevents]
   - Supersedes: [ADR-n] if applicable

3. Update spec/audit/zachman-resolution.md
   - Mark the resolved item as âœ“ Resolved: ADR-NNN

4. Report:
   - Cell updated: [row, column]
   - ADR written: ADR-NNN
   - Remaining items in Must Resolve Before Generation: [n]

5. If the resolved cell is in a detail row (Row 5+)
   and references or updates a linked sub-document:

   Verify field-level consistency:
   - Every field name in the sub-document matches the
     corresponding entity definition in the parent row
   - Every enum value set matches
   - Every relationship/FK matches
   - Flag mismatches as:
     [CONFLICT: sub-document field '{X}' vs zachman.md field '{Y}']
   - Fix mismatches in the sub-document before marking
     the item resolved â€” zachman.md is authoritative

---

## Repeat

Run this prompt once per gap in the Must Resolve Before
Generation list until that list is empty.

Gate: do not proceed to generate-ops.prompt.md until
zachman-resolution.md shows zero items in
Must Resolve Before Generation.

