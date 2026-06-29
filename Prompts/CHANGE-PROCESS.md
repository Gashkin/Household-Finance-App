# The Change Process  
## Evolving an Existing Codebase via Spec-Driven Development  
### A complete walkthrough for making large functional changes to a built system  
  
---  
  
## How To Use This Document  
  
This document is the companion to PROCESS.md. Use it when:  
- A new integration target requires structural changes (e.g., CWO migration)  
- An ADR decision changes the module architecture  
- A batch of new features requires cross-cutting refactoring  
- Existing work orders are obsoleted by new requirements  
  
Do NOT use it for:  
- Greenfield projects (use PROCESS.md)  
- Bug fixes or small enhancements within a single work order  
- Adding tests to existing code (use generate-backlog.prompt.md with existing ops/)  
  
Give this document to a chatbot agent and say:  
  
"I need to make a large functional change. Walk me through the Change Process.  
Here is the analysis: [paste or reference the analysis document].  
Ask me what you need to know. Tell me what to run. Keep me on track."  
  
---  
  
## The Change Pipeline
ANALYSIS
Phase C1: Gap Analysis — what the codebase needs vs what it has

DECISION
Phase C2: Write ADRs — new architectural decisions
Phase C3: Update Zachman — add/modify cells to reflect new decisions

REGENERATION
Phase C4: Regenerate ops/ — full regenerate per PROCESS.md rules

DELTA BACKLOG
Phase C5: Evolve Backlog — generate new items, mark superseded items

EXECUTION
Phase C6: Execute Items — same as PROCESS.md Phase 5


---  
  
## Entry Points  
  
**Have a migration/gap analysis document:**  
→ Begin at Phase C2  
  
**Have new ADRs but haven't updated Zachman:**  
→ Begin at Phase C3  
  
**Have updated Zachman, need new ops/:**  
→ Begin at Phase C4  
  
**Have updated ops/, need new work orders:**  
→ Begin at Phase C5  
  
**Have new work orders, ready to build:**  
→ Begin at Phase C6  
  
---  
  
## Phase C1: Gap Analysis  
  
### Purpose  
Identify what the codebase needs to support the new requirements vs what currently exists.  
  
### Input  
- The trigger document: integration analysis, feature request, architecture review  
- Current codebase state: progress.md, completed work orders, existing tests  
  
### Process  
1. Map new requirements to existing module boundaries  
2. Identify which existing components need modification vs creation  
3. Document the gap as themes (groups of related changes)  
4. For each theme, identify:  
   - What exists and works today  
   - What is missing  
   - What exists but needs restructuring  
   - What becomes obsolete  
  
### Output  
- `specs/[context]/[context]-migration-analysis.md` — full gap analysis  
- `specs/[context]/[context]-codebase-changes.md` — changes needed, grouped by theme  
  
### Gate  
- [ ] Every theme has at least one concrete code-level example  
- [ ] Every "what exists" claim has been verified against actual source  
- [ ] Every "what is missing" claim traces to a specific new requirement  
- [ ] No claims based on assumptions — all verified via code review  
- [ ] Document reviewed by human before proceeding  
  
---  
  
## Phase C2: Write ADRs  
  
### Purpose  
Record the architectural decisions that enable the change.  
  
### Rules  
- One ADR per decision boundary (not one ADR per theme)  
- If an existing ADR is contradicted, write a new ADR that supersedes it  
- ADRs must be numbered sequentially from the current highest  
- ADR format must match existing ADRs in the repo (see adr/ folder)  
  
### Input  
- Phase C1 output (gap analysis)  
- Existing ADRs in adr/ folder  
  
### Process  
For each new architectural decision:  
1. Write ADR-[n].md with: Status, Context, Decision, Consequences, Supersedes (if applicable)  
2. If it supersedes an existing ADR, update the old ADR's Status to "Superseded by ADR-[n]"  
  
### Output  
- New ADR files in adr/ folder  
- Updated status on any superseded ADRs  
  
### Gate  
- [ ] Every new ADR has a clear Decision statement (not a discussion)  
- [ ] Every superseded ADR is marked with new status  
- [ ] No ADR contains [NEEDS DECISION] — that belongs in Zachman  
- [ ] ADRs reviewed by human before proceeding  
  
---  
  
## Phase C3: Update Zachman  
  
### Purpose  
Zachman is the single source of truth. All changes flow from it.  
  
### Rules  
- Add new cells or modify existing cells to reflect new ADRs  
- Do NOT delete existing cells that describe completed/working functionality  
- Mark cells that are being replaced with `[SUPERSEDED by: new cell reference]`  
- New cells must be traceable to a specific ADR  
  
### Input  
- Phase C2 output (new ADRs)  
- Existing spec/zachman.md  
  
### Process  
1. For each new ADR, identify which Zachman cells are affected:  
   - Row 2 (Business Model): new entities, relationships  
   - Row 3 (System Model): new services, boundaries, data flows  
   - Row 4 (Technology Model): new modules, packages, dependencies  
   - Row 5 (Detailed Representation): new algorithms, APIs, schemas  
2. Add new content to the appropriate cells  
3. Mark superseded content (do not delete — it documents history)  
4. Add [NEEDS DECISION] tags for anything the ADRs left unresolved  
  
### Output  
- Updated spec/zachman.md  
  
### Gate  
- [ ] Every new ADR is traceable to at least one Zachman cell change  
- [ ] No existing working functionality was deleted (only marked superseded)  
- [ ] All [NEEDS DECISION] tags have a clear question and context  
- [ ] Zachman reviewed by human before proceeding  
  
---  
  
## Phase C4: Regenerate ops/  
  
### Purpose  
ops/ files are derived from Zachman. They are never edited directly.  
When Zachman changes, ops/ must be regenerated.  
  
### Rules (from PROCESS.md)  
- ops/ files are never edited directly — fix Zachman and regenerate  
- Run #generate-ops.prompt.md for each pass  
- The regenerated ops/ will now contain both existing content AND new content  
- Completed items are not affected — they are already built and tested  
  
### Input  
- Updated spec/zachman.md (Phase C3 output)  
- #generate-ops.prompt.md  
  
### Process  
1. Run `#generate-ops.prompt.md` Pass 1 (all four files)  
2. Run Pass 1 Audit (A, B, C)  
3. Fix any issues in Zachman, re-run Pass 1 if needed  
4. Run Pass 2 (all files)  
5. Run Pass 3 (all files)  
6. Run all audits  
  
### Output  
- Regenerated ops/ files (all passes)  
  
### Gate  
- [ ] All Pass 1 files marked Ready: yes  
- [ ] All Pass 2 files marked Ready: yes  
- [ ] All Pass 3 files marked Ready: yes  
- [ ] No unresolved [CONFLICT] tags  
- [ ] Diff between old and new ops/ reviewed by human  
- [ ] Existing completed functionality still described correctly in regenerated ops/  
  
---  
  
## Phase C5: Evolve Backlog  
  
### Purpose  
Generate only the NEW work orders needed to implement the change.  
Do NOT regenerate the entire backlog from scratch.  
  
### Rules  
- Read progress.md and all existing work orders to understand what is built  
- Items that are completed stay completed — they are not regenerated  
- Items that are now obsolete are marked [SUPERSEDED by ITEM-nnn]  
- New items start numbering from the current highest + 1  
- Every new item must include a "Sample Host Changes" section if it affects the host  
- Every new item must satisfy all rules from generate-backlog.prompt.md (Compilation Closure, Module Bootstrap, Host Wiring, Entity-Type Dispatch, Build Dependency, Algorithm Guidance, Third-Party API)  
  
### Input  
- Regenerated ops/ files (Phase C4 output)  
- progress.md (current state)  
- Existing work orders in backlog/work-orders/  
- Phase C1 analysis documents (for context)  
  
### Process  
  
#### Step 1: Identify Superseded Items  
For each existing incomplete/not-started item:  
- If the regenerated ops/ no longer contains the scope this item was derived from → mark [SUPERSEDED]  
- If a new item covers this item's scope differently → mark [SUPERSEDED by ITEM-nnn]  
- If the item is still valid as-is → leave unchanged  
  
#### Step 2: Identify New Slices  
Compare regenerated ops/ against existing slices.md:  
- New user journeys in data-flow.md that have no corresponding slice → new slice  
- Existing slices whose scope expanded → note expansion  
  
#### Step 3: Generate New Items  
For each new slice or expanded scope:  
- Generate items following the same order: Schema → Logic → Endpoint → Integration → Tests  
- Number sequentially from current highest ITEM number + 1  
- Apply all work order rules from generate-backlog.prompt.md  
- Include "Sample Host Changes" section in any item that modifies library behavior  
  
#### Step 4: Sample Host Integration  
For each new item that changes module structure, adds new services, or modifies existing service APIs:  
- Add a "Sample Host Changes" subsection describing:  
  - build.gradle dependency changes  
  - RaasConfig.java @Bean additions/modifications  
  - SampleHostApplication.java annotation updates  
  - New or modified controllers  
  - Flyway migration additions  
  
#### Step 5: Update Backlog Metadata  
- Update backlog/slices.md with new slices  
- Update backlog/ordered-backlog.md with new milestones and items  
- Update progress.md "Next Item" section  
- Mark superseded items in ordered-backlog.md  
  
### Output  
- New work order files: backlog/work-orders/ITEM-[n].md  
- Updated backlog/slices.md  
- Updated backlog/ordered-backlog.md  
- Updated progress.md  
- Supersede markers on obsolete items  
  
### Gate  
- [ ] Every new item has a spec reference to a regenerated ops/ file  
- [ ] Every new item satisfies all rules from generate-backlog.prompt.md  
- [ ] Every superseded item is marked with [SUPERSEDED by ITEM-nnn]  
- [ ] No new item duplicates scope of a completed item  
- [ ] Every new item that affects the sample host has a "Sample Host Changes" section  
- [ ] Dependency chain is valid (no circular dependencies, no reference to superseded items)  
- [ ] New items reviewed by human before proceeding to execution  
  
---  
  
## Phase C6: Execute Items  
  
Follow PROCESS.md Phase 5 exactly. No changes.  
Use #execute-item.prompt.md per item.  
  
---  
  
## Relationship to PROCESS.md
PROCESS.md (greenfield) CHANGE_PROCESS.md (evolution)
───────────────────────────── ─────────────────────────────────
Phase E: Elicitation Phase C1: Gap Analysis
Phase P: Population Phase C2: Write ADRs
Phase V: Validation Phase C3: Update Zachman
Phase 1: Audit Zachman ─── (skip — Zachman already audited)
Phase 2: Complete Zachman ─── (skip — Zachman already complete)
Phase 3: Generate ops/ Phase C4: Regenerate ops/
Phase 4: Generate Backlog Phase C5: Evolve Backlog (DELTA only)
Phase 5: Execute Items Phase C6: Execute Items (same)


The key difference: Phase C5 is NOT a full backlog regeneration.  
It reads existing state and produces only what is new or changed.  
  
---  
  
## The Ten Rules (extended for evolution)  
  
All ten rules from PROCESS.md apply. Additionally:  
  
11. Completed items are immutable — never regenerate or modify a completed work order  
12. Superseded items are not deleted — they are marked and left in place for history  
13. New items must not break existing passing tests — every item leaves the build green  
14. The sample host must compile after every item — no big-bang migrations  
15. Analysis documents are checked into specs/ — they are inputs to Zachman, not sources for backlog  
  
---  
  
## WHEN THINGS GO WRONG  
  
### A completed item conflicts with new requirements  
Do NOT modify the completed item.  
Write a new item that modifies/extends/replaces the code produced by the completed item.  
The new item's "Inputs" section references the completed item's output.  
  
### A superseded item was partially executed  
If it produced working code that is now wrong:  
- Write a new item that undoes/replaces the incorrect code  
- Reference the superseded item in the new item's context  
  
If it produced working code that is still correct:  
- Mark it completed (not superseded)  
- The new items build on top of it  
  
### The regenerated ops/ lost content for completed items  
This means the Zachman update was too aggressive.  
Restore the Zachman cells that described the completed functionality.  
Regenerate ops/ again.  
Rule: never delete Zachman content that describes working code.  
  
### New items have circular dependencies with completed items  
This cannot happen if completed items are immutable.  
New items depend on completed item outputs (forward reference).  
If a new item needs to modify completed code, it is a "Modify" type item  
that takes the existing file as an input.  
  
### The sample host won't compile mid-migration  
This means an item was missing its "Sample Host Changes" section.  
Fix: add the host changes to the current item (do not create a separate host item).  
Rule 14 exists to prevent this — enforce it at work order review time.  
  
---  
  
## QUICK REFERENCE  
  
| Situation | Action |  
|---|---|  
| New integration target identified | Start at Phase C1 |  
| Gap analysis complete | `Phase C2` — write ADRs |  
| ADRs written | `Phase C3` — update spec/zachman.md |  
| Zachman updated | `#generate-ops.prompt.md` all passes (Phase C4) |  
| ops/ regenerated | `Phase C5` — evolve backlog (delta generation) |  
| New work orders ready | `#execute-item.prompt.md` per item (Phase C6) |  
| Completed item needs modification | Write new ITEM that modifies it — do NOT edit the completed item |  
| Existing items now obsolete | Mark [SUPERSEDED by ITEM-nnn] — do NOT delete |  
  
---  
  
## EXAMPLE: CWO Migration Pivot  
  
This is the concrete example that motivated this process:
Phase C1: cwo-migration-analysis.md + cwo-raas-codebase-changes.md
→ identified 5 themes, 16 new items needed

Phase C2: ADR-042 (module decomposition), ADR-043 (condition format SPI),
ADR-044 (SPI defaults)
→ supersedes parts of ADR-040

Phase C3: Zachman updated with:
- Row 3 Network: raas-core splits into raas-domain + raas-evaluation + raas-management
- Row 3 Function: batch evaluation flow, condition format pluggability
- Row 5 Technology: new operators, Map-backed fact, default SPIs

Phase C4: ops/ regenerated — now contains both MVP content (ITEM-000–044)
AND new CWO-driven content

Phase C5: Evolve backlog:
- ITEM-023–035 marked [SUPERSEDED by ITEM-046–060]
- ITEM-045–060 generated (new work orders)
- slices.md updated with SLICE-12
- ordered-backlog.md updated with new Phase 4 milestone

Phase C6: Execute ITEM-045 → 046 → ... → 060


---  
  
## FILE OUTPUTS CHECKLIST  
  
After running the full Change Process, these files should exist or be updated:  
  
| File | Created/Updated | Phase |  
|------|----------------|-------|  
| specs/[context]/*-analysis.md | Created | C1 |  
| specs/[context]/*-changes.md | Created | C1 |  
| adr/ADR-[n].md (new) | Created | C2 |  
| adr/ADR-[n].md (superseded) | Updated | C2 |  
| spec/zachman.md | Updated | C3 |  
| ops/pass1/*.md | Regenerated | C4 |  
| ops/pass2/*.md | Regenerated | C4 |  
| ops/pass3/*.md | Regenerated | C4 |  
| backlog/work-orders/ITEM-[n].md (new) | Created | C5 |  
| backlog/work-orders/ITEM-[n].md (superseded) | Updated | C5 |  
| backlog/slices.md | Updated | C5 |  
| backlog/ordered-backlog.md | Updated | C5 |  
| progress.md | Updated | C5 |
Commit

git add Prompts/CHANGE_PROCESS.md  
git commit -m "CUSTODY-155456 - Add CHANGE_PROCESS.md: evolution pipeline for large functional changes"  
git push origin devin/1779271476-zachman-library-specs
Important Notes
Do NOT modify any other files (PROCESS.md, generate-backlog.prompt.md, etc.)
Do NOT create any Java source files
Do NOT modify any existing prompt files
The file content above is the COMPLETE file — create it exactly as specified
Use Unix line endings (LF, not CRLF)