# Execute Item Prompt (Soft)

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
# Usage: start of a build session when you want structure without rigid stops

You are a build agent.
Follow the current work order closely, but keep momentum.
Be explicit, practical, and evidence-driven.

Mandatory process controls:
- Start with `Prompts/E2E-SESSION-CHECKLIST.md` session-start checks.
- End by completing `backlog/work-orders/REVIEW-TEMPLATE.md` for the current item.
- Fail closed on gate misses: if any required gate is missing, mark `[BLOCKED: GATE MISS]` and stop.

---

## 0) Environment Constraints (read before any command)

- **Artifactory / repository auth:** If any `mvn` command fails with 401 or 403, STOP. Do not retry, do not attempt workarounds. Record `[ENV BLOCKED: Artifactory auth â€” deferring to CI]` and skip all local build/test steps. CI pipeline is the authoritative build verification.
- **Local tooling:** If a required CLI tool is missing, record `[ENV BLOCKED: <tool> not found]` and continue with what is available.
- **Branch freshness:** Before creating a feature branch, run `git pull origin main` and `git log -1 origin/main`. Confirm the HEAD commit message references the most recently completed item in PROGRESS.md. If it doesn't match, re-pull â€” do not branch from a stale ref.

---

## 1) Orient Quickly

1. Read [backlog/progress.md](backlog/progress.md).
2. Identify the current item.
3. Create or switch to branch `feature/work_order_XXX` where `XXX` is the current item number (for example, ITEM-007 -> `feature/work_order_007`).
4. Read [backlog/work-orders/ITEM-xxx.md](backlog/work-orders).
5. Optionally read directly related files needed to implement or verify acceptance criteria.

Start with a short statement:

I am working on ITEM-xxx: <title>.
Planned files: <from work order exact files list>.

No mandatory confirmation pause is required unless something is ambiguous.

### 1.1 Session Gate Bootstrap (mandatory)

Before any implementation change:
- Read `Prompts/E2E-SESSION-CHECKLIST.md`.
- Declare phase, current item, and allowed output files for this session.
- Confirm the source-of-truth chain and scope lock.

If any required checklist field cannot be completed, fail closed:
- Record `[BLOCKED: GATE MISS - <missing gate>]`.
- Do not code until resolved.

---

## 2) Pre-Flight (Lightweight)

Before coding, capture:

### Files to create
- File path + one-line reason

### Files to modify
- File path + one-line reason

### Out-of-scope files
- State that files outside the work order list are avoided unless required for compile/runtime wiring

### Input verification
- Confirm required inputs exist (classes/files/functions listed by the work order)
- If something is missing, either:
  - create it if clearly implied by the work order, or
  - mark [BLOCKED] with exact missing input and why it prevents progress

Use judgment: prefer progress over ceremony.

---

## 3) Build

Implement the work order requirements and acceptance criteria.

Guidelines:
- Avoid adding features not requested by the work order.
- Avoid new dependencies unless required by the work order.
- Keep changes focused and minimal.
- If a small supporting change outside the exact list is needed for compilation/tests, allow it and document it in evidence.

### Test co-authoring rule
- For every new or modified production file, write or update a corresponding test file in the same commit.
- Coverage targets are class-type-dependent (see table below).
- Do NOT defer test writing to a follow-up item â€” tests ship with the code they cover.

| Class type | Min line% | Min branch% | Notes |
|---|---|---|---|
| Exception / POJO / DTO | 100% | 100% | All constructors, getters, setters must be exercised |
| Mock\*Client | 95% | 90% | Simple deterministic logic; all null/blank guard branches must be hit |
| Service / enricher | 85% | 80% | Happy path + all named error paths; outer structural catches may be excluded |
| Rest\*Client | 80% | 75% | Deep-stub mock chains leave some internal re-throw branches unreachable; annotate with `// not coverable via unit test` |
| Config / Spring wiring | 70% | 60% | Spring lifecycle code is integration-tested at context load time |

### Log sanitization rule (SonarQube S5145)
- Sanitize any user-controlled or external input before passing to log statements.
- Replace `\r`, `\n`, `\t` with underscores or use a sanitize utility method.
- This applies to: file names, account codes, security identifiers, error messages from external services, any string originating from HTTP requests or Kafka messages.
- If a utility method doesn't exist yet, create `com.citi.sl.aim.util.LogSanitizer` with a `sanitize(String)` method.

If spec ambiguity blocks implementation:
- Write [BLOCKED: what is unclear, what assumption is needed, and likely source file to resolve it].

---

## 4) Verify

Ensure that required command-line tools (e.g., Python, Node.js) are available in your system's PATH before running verification commands.

Run the most relevant checks for the item (examples):
- Project compile/build command if applicable
- Targeted tests for changed behavior
- Migrations/health checks if required by acceptance criteria

### Coverage gate (mandatory)
After `mvn test`, run `mvn jacoco:report` and inspect `target/site/jacoco/index.html`.
For each new or modified production class, apply the threshold from the class-type table in Section 3:

| Class type | Min line% | Min branch% |
|---|---|---|
| Exception / POJO / DTO | 100% | 100% |
| Mock\*Client | 95% | 90% |
| Service / enricher | 85% | 80% |
| Rest\*Client | 80% | 75% |
| Config / Spring wiring | 70% | 60% |

- If any class is below its threshold, write additional tests before proceeding to Section 5.
- For structurally uncoverable branches in Rest\*Client (internal re-throw paths from deep-stub mocks), add a comment `// not coverable via unit test` and record in Notes.
- Record per-class coverage in the evidence section.

If JaCoCo report is unavailable (e.g., Artifactory issue), record `[CANNOT VERIFY: JaCoCo report unavailable]` and list every test class written with approximate method-level coverage reasoning.

For each acceptance criterion, record:
- Met / Not Met / Cannot Verify
- One short evidence line (command, output summary, or file proof)

---

## 5) Update Status in progress.md

When all criteria are met (or clearly noted), update [backlog/progress.md](backlog/progress.md):

- Move completed item into Completed Items list
- Set Current Item to the next executable item
- Set Next Item accordingly
- Update date if needed

If item is partially complete or blocked:
- Keep Current Item unchanged
- Add short blocker note in your session summary

Fail-closed override:
- If any required PROCESS/CHANGE-PROCESS gate is missing, status must be Blocked.
- Do not move the item to Completed Items.
- Include exact missing gate in work-order Evidence and session summary.

---

## 6) Add Evidence into the Work Order

At the end of the work order file, add/update an Evidence section:

## Evidence
- Date: YYYY-MM-DD
- Status: Completed | Partial | Blocked | Not Started
- Commands run:
  - <command>
- Key outputs:
  - <important result>
- Coverage summary:
  - <ClassName>: line X%, branch Y%
  - (list every new/modified production class)
- Files changed:
  - <path>
- Acceptance criteria mapping:
  - [x]/[ ] <criterion> -> <proof>
- Notes:
  - <assumptions, deviations, follow-ups>

Keep evidence concise and factual.

---

## 7) Mandatory Review Handoff

At session end, complete `backlog/work-orders/REVIEW-TEMPLATE.md` for the current item.

Minimum handoff requirements:
- Scope validation (in-scope vs out-of-scope files)
- Acceptance criteria status with evidence
- Build/test/data verification summary
- Drift/risk check
- Verdict and `progress.md` update fields

Fail-closed rule:
- Any unresolved gate miss forces verdict `Blocked`.
- Do not mark item complete while gate status is unresolved.

---

## Operating Principles

- One item at a time by default.
- Prefer explicit evidence over long narrative.
- Use [BLOCKED] when needed; do not guess silently.
- Prioritize repository state as the source of truth (progress.md + work order + code).
- If a prior session drifted, re-read progress.md and current work order, then continue pragmatically.

