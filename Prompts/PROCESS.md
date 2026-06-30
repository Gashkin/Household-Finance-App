# The Process
## Spec-Driven Development with Agents
### A complete walkthrough for a chatbot agent to guide you through

---

## How To Use This Document

This document is your process guide. Give it to a chatbot agent and say:

"Walk me through The Process. I am at [phase and step].
Ask me what you need to know. Tell me what to run.
Keep me on track."

The agent's job is to:
- Tell you exactly what to do at each step
- Ask you the right questions before you proceed
- Check gates are passed before moving you forward
- Never let you skip a step without acknowledging it

Your job is to:
- Answer questions honestly
- Make decisions when options are presented
- Review agent output before confirming gates
- Never let an agent resolve ambiguity for you

---

## The Complete Pipeline

```
ZACHMAN CREATION
  Phase E: Elicitation
  Phase P: Population
  Phase V: Validation

SPEC DEVELOPMENT
  Phase 1: Audit Zachman
  Phase 2: Complete Zachman

SPEC GENERATION
  Phase 3: Generate Operational MDs (3 passes)

BACKLOG
  Phase 4: Generate Backlog
  Phase 4A: Audit Backlog

EXECUTION
  Phase 5: Execute Items

ARCHAEOLOGY (run before any phase if codebase exists)
  Phase 0: Archaeology
```

---

## Entry Points

**Starting a brand new project with no Zachman:**
→ Begin at Phase E

**Have a Zachman already (yours or adapted):**
→ Begin at Phase 0 if code exists, otherwise Phase 1

**Have a Zachman and existing code:**
→ Begin at Phase 0

**Have completed ops/ files:**
→ Begin at Phase 4

**Have a completed backlog:**
→ Begin at Phase 5

---

## Folder Structure

```
repo/
│
├── PROCESS.md                        ← this file
│
├── spec/
│   ├── zachman.md                    ← primary source of truth
│   ├── elicitation/
│   │   ├── motivation.md             ← Phase E output
│   │   ├── domain.md                 ← Phase E output
│   │   ├── people.md                 ← Phase E output
│   │   ├── journeys.md               ← Phase E output
│   │   ├── scope.md                  ← Phase E output
│   │   └── elicitation-summary.md    ← Phase E consolidation
│   ├── archaeology/
│   │   ├── existing-state.md         ← Phase 0 output
│   │   └── existing-patterns.md      ← Phase 0 output
│   ├── audit/
│   │   ├── zachman-completeness.md   ← Phase 1 output
│   │   ├── zachman-conflicts.md      ← Phase 1 output
│   │   ├── zachman-greenfield.md     ← Phase 1 output
│   │   └── zachman-resolution.md     ← Phase 1 output
│   └── validation/
│       ├── elicitation-coverage.md   ← Phase V output
│       ├── row-consistency.md        ← Phase V output
│       ├── column-coherence.md       ← Phase V output
│       ├── why-dominance.md          ← Phase V output
│       └── validation-summary.md     ← Phase V output
│
├── adr/
│   ├── ADR-000-template.md
│   └── ADR-NNN.md ...                ← one per decision, Phase 2
│
├── ops/
│   ├── pass1/
│   │   ├── service-boundaries.md     ← Phase 3 Pass 1
│   │   ├── domain-rules.md           ← Phase 3 Pass 1
│   │   ├── data-flow.md              ← Phase 3 Pass 1
│   │   └── decisions.md              ← Phase 3 Pass 1
│   ├── pass2/
│   │   ├── out-of-scope.md           ← Phase 3 Pass 2
│   │   ├── error-handling.md         ← Phase 3 Pass 2
│   │   └── testing-strategy.md       ← Phase 3 Pass 2
│   └── pass3/
│       ├── api-contracts.md          ← Phase 3 Pass 3
│       ├── events.md                 ← Phase 3 Pass 3
│       ├── component-patterns.md     ← Phase 3 Pass 3
│       └── seed-data.md              ← Phase 3 Pass 3
│
├── agents/
│   ├── agents.md                     ← agent roles and constraints
│   ├── architecture.md               ← tech stack and conventions
│   ├── principles.md                 ← build principles
│   ├── microservice-strategy.md      ← service decomposition rules
│   └── ui-template.md                ← UI patterns and tokens
│
├── backlog/
│   ├── slices.md                     ← Phase 4 output
│   ├── walking-skeleton.md           ← Phase 4 output
│   ├── ordered-backlog.md            ← Phase 4 output
│   ├── audit/
│   │   └── backlog-audit.md          ← Phase 4A output
│   └── work-orders/
│       ├── ITEM-000.md               ← walking skeleton
│       └── ITEM-NNN.md ...           ← one per item
│
└── progress.md                       ← live execution state
```

---

## File Roles Quick Reference

| File | Role | Never Edit Directly? |
|---|---|---|
| `spec/zachman.md` | Primary source of truth | No — update via Phase 2 only |
| `spec/elicitation/` | Raw domain material from interview | No — output of Phase E |
| `spec/archaeology/` | Implicit decisions from existing code | No — output of Phase 0 |
| `spec/audit/` | Zachman gap analysis | No — output of Phase 1 |
| `spec/validation/` | Zachman internal consistency | No — output of Phase V |
| `adr/` | Decision records | Yes — you write these |
| `ops/` | Agent-readable specs | No — regenerate from Zachman |
| `agents/` | Stable agent constraints | Yes — but never during execution |
| `backlog/work-orders/` | Self-contained build tasks | Yes — annotate if blocked |
| `progress.md` | Live session state | Agent updates, you review |

---

## Prompt Quick Reference

| Prompt | Phase | What It Does |
|---|---|---|
| `#elicit-domain.prompt.md` | E | Interviews you to capture domain knowledge |
| `#populate-zachman.prompt.md` | P | Fills Zachman matrix from elicitation output |
| `#validate-zachman.prompt.md` | V | Checks Zachman internal consistency |
| `#audit-zachman.prompt.md` | 1 | Audits Zachman for gaps and conflicts |
| `#complete-zachman.prompt.md` | 2 | Resolves gaps one at a time, writes ADRs |
| `#generate-ops.prompt.md` | 3 | Generates all ops/ files in 3 passes |
| `#generate-backlog.prompt.md` | 4 | Generates slices, items, work orders |
| `#audit-backlog.prompt.md` | 4A | Audits full backlog before execution |
| `#execute-item.prompt.md` | 5 | Runs one build item per session |
| `#review-item.prompt.md` | 5 | Reviews completed item, Pass or Blocked |
| `#archaeology.prompt.md` | 0 | Surfaces implicit decisions in existing code |

---

---

# PHASE 0: ARCHAEOLOGY
*Run this on any existing codebase before any other phase.*
*Also run when taking over someone else's code or returning to old code.*
*Skip to Phase E if starting completely from scratch with no code.*

## Purpose
Surface implicit decisions baked into existing code before they
cause drift. The codebase is the only documentation for decisions
that were never written down.

## Prompt
```
#archaeology.prompt.md
```

## Steps
1. Run against existing codebase
2. Agent produces:
   - `spec/archaeology/existing-state.md`
   - `spec/archaeology/existing-patterns.md`
3. You review existing-state.md and annotate every finding:
   - ✓ Carry forward — aligns with intent
   - ✗ Conflicts with intent — decision needed
   - ? Not yet decided — add to resolution list
4. ✗ and ? items feed into Phase 1 as additional audit input

## Gate
- [ ] existing-state.md exists and is fully annotated
- [ ] existing-patterns.md exists
- [ ] All ✗ items noted for Phase 1 resolution

---

---

# PHASE E: ELICITATION
*Run this when starting a new project with no Zachman.*
*This is a structured interview — the agent asks, you answer.*

## Purpose
Extract domain knowledge from your head into structured raw
material before any framework shapes it. This prevents your
Zachman from being a generic template someone else filled in.

## Prompt
```
#elicit-domain.prompt.md
```

## What Happens
The agent interviews you across five areas in order.
It asks one question at a time. You answer in plain language.
It captures your answers verbatim without interpretation.

**Area 1: Purpose and Motivation**
Why does this system exist? What problem does it solve?
What must it never get wrong?
→ Output: `spec/elicitation/motivation.md`

**Area 2: The Domain**
What things does the system track or act on?
What rules must always be true? Where do the numbers come from?
→ Output: `spec/elicitation/domain.md`

**Area 3: The People**
Who uses it? Who owns what data? Who is responsible when
things go wrong?
→ Output: `spec/elicitation/people.md`

**Area 4: The Journeys**
Walk through the most important things users do.
What can go wrong mid-journey?
→ Output: `spec/elicitation/journeys.md`

**Area 5: Scope and Boundaries**
What does v1 explicitly not do? What is the irreducible core?
Where are you uncertain?
→ Output: `spec/elicitation/scope.md`

**Consolidation**
→ Output: `spec/elicitation/elicitation-summary.md`

## Gate
- [ ] All five area files exist
- [ ] elicitation-summary.md exists
- [ ] Readiness assessment shows all areas Complete
- [ ] Any gaps explicitly accepted and recorded as
      [ELICITATION GAP] entries

---

---

# PHASE P: POPULATION
*Run after Phase E. Fills the Zachman matrix from elicitation output.*

## Purpose
Translate structured elicitation material into the Zachman matrix.
Row by row, Why first within each row, with human review gates
between rows.

## Prompt
```
#populate-zachman.prompt.md
```

## Population Order
Rows must be populated in this sequence. Do not skip ahead.

```
Row 1: Scope (Contextual)
  → You review before Row 2 begins

Row 2: Business (Conceptual)    ← most important row
  → You review carefully before Row 3 begins
  → Weaknesses here propagate into all system design

Row 3: System (Logical)
  → You review before Row 4 begins

Row 4: Technology (Physical)
  → Partial population acceptable
  → Undecided cells marked [NEEDS DECISION]

Rows 5-6
  → Placeholder only
  → Completed during Phase 2 as decisions are made
```

Within each row, columns populate in this order:
```
Why → What → How → Where → Who → When
```

## What the Agent Does Per Row
1. Reads relevant elicitation files
2. Populates all six columns
3. Writes to spec/zachman.md
4. Presents row to you for review
5. Waits for confirmation before next row

## Gate
- [ ] spec/zachman.md exists with Rows 1–4 populated
- [ ] Rows 1, 2, 3 reviewed and confirmed by you
- [ ] Row 4 has [NEEDS DECISION] tags where appropriate
- [ ] Rows 5–6 have placeholder sections

---

---

# PHASE V: VALIDATION
*Run after Phase P. Checks Zachman internal consistency.*
*Run before Phase 1 audit.*

## Purpose
Catch internal consistency issues in the Zachman before the
audit phase. A Zachman that fails validation produces noisy
audit output. Fix structural issues here first.

## Prompt
```
#validate-zachman.prompt.md
```

## Four Validation Checks

**Validation 1: Elicitation Coverage**
Every item in elicitation-summary.md must be represented
in spec/zachman.md.
→ Output: `spec/validation/elicitation-coverage.md`

**Validation 2: Row Consistency**
Every cell in a row must be at the correct abstraction level.
Nothing in Row 2 should assume system implementation detail.
Nothing in Row 3 should introduce scope not in Rows 1–2.
→ Output: `spec/validation/row-consistency.md`

**Validation 3: Column Coherence**
Each column must tell a coherent story top to bottom.
Row 1 entities should evolve into Row 2 entities into Row 3.
→ Output: `spec/validation/column-coherence.md`

**Validation 4: Why Column Dominance**
The Why column governs all other columns at each row.
Every cell must serve the Why of its row.
→ Output: `spec/validation/why-dominance.md`

**Consolidation**
→ Output: `spec/validation/validation-summary.md`

## Note
[NEEDS DECISION] tags are not validation failures.
They are expected and correct. Do not flag them as issues.

## Gate
- [ ] All four validation files exist
- [ ] validation-summary.md verdict is Ready for audit: yes
- [ ] All blocking issues resolved in spec/zachman.md
- [ ] Non-blocking issues noted for Phase 1

---

---

# PHASE 1: AUDIT ZACHMAN
*Establish what the spec knows, assumes, and is silent on.*

## Purpose
Produce a prioritised resolution list of everything that must
be decided before spec generation can begin. This is your
working list for Phase 2.

## Prompt
```
#audit-zachman.prompt.md
```

## Four Audit Steps

**Audit 1a: Cell Completeness**
Identifies every cell that is Empty, Thin, or Assumed.
Assumed means language like "standard", "typical", "TBD",
"as needed" — these are vibe placeholders.
→ Output: `spec/audit/zachman-completeness.md`

**Audit 1b: Cross-Cell Conflicts**
Identifies contradictions — row conflicts, column conflicts,
motivation conflicts.
→ Output: `spec/audit/zachman-conflicts.md`

**Audit 1c: Greenfield Gaps**
Identifies cells that assume things that do not exist yet.
Flags each as [BLOCKS GENERATION] or [BLOCKS BUILD].
→ Output: `spec/audit/zachman-greenfield.md`

**Audit 1d: Consolidation**
Single prioritised resolution list ordered by dependency.
Cells that unblock other cells listed first.
→ Output: `spec/audit/zachman-resolution.md`

If Phase 0 ran, include existing-state.md as additional
source in all four audit steps.

## Gate
- [ ] All four audit files exist in spec/audit/
- [ ] zachman-resolution.md exists and is prioritised
- [ ] You have read and understood the resolution list

---

---

# PHASE 2: COMPLETE ZACHMAN
*Resolve every blocking gap before spec generation starts.*
*Run once per gap until Must Resolve Before Generation list is empty.*

## Purpose
Every decision is yours. The agent presents options.
You choose. The agent records. Nothing is invented.

## Prompt
```
#complete-zachman.prompt.md
```

## The Loop — Run Once Per Gap

```
1. Take first unresolved item from
   "Must Resolve Before Generation" in zachman-resolution.md

2. Run #complete-zachman.prompt.md
   Agent reads: relevant Zachman cells + agents/ constraints

3. Agent presents 2–3 options. Per option:
   - What is decided
   - What it locks in
   - What it rules out
   - Which downstream cells it affects

4. You choose

5. Agent updates spec/zachman.md
   Agent writes adr/ADR-NNN.md
   Agent marks item ✓ Resolved in zachman-resolution.md

6. Repeat until list is empty
```

## ADR Structure
```
## ADR-[n]: [Decision Title]
- Status: Accepted
- Date: [date]
- Context: why this decision was needed
- Decision: what was decided
- Constraints: what this locks in for agents
- Rules out: what this explicitly prevents
- Supersedes: [ADR-n] if applicable
```

## Detail Row Consistency Check

If any resolved gap populates or updates a detail row
(Row 5+) that references linked sub-documents:

- Every field name in the sub-document must match
  the corresponding entity definition in the parent
  Zachman row (typically Row 3 What)
- Every table/entity name must match
- Every enum value set must match
- Every relationship/FK must match

Run this check after each detail-row ADR is written,
before marking the item resolved.

If mismatches are found, fix the sub-document to match
the Zachman source — the Zachman matrix is authoritative.

This prevents field name drift from propagating into
ops/ generation, where it surfaces as CONFLICT tags
in Pass 3 Audit D — much more expensive to fix.

## Gate
- [ ] Zero items in Must Resolve Before Generation
- [ ] Every resolved item has a corresponding ADR
- [ ] spec/zachman.md updated for every resolved cell

---

---

# PHASE 3: GENERATE OPERATIONAL MDs
*Translate Zachman into agent-readable specifications.*
*Three passes with audit gates between each.*

## Purpose
ops/ files are what build agents read. They must be flat,
declarative, unambiguous, and traceable to Zachman.
They are never edited directly — fix Zachman and regenerate.

## Prompt
```
#generate-ops.prompt.md
```

## Global Generation Rule
The agent extracts and reformats only.
It does not invent. It does not infer.
Gaps become [NEEDS DECISION] tags.
Conflicts become [CONFLICT] tags.

---

## Pass 1 — Extract from Zachman

Source: spec/zachman.md

**service-boundaries.md**
What each service owns and explicitly does not own.
Derived from: Zachman Network + Data columns, Rows 3–4

**domain-rules.md**
Invariants, state transitions, validation rules, calculations.
Derived from: Zachman Motivation + Data columns, Rows 2–3

**data-flow.md**
Runtime sequence per user journey from trigger to end state.
Derived from: Zachman Function + Network columns, Row 3

**decisions.md**
Flat ADR-style list of architectural decisions with constraints.
Derived from: Zachman Owner + Designer rows

### Pass 1 Audit (agent runs automatically after generation)

Audit A: Cross-file consistency
Inconsistencies, duplications, missing references between files.

Audit B: Zachman coverage
What is in Zachman but not represented in Pass 1 files.

Audit C: Agent readiness
Per file: Blocking Issues, Non-blocking Issues, Ready: yes/no

## Pass 1 Gate
- [ ] All four files exist in ops/pass1/
- [ ] All four files marked Ready: yes in Audit C
- [ ] You have reviewed and confirmed Pass 1 files

---

## Pass 2 — Author Fresh

Source: reviewed ops/pass1/ files

**out-of-scope.md**
Explicit v1 exclusions by feature, integration, and data.
Deferred decisions. Stops agent gold-plating.

**error-handling.md**
Error categories, per-service rules, propagation standard,
logging standard. Prevents inconsistent failure patterns.

**testing-strategy.md**
What to test at what layer per service type.
Definition of done checklist. Gates what agents mark complete.

### Pass 2 Audit — same three audits as Pass 1

## Pass 2 Gate
- [ ] All three files exist in ops/pass2/
- [ ] All three files marked Ready: yes
- [ ] You have reviewed and confirmed Pass 2 files

---

## Pass 3 — Derive from Pass 1 + 2

Source: all reviewed ops/pass1/ and ops/pass2/ files

**api-contracts.md**
Every endpoint: method, path, request/response schemas,
error codes, consumers.

**events.md**
Every async event: name, producer, consumers, payload,
ordering guarantee, retry behaviour.

**component-patterns.md**
Which UI pattern to use when. Prevents template drift.

**seed-data.md**
Minimal realistic dataset for dev and test.
Prevents agents generating nonsense fixtures.

### Pass 3 Audit — same three audits

## Pass 3 Gate
- [ ] All four files exist in ops/pass3/
- [ ] All four files marked Ready: yes
- [ ] You have reviewed and confirmed Pass 3 files
- [ ] Total: 11 ops/ files exist, all Ready

---

---

# PHASE 4: GENERATE BACKLOG
*Derive ordered executable items from operational MDs.*

## Purpose
Convert the spec into a prioritised, dependency-ordered list
of self-contained build tasks. Every item must be executable
by a build agent without needing to read anything else.

## Prompt
```
#generate-backlog.prompt.md
```

## Five Steps

**Step 4a: Slice Identification**
Identifies all vertical slices from data-flow.md.
A slice is one complete user journey from trigger to end state.
Orders slices by dependency — lowest dependency first.
→ Output: `backlog/slices.md`

**Step 4b: Walking Skeleton**
Identifies ITEM-000 — the minimum end-to-end path that
proves the architecture is real before any feature is built.
→ Output: `backlog/walking-skeleton.md`

**Step 4c: Item Generation**
Generates items per slice in this order:
Schema → Logic → Endpoint → Integration → UI → Tests

**Step 4d: Context Packaging**
Wraps each item as a fully self-contained work order.
The work order must contain everything a build agent needs.
→ Output: `backlog/work-orders/ITEM-NNN.md` per item

**Step 4e: Backlog Ordering**
Master ordered list with dependency map.
Identifies immediately unblocked items.
→ Output: `backlog/ordered-backlog.md`

## Recommended Approach for Medium-Large Projects

```
Session A: Steps 4a + 4b only
  → Review slices.md before proceeding
  → Wrong slice boundaries create misaligned work orders downstream

Session B: Steps 4c + 4d + 4e
  → Run per slice if model starts to drift
  → Each slice run is bounded and verifiable
```

## Gate
- [ ] backlog/slices.md exists
- [ ] backlog/walking-skeleton.md exists as ITEM-000
- [ ] backlog/ordered-backlog.md exists
- [ ] Every item has a work order in backlog/work-orders/
- [ ] Every item has spec reference, defined inputs/outputs,
      verifiable acceptance criteria
- [ ] No unresolved [NEEDS DECISION] tags in any work order

---

---

# PHASE 4A: AUDIT BACKLOG
*Run after Phase 4 completes fully.*
*Run before seeding progress.md and starting execution.*

## Purpose
Verify the full backlog is coherent before a line of code
is written. Catches issues that only appear when all slices
are visible together.

## Prompt
```
#audit-backlog.prompt.md
```

## Five Audit Areas

**Audit 1: Slice Completeness**
Every journey in data-flow.md maps to a slice.
Every slice has at least one item.

**Audit 2: Item Integrity**
Every item has valid spec reference, defined inputs/outputs,
verifiable acceptance criteria, self-contained work order.

**Audit 3: Dependency Validity**
No circular dependencies. No missing inputs.
No items blocked later than necessary.

**Audit 4: Walking Skeleton Integrity**
ITEM-000 proves architecture end-to-end.
ITEM-000 is minimal — no feature scope.

**Audit 5: Spec Coverage**
Every spec reference in every work order points to
a real section in a real ops/ file.

→ Output: `backlog/audit/backlog-audit.md`

## Gate
- [ ] backlog/audit/backlog-audit.md exists
- [ ] Verdict: Ready to execute: yes
- [ ] All blocking issues resolved
- [ ] You have read ordered-backlog.md and ITEM-000

---

---

# PHASE 5: EXECUTE
*One item. One session. State in files.*

## Purpose
Build the application item by item, in order, with full
traceability to the spec. No improvisation. No drift.
Progress is always recoverable because state lives in files.

## Prompts
```
#execute-item.prompt.md    ← start of every session
#review-item.prompt.md     ← end of every session
```

---

## Seed progress.md Before First Session

Create progress.md in repo root:

```markdown
## Current Item
ITEM-000: Walking Skeleton
Status: Not Started

## Completed Items
(none)

## Blocked Items
(none)

## Next Item
ITEM-001: [title from ordered-backlog.md]
```

---

## The Session Loop

### Start of Session
```
1. Open Copilot Chat
2. Run: #execute-item.prompt.md
3. Agent reads progress.md
4. Agent reads current work order only
5. Agent states pre-flight:
   "I am working on ITEM-[n]: [title]"
   "I will create/modify: [file list]"
6. You confirm
7. Agent builds
8. Agent self-reviews against acceptance criteria
9. Agent updates progress.md
10. Agent stops — does not begin next item
```

### End of Session
```
11. Run: #review-item.prompt.md
12. Provide: completed work order + what agent produced
13. Agent returns verdict:

    ✓ Pass →
      Confirm progress.md is updated
      Start next session on next item

    ✗ Blocked →
      Do NOT update progress.md
      Annotate work order with the issues
      Start new session with #execute-item.prompt.md
      Reference the annotated work order
```

---

## Handling Drift

Older and smaller models will sometimes drift off task.

**Signs of drift:**
- Agent modifies files not in the work order
- Agent adds features not in acceptance criteria
- Agent asks questions instead of writing [BLOCKED]
- Agent begins the next item without being asked

**Response to drift:**
Start a new session. Do not continue the drifted session.
Run #execute-item.prompt.md from scratch.
Ignore all previous conversation output.
progress.md is the only state that matters.

**Prevention for older or smaller models:**
- Only paste the work order — not full spec files
- Add at the end of every prompt:
  "Reminder: your only job is ITEM-[n].
   Stop when acceptance criteria are met.
   Do not proceed to ITEM-[n+1]."
- For complex items, add a mid-item checkpoint:
  "Before writing any code, list the files you will
   create or modify and why. Stop and wait."

---

## progress.md Format

```markdown
## Current Item
ITEM-[n]: [title]
Status: In Progress | Ready for Review | Not Started

## Completed Items
- ITEM-000: Walking Skeleton ✓
- ITEM-001: [title] ✓

## Blocked Items
- ITEM-[n]: [reason] → [NEEDS DECISION: what must be resolved]

## Next Item
ITEM-[n+1]: [title]
```

---

## Definition of Done Per Item

- [ ] All acceptance criteria in the work order are met
- [ ] Only files listed in the work order were modified
- [ ] No features added beyond the acceptance criteria
- [ ] Tests required by testing-strategy.md are passing
- [ ] progress.md is updated
- [ ] #review-item.prompt.md returns Ready to proceed

---

---

# THE TEN RULES

These apply at all times across all phases.

1. No agent sees more than one work order at a time
2. No agent resolves ambiguity — it writes [BLOCKED]
3. No phase starts until the previous gate is passed
4. State lives in files, not conversation history
5. ops/ files are never edited directly — fix Zachman and regenerate
6. agents/ files are never modified during execution
7. Every work order is self-contained
8. Every item traces to a spec reference
9. Archaeology before any existing codebase is touched
10. You decide. Agents execute. You verify.

---

---

# WHEN THINGS GO WRONG

## An ops/ file has a gap or error
Fix spec/zachman.md.
Re-run #generate-ops.prompt.md for the affected pass only.
Do not edit ops/ files directly.

## A work order is wrong or incomplete
Annotate the work order file with the issue.
Re-run #generate-backlog.prompt.md for that slice only.
Do not edit work orders to add scope — fix the ops/ source first.

## An ADR needs to change
Write a new ADR that supersedes the old one.
Update spec/zachman.md to reflect the new decision.
Identify which ops/ files are affected and regenerate them.
Identify which work orders reference the changed area
and regenerate those.

## A gate cannot be passed
Do not proceed. The gate exists because the next phase
depends on the previous one being complete.
Identify the specific issue, fix it in the correct source
file, and re-run the relevant prompt.

## You are unsure which phase you are in
Read progress.md.
Check which ops/ files exist.
Check spec/audit/zachman-resolution.md status.
Tell the guide agent:
"Look at my repo structure and tell me which phase
I am in and what the next step is."

---

---

# QUICK REFERENCE

## What to run when

| Situation | Action |
|---|---|
| New project, no Zachman | `#elicit-domain.prompt.md` |
| Elicitation done | `#populate-zachman.prompt.md` |
| Zachman populated | `#validate-zachman.prompt.md` |
| Zachman valid | `#audit-zachman.prompt.md` |
| Audit done, gaps to resolve | `#complete-zachman.prompt.md` once per gap |
| All gaps resolved | `#generate-ops.prompt.md` Pass 1 |
| Pass 1 gate passed | `#generate-ops.prompt.md` Pass 2 |
| Pass 2 gate passed | `#generate-ops.prompt.md` Pass 3 |
| All ops/ files Ready | `#generate-backlog.prompt.md` |
| Backlog generated | `#audit-backlog.prompt.md` |
| Backlog audit passed | Seed progress.md then `#execute-item.prompt.md` |
| Item built | `#review-item.prompt.md` |
| Review passed | Update progress.md, next `#execute-item.prompt.md` |
| Review blocked | Annotate work order, new `#execute-item.prompt.md` |
| Agent drifted | New session, `#execute-item.prompt.md` from scratch |
| Existing codebase | `#archaeology.prompt.md` first, then normal flow |
| ops/ file has error | Fix zachman.md, rerun `#generate-ops.prompt.md` |

---

## File Ownership at a Glance

| You write | Agent writes | Agent writes, you confirm |
|---|---|---|
| agents/ files | spec/elicitation/ | spec/zachman.md (Phase 2) |
| adr/ files | spec/archaeology/ | ops/ files |
| progress.md seed | spec/audit/ | backlog/work-orders/ |
| annotations on any file | spec/validation/ | backlog/ordered-backlog.md |
| | progress.md updates | |

---

## Gates Summary

| Phase | Gate Condition |
|---|---|
| 0 Archaeology | existing-state.md annotated, existing-patterns.md exists |
| E Elicitation | All five areas Complete, summary exists |
| P Population | Rows 1–3 reviewed, Row 4 populated with NEEDS DECISION tags |
| V Validation | validation-summary.md Ready for audit: yes |
| 1 Audit | zachman-resolution.md exists and prioritised |
| 2 Complete | Zero items in Must Resolve Before Generation |
| 3 Pass 1 | All four files Ready: yes |
| 3 Pass 2 | All three files Ready: yes |
| 3 Pass 3 | All four files Ready: yes |
| 4 Backlog | All items have work orders, no NEEDS DECISION tags |
| 4A Audit | Ready to execute: yes, all blocking issues resolved |
| 5 Per item | review-item returns Ready to proceed |

---

*This process is a living document.
If a phase reveals a gap in this process, update this file.
The process should reflect reality, not aspiration.*
