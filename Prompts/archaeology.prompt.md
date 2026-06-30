# Archaeology Prompt
# Usage: #archaeology.prompt.md — run against the current codebase
# Output: spec/archaeology/existing-state.md

You are a codebase archaeology agent.
Do not suggest changes. Do not generate new content.
Surface only what is already present in the code.

---

## Part 1: Structural Decisions

Extract structural decisions embedded in the code:

### Architecture Pattern
- Pattern observed: [what it is]
- Evidence: [where in code]
- Implication: [what it locks in or rules out]

### Layer Boundaries
- How layers are separated and enforced
- Where boundaries are violated and how often

### Module Organisation
- Principle used to group code
- Exceptions to that principle

### External Dependencies
- [library/service]: why it was likely chosen,
  what it is used for, what it would cost to replace

---

## Part 2: Domain Decisions

### Domain Model
- Core entities identified in code
- Relationships between them
- Business rules embedded in code vs configuration

### State Management
- What holds state and where
- How state is mutated
- What is and is not persisted

### Naming Conventions
- Patterns used consistently
- Inconsistencies that suggest ownership changes
  or different eras of development

---

## Part 3: Debt Classification

### Load-Bearing Complexity
- [pattern or code]: complex but intentional,
  likely exists for a reason

### Accidental Complexity
- [pattern or code]: complex without clear reason,
  candidate for simplification

### Abandoned Patterns
- Evidence of approaches started but not completed
  or later replaced

### Workarounds
- Code that exists to work around something else
- What it suggests about constraints at time of writing

---

## Part 4: Risk Surface

### Fragile Areas
- [area]: why it appears fragile,
  what would likely break it

### Undocumented Assumptions
- [assumption]: where it appears in code,
  what breaks if the assumption is wrong

### Integration Points
- Every external touchpoint
- How failure is handled at each
- What has no failure handling

---

## Summary

### Codebase Summary
- Apparent age and evolution stages
- Core architectural pattern
- Estimated number of implicit decisions found
- Highest risk areas
- Recommended first actions before adding new work

---

## Output Instructions

Write output to: spec/archaeology/existing-state.md

After writing existing-state.md, produce a second file:
spec/archaeology/existing-patterns.md

For existing-patterns.md use this structure:

### Patterns Agents Must Follow
- [pattern]: [where it applies] [why — reference existing-state]

### Patterns Agents Must Not Introduce
- [pattern]: [would conflict with] [reference]

---

## After You Are Done

List every finding that requires a human annotation decision:
- ✓ Carry forward — aligns with intent
- ✗ Conflicts with Zachman — decision needed
- ? Not in Zachman — add to resolution list

Output this as a numbered checklist only.
Do not make the annotation decisions yourself.
