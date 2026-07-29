# Generate Operational MDs Prompt

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
# Usage: #generate-ops.prompt.md â€” specify which pass to run
# Source: spec/zachman.md (must be complete â€” zero BLOCKS GENERATION items)
# Output: ops/ folder

You are a spec extraction agent.
Your only source of truth is the Zachman material and reviewed
ops/ files provided. Do not invent, infer, or improve.

Global rules that apply to every file you generate:
- Extract and reformat only
- Use flat, declarative, imperative language
- Where the source is ambiguous or silent:
  write [NEEDS DECISION: describe the gap]
- Do not add explanatory prose
  Every line is a statement of fact or constraint
- Do not resolve conflicts between sources
  Flag as [CONFLICT: describe it]
- Output MD only â€” no preamble, no summary at the end

---

## Pass 1 â€” Extract from Zachman

Run all four. Do not skip any.

### ops/pass1/service-boundaries.md
Source: spec/zachman.md Network + Data columns, Rows 3â€“4

Structure per service:
  ## [Service Name]
  ### Owns
  - data entities, operations, decisions this service owns
  ### Does Not Own
  - explicit list of what adjacent services handle
  ### Dependencies
  - what it consumes from other services (read-only)

### ops/pass1/domain-rules.md
Source: spec/zachman.md Motivation + Data columns, Rows 2â€“3

Structure:
  ## [Domain Area]
  ### Invariants
  - rules that must always be true
  ### State Transitions
  - [current state] + [trigger] â†’ [new state]
  ### Validation Rules
  - field/entity: rule as a boolean constraint
  ### Calculations
  - name: formula or plain-language definition

### ops/pass1/data-flow.md
Source: spec/zachman.md Function + Network columns, Row 3

Structure per user journey:
  ## [Journey Name]
  ### Trigger
  - what initiates this flow
  ### Steps
  1. [Actor/Service] â†’ [action] â†’ [Actor/Service]
  ### Data Touched
  - entities read or written per step
  ### End State
  - what is true when this journey completes successfully

### ops/pass1/decisions.md
Source: spec/zachman.md Owner + Designer rows, all columns

Structure:
  ## ADR-[n]: [Decision Title]
  - Status: Accepted
  - Context: one sentence on why this was needed
  - Decision: what was decided
  - Constraints: what this locks in for agents
  - Supersedes: [ADR-n] if applicable

---

## Pass 1 Audit (run after all four files are written)

### Audit A: Cross-file consistency
  ## Inconsistencies
  - [file-a] vs [file-b]: [what conflicts]
  ## Duplications
  - [topic]: defined in both [file-a] and [file-b]
  ## Missing References
  - [file-a] references [thing] not defined anywhere

### Audit B: Zachman coverage
  ## Unrepresented Zachman Content
  - [row/col]: [missing] â†’ belongs in [file]
  ## Zachman Gaps
  - [row/col]: empty or ambiguous
    [NEEDS DECISION: describe]

### Audit C: Agent readiness
For each file:
  ## [filename]
  ### Blocking Issues
  - [issue]
  ### Non-blocking Issues
  - [issue]
  ### Ready: yes / no

Gate: all four files must be marked Ready: yes
before running Pass 2.

---

## Pass 2 â€” Author Fresh

Run only after Pass 1 gate is passed.
Source for all Pass 2 files: reviewed ops/pass1/ files.

### ops/pass2/out-of-scope.md
  ## Explicitly Excluded from v1
  ### Features
  - [feature]: [one-line reason]
  ### Integrations
  - [system]: [one-line reason]
  ### Data
  - [entity or dataset]: [one-line reason]
  ## Deferred Decisions
  - [topic]: [NEEDS DECISION: what needs resolving before v2]

### ops/pass2/error-handling.md
  ## Error Categories
  - [category name]: definition, HTTP status if applicable
  ## Per-Service Rules
  ### [Service Name]
  - Retryable errors: list
  - Non-retryable errors: list
  - Propagation: bubble up or handle locally
  ## User-Facing Errors
  - [error category]: what the UI receives and displays
  ## Logging Standard
  - what is always logged at each severity level

### Pre-authoring gate for testing-strategy.md  
Before writing unit tests for any service, verify:  
- Every entity named in domain-rules.md has a corresponding test area  
- Every operator/algorithm in specs/row5/ has a corresponding test area  
- Every extension point in service-boundaries.md has present, absent, and failure test cases  
- Every user journey in data-flow.md has a corresponding integration test  
If any are missing, add them before proceeding.

### ops/pass2/testing-strategy.md  
Source: ops/pass1/domain-rules.md, ops/pass1/service-boundaries.md,  
        ops/pass1/data-flow.md, spec/zachman.md Rows 4â€“5  
        (including any linked sub-documents referenced from Row 5 cells)  
  
  ## Per Service  
  ### [Service Name]  
  
  #### Unit Tests  
  Structure per test area:  
  - Entity coverage: one test area per entity defined in domain-rules.md  
    - Every enum field: assert each enum value persists and queries correctly  
    - Every constraint (required, unique, FK): assert valid accepted, invalid rejected  
    - Every state transition in domain-rules.md State Transitions: assert  
      [current] + [trigger] â†’ [new state], and assert illegal transitions rejected  
  - Operator/algorithm coverage: one test area per operator or algorithm  
    defined in specs/row5/ binding artifacts  
    - Enumerate each operator/value individually â€” do not summarise as a group  
    - Include: valid input, null input, empty input, type mismatch input  
  - Extension point coverage: one test area per extension point or dependency  
    listed in service-boundaries.md Dependencies  
    - Extension present and returns expected value  
    - Extension present and throws exception  
    - Extension absent (if optional) â€” assert fallback behaviour  
  - Hierarchy/graph coverage: for any entity with a self-referencing  
    relationship (parent, hierarchy, dependency graph):  
    - Valid tree (depth 1, depth 3)  
    - Circular reference detected and rejected  
    - Orphan node handled  
  
  #### Integration Tests  
  - One test area per user journey defined in data-flow.md  
    - Assert: trigger fires, all steps execute, end state matches  
  - One test area per schema migration or data setup script  
    - Assert: applies cleanly to blank store, idempotent on re-run  
  - State what is live vs stubbed and why  
  
  #### Contract Tests  
  - One test area per cross-service dependency in service-boundaries.md  
    - Assert: consumed interface shape matches provider's published shape  
  
  #### Edge Cases (per service)  
  - Zero-input: assert behaviour when primary input set is empty  
  - Null-input: assert behaviour when required input is null  
  - Boundary values: assert behaviour at documented limits  
    (max lengths, min/max numeric, date boundaries)  
  
  ## Definition of Done  
  Checklist â€” agent must satisfy all before a slice is complete:  
  - Every entity in domain-rules.md has at least one test area  
  - Every state machine in domain-rules.md has transition coverage  
  - Every invariant in domain-rules.md is asserted by at least one test  
  - Every user journey in data-flow.md passes as an integration test  
  - Every schema migration applies cleanly to a blank store  
  - Every extension point has present, absent (if optional), and failure tests  
  - No evaluated operation silently swallows an exception  
  - Cross-partition data isolation verified (data under partition A  
    not visible to partition B)  
  - All test data uses synthetic/masked values  
  - No unresolved [NEEDS DECISION] or [CONFLICT] tags in referenced specs---

## Pass 3 â€” Derive from Pass 1 + 2

Run only after Pass 2 gate is passed.
Source: all reviewed ops/pass1/ and ops/pass2/ files.

### ops/pass3/api-contracts.md
  ## [Service Name]
  ### [METHOD] /path
  - Purpose: one line
  - Request: schema as key: type, required/optional
  - Response 200: schema
  - Response errors: [status]: [condition]
  - Consumed by: [service or UI]

### Pre-authoring gate for api-contracts.md
Before writing any request/response shape, verify each field name
against the actual entity fields in specs/what/data-model.md.
If a field in the intended request shape does not exist on the entity,
write [CONFLICT: field 'X' not present on entity Y â€” reconcile before proceeding].
Do not invent field names. Do not use business-language aliases for technical field names.
Every entity reference must use the correct Python module path (kafka_compliance_agent.models).
  ## [event.name] (dot notation, past tense verb)
  - Producer: [service]
  - Consumers: [service list]
  - Trigger: what causes this event
  - Payload: schema as key: type, required/optional
  - Ordering guarantee: yes/no
  - Retry behaviour: [NEEDS DECISION] if not in source

### ops/pass3/component-patterns.md
  ## [Pattern Name]
  - Use when: [condition]
  - Do not use when: [condition]
  - Required elements: list
  - Optional elements: list
  - Example context: [journey name from data-flow]

### ops/pass3/seed-data.md
  ## [Entity Name]
  - Minimum records: [n]
  - Required fields: key: example value
  - Relationships: [entity] references [entity]
  - Covers journey: [journey name from data-flow]

Run Pass 1 audits A, B, C again against Pass 3 files.

### Audit D: Data model alignment
For each request/response shape in api-contracts.md:
- Every field name must match a field in the entity defined in specs/what/data-model.md
- Every entity reference must use the correct Python module path (kafka_compliance_agent.models)
- Flag mismatches as [CONFLICT: api-contracts.md field 'X' vs entity field 'Y']
Gate: no unresolved CONFLICT tags before proceeding to generate-backlog.

Gate: all four Pass 3 files must be marked Ready: yes
before proceeding to generate-backlog.prompt.md.

