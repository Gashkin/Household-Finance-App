# Generate Backlog Prompt

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
# Usage: #generate-backlog.prompt.md
# Source: all ops/ files (must all be marked Ready: yes)
# Output: backlog/ folder

You are a backlog generation agent.
Derive items from the ops/ files only.
Do not invent scope. Do not add items not traceable to a spec reference.
Flag gaps as [SPEC INCOMPLETE: which file needs more detail].

---

## Step 4a: Slice Identification

Source: all ops/ files

Identify all vertical slices.
A slice is one complete user journey from trigger to end state
as defined in ops/pass1/data-flow.md.

Structure per slice:
  ## [Slice Name]
  - Trigger: what initiates it (from data-flow.md)
  - Services involved: list (from service-boundaries.md)
  - Data touched: list (from data-flow.md)
  - Dependencies: which slices must exist first
  - Complexity: Low | Medium | High

Order slices by dependency chain, lowest dependency first.

Write output to: backlog/slices.md

---

## Step 4b: Walking Skeleton

Source: ops/pass1/service-boundaries.md,
        ops/pass1/data-flow.md,
        agents/architecture.md,
        agents/microservice-strategy.md

Identify the walking skeleton before any slice work begins.
The walking skeleton is the minimum viable end-to-end path
that proves the architecture is real.

  ## Walking Skeleton â€” ITEM-000
  ### Minimum Services
  - services that must exist for any slice to run
  ### Minimum Infrastructure
  - what must be provisioned before any code runs
  ### Minimum Data Model
  - tables/schemas required before any slice can execute
  ### Acceptance Criteria
  - [ ] verifiable statement that proves skeleton works
  - [ ] verifiable statement
  ### Done When
  [single unambiguous statement]

Write output to: backlog/walking-skeleton.md

---

## Step 4c: Item Generation

Source: current slice from slices.md,
        ops/pass3/api-contracts.md,
        ops/pass1/service-boundaries.md,
        ops/pass1/domain-rules.md,
        ops/pass2/testing-strategy.md

Run once per slice. Generate items in this order within each slice:
Schema â†’ Logic â†’ Endpoint â†’ Integration â†’ UI â†’ Tests

Structure per item:
  ## ITEM-[n]: [Title]
  - Type: Schema | Logic | Endpoint | UI | Integration | Test
  - Service: [service name]
  - Spec reference: [MD file and section]
  - Inputs: what must exist before this item starts
  - Outputs: what exists when this item is done
  - Acceptance criteria: verifiable statements only,
      no subjective language
  - [NEEDS DECISION] tags for anything unresolved

---

## Step 4d: Context Packaging

Run once per item immediately after generation.
Do not wait until all items are generated.

Source: ITEM-[n] + all ops/ files

Produce a self-contained work order for ITEM-[n].
The work order must contain everything the agent needs.
The agent should not need to open any other file.

### Input Verification Rule
For each item listed under Inputs:
- Verify the class/file/method actually exists in the declared source item's completed work order output
- If it does not exist in a completed work order, do not list it as an input
- If it is needed but not yet created, either add it to the current item's file list or create a blocking dependency on a prior item
- Never list an input that exists only in the spec but not in a completed work order
- Entity field names used in acceptance criteria must match specs/what/data-model.md exactly â€” not business-language aliases

Write to: backlog/work-orders/ITEM-[n].md

Structure:
  ## ITEM-[n]: [Title]

  ### What To Build
  [single paragraph, plain language, no jargon]


  ### Exact Files To Create Or Modify
  - [filepath]: [what changes and why]

  ### Compilation Closure Rule  
    For every file in "Exact Files To Create Or Modify", scan all method signatures, constructor parameters, field types, and import statements.  
    Every type referenced must satisfy ONE of:  
    - It already exists in the repo (from a prior completed item)  
    - It is listed as another file in THIS item's file list  
    - It is a JDK or declared-dependency class  
    If a type does not satisfy any of the above, add it to the file list with a description of what it is and why it is needed.  
    Do not assume the agent will infer implicit types â€” they will write [BLOCKED] instead.  
  
  ### Module Bootstrap Rule  
    If this is the FIRST item targeting a new Gradle module (e.g., `raas-governance`):  
    - The file list MUST include `<module>/build.gradle` with dependency declarations  
    - The file list MUST include `settings.gradle` as a modify entry (to add `include '<module>'`)  
    - The file list MUST include the module's directory structure creation  
    These are prerequisites for any source file in the new module to compile.  
  
  ### Host Wiring Rule  
    If the item's Type is Endpoint, or if it introduces new service classes that must be injected into the Spring host:  
    - The file list MUST include `raas-sample-host/src/main/java/com/citi/raas/host/config/RaasConfig.java` as a modify entry â€” new @Bean methods are needed for new services  
    - If the new module's entities/repositories need scanning, the file list MUST include `SampleHostApplication.java` for @EntityScan/@EnableJpaRepositories updates  
    The agent cannot modify files not in the file list. If wiring files are omitted, the application will not start.  

### Entity-Type Dispatch Rule  
    If the item introduces a new entity type (e.g., ScopeOverride) that flows through an existing generic interface  
    (any method that accepts `entityType` as a parameter or dispatches on entity type):  
    - Identify ALL methods on that interface/service that contain entity-specific logic (not just the one the work order mentions)  
    - The file list MUST include every file containing those methods as a modify entry  
    - The work order MUST describe the dispatch logic for the new entity type in EVERY affected method  
    - Example: if `ApprovalService` has `submitForApproval()`, `approve()`, and `reject()` all with Rule-specific logic,  
      and the new item adds ScopeOverride support, all three methods must be listed â€” not just `submitForApproval()`  
    - If a prior item stubbed/no-oped the new entity type in one method, the consuming item MUST un-stub ALL methods, not just the one it directly calls  
    Do not assume upstream services are entity-agnostic â€” check every method body for hardcoded entity assumptions.

  ### Build Dependency Rule  
    If the item introduces usage of a library not currently in the module's `build.gradle` (e.g., Drools, a new Spring starter, a test library):  
    - The file list MUST include the relevant `build.gradle` as a modify entry  
    - The work order MUST specify the exact dependency coordinates (group:artifact:version) or at minimum the artifact name  
    - If the library has a non-obvious API (e.g., Drools 9.x removed KnowledgeBaseFactory), include a "Design Note" with the correct API entry point and import path  
  
  ### Algorithm Guidance Rule  
    If the item requires non-trivial algorithmic logic (DRL generation, graph traversal, scoring/ranking, state machines, transpilation):  
    - The work order MUST include pseudo-code or an exact code skeleton for the critical method bodies  
    - Method signatures alone are insufficient â€” agents cannot reliably infer correct algorithms from prose descriptions  
    - For third-party DSLs (Drools DRL, SQL, MVEL), provide an exact example of the expected output format  
  
  ### Third-Party API Rule  
    If the item depends on a third-party library with version-specific APIs:  
    - The work order MUST name the exact version being used  
    - The work order MUST include the correct import paths and API entry points for that version  
    - Example: Drools 9.x uses `org.kie.internal.utils.KieHelper` not `org.drools.core.impl.KnowledgeBaseFactory`  
    - Do not assume the agent knows which APIs exist in which versions

  ### Rules That Apply
  [extract only the lines from ops/ files relevant to this item â€”
   do not paste entire files]

  ### Inputs
  - [file or endpoint]: what it contains that this item needs

  ### Acceptance Criteria
  - [ ] verifiable statement
  - [ ] verifiable statement

  ### Do Not
  - do not modify [file] â€” owned by [service]
  - do not add [feature] â€” out of scope per out-of-scope.md
  - do not invent [thing] â€” not in spec

  ### Done When
  [single unambiguous statement of completion]

---

## Step 4e: Backlog Ordering

Source: all generated items

  ## Backlog

  ### Milestone 0: Walking Skeleton
  - ITEM-000

  ### Milestone [n]: [Slice Name]
  - ITEM list in execution order

  ## Dependency Map
  - ITEM-[n] blocked by ITEM-[n]: [reason]

  ## Unblocked Items
  Items with no dependencies that can start immediately:
  - ITEM-[n]: [title]

Flag any item where spec coverage is insufficient:
[SPEC INCOMPLETE: which MD file needs more detail]

Write output to: backlog/ordered-backlog.md

---

## Step 4f: Code Coverage Hardening

Source: ops/pass2/testing-strategy.md

Generate a dedicated milestone containing one item per module (as defined in
ops/pass1/service-boundaries.md) that enforces the coverage floor.

Coverage floor: 90% line coverage and 90% branch coverage per module.
Measurement tool: JaCoCo (configured in pom.xml).

Structure per item:
  ## ITEM-[n]: Coverage â€” [Module Name]
  - Type: Test
  - Service: [module name]
  - Spec reference: ops/pass2/testing-strategy.md
  - Coverage floor: 90% line, 90% branch
  - Acceptance criteria:
    - [ ] `mvn verify` passes, enforcing the coverage floor via the JaCoCo `check` goal.
    - [ ] Any line or branch below the floor has a corresponding test added in this item
    - [ ] No new JaCoCo exclusions added to `pom.xml` without a documented justification in the item's work order
  - Done When: CI gate fails the build if coverage drops below 90% for this module.

Add a summary item ITEM-COV-ALL:
  ## ITEM-COV-ALL: Coverage Gate â€” Full Suite
  - Type: Test
  - Spec reference: ops/pass2/testing-strategy.md
  - Acceptance criteria:
    - [ ] `mvn verify` passes with JaCoCo enforcing 90% line and branch coverage across the full project
    - [ ] Coverage report is published as a CI artefact on every pipeline run
  - Done When: full-suite coverage gate is enforced in the CI pipeline.

Write output to: backlog/ordered-backlog.md under milestone "Coverage Hardening".

---

## Step 4g: Logging Hardening

Source: ops/pass2/error-handling.md Â§ Logging Standard,
        ops/pass3/component-patterns.md

Generate a dedicated milestone that enforces the logging standard defined in
ops/pass2/error-handling.md across every module.

Rules to enforce (from error-handling.md):
- Structured log output (key=value or JSON) at every log call site
- Log level discipline: DEBUG for internal state, INFO for cross-module handoffs and counts, WARNING for recoverable errors, ERROR for unrecoverable failures
- Every ERROR log must include: module, operation, error_type, and a correlation/scan_id where one exists
- No bare `print()` calls in production code paths
- No stack traces logged above WARNING level without a structured context envelope

Structure per item:
  ## ITEM-[n]: Logging Hardening â€” [Module Name]
  - Type: Logic
  - Service: [module name]
  - Spec reference: ops/pass2/error-handling.md Â§ Logging Standard
  - Acceptance criteria:
    - [ ] All log call sites in the module use structured key=value fields
    - [ ] No `print()` calls remain in production code paths
    - [ ] Every ERROR log includes module, operation, error_type, and scan_id (where applicable)
    - [ ] Log level at each call site matches the error-handling.md level discipline
  - Done When: a grep for `print(` and bare `logging.error(` (without structured fields) returns zero matches in the module.

Write output to: backlog/ordered-backlog.md under milestone "Logging Hardening".

---

## Gate

Before finishing, verify every item has:
- [ ] Spec reference pointing to a real ops/ file and section
- [ ] Defined inputs â€” each input is traceable to a specific completed work order output, not just a spec reference
- [ ] Defined outputs
- [ ] At least two verifiable acceptance criteria
- [ ] No unresolved [NEEDS DECISION] tags
- [ ] A self-contained work order in backlog/work-orders/

  - [ ] Entity field names in acceptance criteria match specs/what/data-model.md field names exactly
  - [ ] Every type referenced in method signatures across all file lists is accounted for (exists in repo, prior item output, or current item file list)  
  - [ ] Every new module has its build.gradle and settings.gradle entry listed in the first item targeting it  

  - [ ] Every Endpoint item includes host wiring files (RaasConfig.java, SampleHostApplication.java) if depending on a new module or new service classes  
  - [ ] Every item that introduces new library imports includes the relevant build.gradle with dependency coordinates

  - [ ] Every item that introduces a new entity type into an existing generic interface lists ALL methods on that interface that contain entity-specific logic â€” not just the one the item directly calls  
    - [ ] Every stub/no-op deferred to a future item (e.g., "ScopeOverride: stub â€” ITEM-015") has a corresponding un-stub entry in the consuming item's file list and acceptance criteria

Report:
- Total items generated: [n]
- Items with [NEEDS DECISION]: [n] â€” list them
- Items with [SPEC INCOMPLETE]: [n] â€” list them
- Coverage Hardening milestone present: yes / no
- Logging Hardening milestone present: yes / no
- Ready to execute: yes / no

