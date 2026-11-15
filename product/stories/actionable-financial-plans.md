# Financial plans and follow-through (GBD-12)

Use **Debt decision → Compare scenarios → Save comparison to financial plan** to retain a server-calculated debt report with its assumptions and cash provenance. Or open **Financial plans → New financial plan** for another household decision.

1. Describe the goal, dated source evidence and assumptions. Compare at least two options with impacts and uncertainty. Save a proposal.
2. Choose an option, explain why, set a review date and one or more dated follow-ups, and explicitly confirm the decision.
3. Record observed outcomes when you complete follow-ups. Due/overdue dates help identify what to revisit.
4. Edit evidence/options and save to return to a proposal. The previous decision, actions, outcomes and evidence remain in **Decision history**. Record a fresh decision after reassessing the new position.

No payment, provider refresh, pension instruction, tax submission or household policy change is executed. Manual option descriptions are operator estimates; only the attached debt comparison uses the existing deterministic backend calculation. The frozen report is historical evidence and does not silently recalculate when ledger/forecast evidence changes. Revised option descriptions are separate from that original report. Source dates and uncertainty remain visible. A due review prompts reassessment; it is not renewed permission to act.

## Operation and recovery

The existing authenticated, loopback-only workspace protects `/api/plans` and all financial endpoints. No new provider or cloud calls, credentials or real-data fixtures. Plans and their complete history persist in the existing H2 database. Live database protection still depends on private OS permissions/device encryption. Encrypted backup **v4** adds `DECISION_PLANS`; v1/v2/v3 manifests remain supported and restore with no financial plans. Restore remains empty-workspace-only and transactional. Exported report JSON is unencrypted and must be stored privately.

Plan edits, decisions and outcomes use optimistic revisions; stale browser tabs cannot overwrite a newer revision. Failed/unknown requests are never automatically replayed. Reload the saved state before retrying an uncertain save. Switching plans/reloading discards unsaved edits; the UI warns when edits exist. Revisions preserve full bounded history rather than overwriting prior reasoning. Local limits: 100 plans, 200 history entries per plan, 20 evidence items, 10 options and 20 follow-ups per decision; bodies at most 256 KiB. Retain completed plans instead of silently pruning history.

## Delivery gate hygiene

Trusted-main delivery checks validate story identity and exclusivity before review readiness. A separate **Story merge readiness** check remains `in_progress` for drafts, missing reviews, unresolved threads or current-head CI in progress; invalid identity, excess reviews or failed CI remain blocking failures. `ready_for_review`, conversion back to draft and PR CI completion refresh readiness. Stale completed CI events are ignored. Only current-head evidence may complete the readiness check. GPT receipts and ordering still require owner verification; the executable gate does not claim to check them. It never requests a reviewer or merges.

The token-bearing workflow checks out **main only**, never PR code. Its checks permission permits publishing the readiness check; contents/PR/actions remain read-only. This PR cannot safely run its own privileged new gate code before human integration: existing-main behavior on this PR is the old implementation. Activation and a live subsequent draft/ready event remain to verify after human merge. Repository branch protection has not been changed to require the new named check; do not claim automatic merge enforcement where only owner verification exists.

## Verification

Synthetic API/domain tests cover goal-to-choice-to-outcome-to-revision, strict request types and authentication, invalid decisions, concurrency and backup versions. File-backed tests close/reopen both original and recovered databases with a saved decision/history. Desktop/mobile browser tests cover debt seeding, decisions, outcome, reload, revision invalidation, stale-tab rejection, intact history and ordinary plan creation. Coordinator tests cover draft pending states, ready events, CI completion, invalid identity and excess reviews.
