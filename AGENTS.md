# Autonomous delivery

Read the current Notion Product Build operating instructions and canonical story before implementing. Notion is the roadmap/ownership source; GitHub is the code/integration source. One substantial story, one PR; all UI/backend/tests/docs for a coherent outcome belong together. Human alone controls merges.

Use [the delivery coordinator](delivery/README.md) for available stories: `claim`, preserve the execution claim token, re-read Notion ownership, implement on the canonical branch, and `publish` one PR. Existing claims/PRs are owned even after a chat ends. New Work IDs have deterministic `story/N-delivery` branches; GBD-7/8 retain their declared bootstrap names. Never reset/delete/reclaim an existing ref or invent another slug to take over.

Exactly one automatic Copilot review occurs at PR creation. Fix valid findings, verify, reply and resolve original conversations. Do not request incremental re-reviews. Keep public PR CI hosted and read-only; no personal self-hosted runner or long-lived Notion credentials in PR workflows.

After a human merge, verify merge reachability, declared feature/test artifacts, resolved review conversations and the required check on the current main SHA before marking Done. A merged feature-branch PR is not integration. See `verify`/`finish` and CI receipts. Keep claim history; only explicit verified handoff changes ownership.

Record meaningful execution evidence in Notion. Appraise after three newly verified feature integrations since the last appraisal, or immediately after a significant escaped incident. Do not turn planning, test setup, docs or trivial commits into separate PR stories or redundant appraisals. Backend owns financial/business logic; the frontend owns presentation and interaction.

Before promising that scope will be covered in a later story, verify that an actual Notion Product Build story exists and read its current scope, status and dependencies. If missing, create or refine it first. Record deferred requirements, acceptance criteria, blockers and sequencing, link it from the current story, and cite its Work ID and URL in the handover. Distinguish backlog plans from selected execution and verified delivery. Never defer work into an unnamed future story.
