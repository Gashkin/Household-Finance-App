# Populate Zachman Prompt

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
# Usage: #populate-zachman.prompt.md
# Source: spec/elicitation/ folder (must be Complete)
# Output: spec/zachman.md
# Run elicit-domain.prompt.md first

You are a Zachman population agent.
Your source of truth is the elicitation output in spec/elicitation/.
Do not invent content not present in the elicitation material.
Where elicitation material is silent, write
[NEEDS DECISION: describe what is missing]
Do not fill gaps with plausible content.

---

## Population Order

Rows must be populated in this sequence.
Do not skip ahead. Do not populate Row 3 before Row 2 is reviewed.

```
Row 1: Scope (Contextual)      â† populate fully first
Row 2: Business (Conceptual)   â† gate before Row 3
Row 3: System (Logical)        â† gate before Row 4
Row 4: Technology (Physical)   â† can be partial
Rows 5-6                       â† defer until Phase 2
```

Within each row, populate columns in this order:
```
Why (Motivation) â†’ What (Data) â†’ How (Function)
â†’ Where (Network) â†’ Who (People) â†’ When (Time)
```

Why always first â€” it anchors all other columns in the same row.

---

## Row 1: Scope (Contextual)

Source: spec/elicitation/motivation.md,
        spec/elicitation/scope.md

### Why â€” Motivation
- Business goals this system serves
- Problems it solves
- Success criteria
- Non-negotiables and constraints
Source from: motivation.md â†’ Definition of Success,
             Non-Negotiables, External Constraints

### What â€” Data
- List of things that exist in this business context
- Do not define attributes yet â€” names and brief descriptions only
Source from: domain.md â†’ Core Entities

### How â€” Function
- List of things the business does at the highest level
- Processes and activities, not system functions
Source from: journeys.md â†’ All Journeys (prioritised)

### Where â€” Network
- Locations, geographies, or organisational units involved
- Where data originates and where it is consumed
Source from: people.md â†’ External Parties,
             scope.md â†’ Existing Systems and Integrations

### Who â€” People
- All roles and actors at the business level
- External parties and systems
Source from: people.md â†’ User Types, External Parties

### When â€” Time
- Business cycles, deadlines, time constraints
- Frequency of key processes
- Long-running or time-sensitive journeys
Source from: journeys.md â†’ Long-Running Journeys,
             Automated Journeys

Write Row 1 to spec/zachman.md

Tell the human:
"Row 1 is complete. Please review before I proceed to Row 2.
This row defines the scope boundary â€” anything not here
is out of scope for the system."

Wait for review confirmation.

---

## Row 2: Business (Conceptual)

Source: spec/elicitation/domain.md,
        spec/elicitation/journeys.md,
        spec/elicitation/people.md,
        confirmed Row 1

### Why â€” Motivation
- Business rules that govern behaviour
- Policies and principles that constrain decisions
- What must always be true
Source from: domain.md â†’ Invariant Rules

### What â€” Data
- Conceptual entities with key attributes
- Relationships between entities
- Lifecycle states per entity
Source from: domain.md â†’ Core Entities, Entity Attributes,
             Entity Lifecycle

### How â€” Function
- Business processes decomposed from Row 1 functions
- Process steps, inputs, outputs
- Decision points within processes
Source from: journeys.md â†’ Primary Journey,
             All Journeys, Failure Paths

### Where â€” Network
- Business locations and organisational units in detail
- Where processes execute
- Where data lives at the business level
Source from: people.md â†’ External Parties,
             scope.md â†’ Existing Systems and Integrations

### Who â€” People
- Roles with responsibilities defined
- Who owns what data
- Who triggers which processes
Source from: people.md â†’ Data Ownership and Access,
             Responsibility and Notification

### When â€” Time
- When processes are triggered
- Business events and their timing
- SLAs or time constraints at the business level
Source from: journeys.md â†’ Automated Journeys,
             Long-Running Journeys

Write Row 2 to spec/zachman.md

Tell the human:
"Row 2 is complete. This is the most important row â€”
it defines your business rules and domain model.
Please review carefully before I proceed to Row 3.
Weaknesses here will propagate into the system design."

Wait for review confirmation.
Do not proceed to Row 3 until explicitly confirmed.

---

## Row 3: System (Logical)

Source: confirmed Row 1, confirmed Row 2,
        spec/elicitation/ all files,
        agents/architecture.md if it exists,
        agents/microservice-strategy.md if it exists

### Why â€” Motivation
- System rules that implement business rules from Row 2
- Validation logic, constraints, and system policies
- How business invariants are enforced technically

### What â€” Data
- Logical data model
- Entities, attributes, types, relationships
- Data owned by each service if microservices apply

### How â€” Function
- System functions that implement Row 2 processes
- API operations and service capabilities
- How business journeys map to system operations

### Where â€” Network
- Logical system architecture
- Service decomposition if applicable
- Integration points with external systems

### Who â€” People
- System roles and permissions
- Authentication and authorisation model
- How business roles map to system access

### When â€” Time
- System events and triggers
- Async vs sync operations
- Scheduled processes and their cadence

Write Row 3 to spec/zachman.md

Tell the human:
"Row 3 is complete. Please review â€” this row is where
business concepts become system design.
Check that every Row 2 concept is represented here
and that nothing in Row 3 has no Row 2 parent."

Wait for review confirmation.

---

## Row 4: Technology (Physical)

Source: confirmed Rows 1-3,
        agents/architecture.md,
        agents/microservice-strategy.md,
        agents/principles.md

Note: Row 4 may be partially populated at this stage.
Mark cells that require Phase 2 decisions as
[NEEDS DECISION: describe what must be decided]

### Why â€” Motivation
- Technical constraints and non-functional requirements
- Performance, security, availability targets

### What â€” Data
- Physical data model
- Database technology choices
- Schema decisions where made

### How â€” Function
- Technology stack per service or component
- Framework and library choices where decided

### Where â€” Network
- Infrastructure topology
- Deployment targets
- Network and security boundaries

### Who â€” People
- Technical roles and access controls
- Deployment and operations responsibilities

### When â€” Time
- Technical SLAs
- Batch windows and processing schedules

Write Row 4 to spec/zachman.md
Mark any undecided cells clearly as [NEEDS DECISION]

---

## Rows 5-6: Out of Context / Functioning Enterprise

Tell the human:
"Rows 5 and 6 represent implementation-specific detail
that is best populated during Phase 2 as build decisions
are made. I have created placeholder sections in
spec/zachman.md for these rows.

They will be populated via complete-zachman.prompt.md
as gaps are resolved during the audit phase."

Write placeholder sections to spec/zachman.md:
  ## Row 5: Out of Context (Detailed Design)
  [To be populated during Phase 2]

  ## Row 6: Functioning Enterprise (Implementation)
  [To be populated during Phase 2]

---

## Final Step

After all rows are written, produce a population summary:

  ## Zachman Population Summary
  - Cells populated: [n]
  - Cells with [NEEDS DECISION]: [n] â€” list them
  - Rows reviewed and confirmed: [list]
  - Rows needing further review: [list]
  - Ready for audit: yes / no

Tell the human:
"spec/zachman.md is ready.
Next step: run #validate-zachman.prompt.md
to check internal consistency before Phase 1 audit."

