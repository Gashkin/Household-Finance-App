# Populate Zachman Prompt
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
Row 1: Scope (Contextual)      ← populate fully first
Row 2: Business (Conceptual)   ← gate before Row 3
Row 3: System (Logical)        ← gate before Row 4
Row 4: Technology (Physical)   ← can be partial
Rows 5-6                       ← defer until Phase 2
```

Within each row, populate columns in this order:
```
Why (Motivation) → What (Data) → How (Function)
→ Where (Network) → Who (People) → When (Time)
```

Why always first — it anchors all other columns in the same row.

---

## Row 1: Scope (Contextual)

Source: spec/elicitation/motivation.md,
        spec/elicitation/scope.md

### Why — Motivation
- Business goals this system serves
- Problems it solves
- Success criteria
- Non-negotiables and constraints
Source from: motivation.md → Definition of Success,
             Non-Negotiables, External Constraints

### What — Data
- List of things that exist in this business context
- Do not define attributes yet — names and brief descriptions only
Source from: domain.md → Core Entities

### How — Function
- List of things the business does at the highest level
- Processes and activities, not system functions
Source from: journeys.md → All Journeys (prioritised)

### Where — Network
- Locations, geographies, or organisational units involved
- Where data originates and where it is consumed
Source from: people.md → External Parties,
             scope.md → Existing Systems and Integrations

### Who — People
- All roles and actors at the business level
- External parties and systems
Source from: people.md → User Types, External Parties

### When — Time
- Business cycles, deadlines, time constraints
- Frequency of key processes
- Long-running or time-sensitive journeys
Source from: journeys.md → Long-Running Journeys,
             Automated Journeys

Write Row 1 to spec/zachman.md

Tell the human:
"Row 1 is complete. Please review before I proceed to Row 2.
This row defines the scope boundary — anything not here
is out of scope for the system."

Wait for review confirmation.

---

## Row 2: Business (Conceptual)

Source: spec/elicitation/domain.md,
        spec/elicitation/journeys.md,
        spec/elicitation/people.md,
        confirmed Row 1

### Why — Motivation
- Business rules that govern behaviour
- Policies and principles that constrain decisions
- What must always be true
Source from: domain.md → Invariant Rules

### What — Data
- Conceptual entities with key attributes
- Relationships between entities
- Lifecycle states per entity
Source from: domain.md → Core Entities, Entity Attributes,
             Entity Lifecycle

### How — Function
- Business processes decomposed from Row 1 functions
- Process steps, inputs, outputs
- Decision points within processes
Source from: journeys.md → Primary Journey,
             All Journeys, Failure Paths

### Where — Network
- Business locations and organisational units in detail
- Where processes execute
- Where data lives at the business level
Source from: people.md → External Parties,
             scope.md → Existing Systems and Integrations

### Who — People
- Roles with responsibilities defined
- Who owns what data
- Who triggers which processes
Source from: people.md → Data Ownership and Access,
             Responsibility and Notification

### When — Time
- When processes are triggered
- Business events and their timing
- SLAs or time constraints at the business level
Source from: journeys.md → Automated Journeys,
             Long-Running Journeys

Write Row 2 to spec/zachman.md

Tell the human:
"Row 2 is complete. This is the most important row —
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

### Why — Motivation
- System rules that implement business rules from Row 2
- Validation logic, constraints, and system policies
- How business invariants are enforced technically

### What — Data
- Logical data model
- Entities, attributes, types, relationships
- Data owned by each service if microservices apply

### How — Function
- System functions that implement Row 2 processes
- API operations and service capabilities
- How business journeys map to system operations

### Where — Network
- Logical system architecture
- Service decomposition if applicable
- Integration points with external systems

### Who — People
- System roles and permissions
- Authentication and authorisation model
- How business roles map to system access

### When — Time
- System events and triggers
- Async vs sync operations
- Scheduled processes and their cadence

Write Row 3 to spec/zachman.md

Tell the human:
"Row 3 is complete. Please review — this row is where
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

### Why — Motivation
- Technical constraints and non-functional requirements
- Performance, security, availability targets

### What — Data
- Physical data model
- Database technology choices
- Schema decisions where made

### How — Function
- Technology stack per service or component
- Framework and library choices where decided

### Where — Network
- Infrastructure topology
- Deployment targets
- Network and security boundaries

### Who — People
- Technical roles and access controls
- Deployment and operations responsibilities

### When — Time
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
  - Cells with [NEEDS DECISION]: [n] — list them
  - Rows reviewed and confirmed: [list]
  - Rows needing further review: [list]
  - Ready for audit: yes / no

Tell the human:
"spec/zachman.md is ready.
Next step: run #validate-zachman.prompt.md
to check internal consistency before Phase 1 audit."
