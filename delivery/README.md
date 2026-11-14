# Reliable autonomous story-to-main delivery (GBD-8)

This operator capability coordinates one Notion story, one exclusive Git claim, one PR and verified integration. It never merges, requests another Copilot review, deploys, deletes claims or executes commands from a story definition.

## Try the complete lifecycle without credentials

Requires Python 3.12+ and no third-party packages:

```bash
python -m delivery.demo
python -m unittest discover -s delivery/tests -v
```

The demo is explicitly a stateful **simulation**, including a simulated human merge. Two actual worker threads race; only one claims the story, one PR exists, and Done appears only after main/artifact/CI/review verification. The suite also runs two workers through separate real HTTP clients against a local GitHub/Notion protocol simulator. It covers interrupted Notion writes, recovery, handoff CAS, stale owners, concurrent publication, deleted branches, review count/thread state, failed CI, missing artifacts, event routing and the PR #16 wrong-base failure.

## Live setup

Use environment credentials scoped to this repository/project. Never commit them:

- `GITHUB_TOKEN` (or `GH_TOKEN`): repository contents and pull-request write access for claim/publication/handoff; read access to PRs, checks and contents for audit. GraphQL is used for paginated thread state and atomic ref compare-and-swap.
- `NOTION_TOKEN`: a Notion integration shared with the Product Build backlog, with page read/update access. It is needed for `claim`, `sync`, `handoff`, `publish` and `finish`, not read-only verification. Connected ChatGPT app credentials are not exported to this CLI. Without CLI credentials, the executor can use the connected tools for claim/sync and consume the CI receipt, following the same protocol; do not imply the CLI has authenticated itself.
- `GBD_CLAIM_TOKEN`: the generated execution claim identity, retained by the owning executor. It is public provenance in Git/Notion, **not an authentication secret** or a security boundary between users with repository write access.

The trusted-main guard uses only its read-only `GITHUB_TOKEN`, with no Notion credential or personal runner. PR code receives no API-token audit step; ordinary build/test checkout retains its existing contents-read permission only. It emits a receipt; it cannot mark Notion Done. No account tokens or repository secrets were created by this change. Live Notion CLI writes require the integration credentials above; their HTTP contract and lifecycle are tested against the protocol simulator, not a newly authenticated live Notion integration.

## Register and claim a feature

Define `.delivery/stories/GBD-N.json` locally before execution. Include `work_id`, Notion `page_id`, canonical `branch`, and `artifacts`: a nonempty list of source/test paths and expected `contains` markers. Keep the entire feature's UI/API/domain/tests in its single PR. GBD-7/8 retain their existing canonical branch names; new Work IDs always derive `story/N-delivery` independent of mutable titles. An arbitrary second slug is rejected.

```bash
python -m delivery.coordinator claim --definition .delivery/stories/GBD-N.json
```

The Notion story must be Ready and unowned, with no existing PR. The coordinator creates a commit containing the claim record, then **POSTs a create-only Git ref**. The successful ref creation wins; pre-checks alone are not a lock. A collision is a rejection, never a reset. Only the winner writes Notion ownership/status and re-reads it before coding. Git/Notion are separate services: if Notion fails, the Git claim stays held, the command stops, and the original token is retained for recovery. Unreferenced losing-worker Git objects are harmless; they do not create branches or PRs.

Persist the returned token in the executor's session context. Fetch and check out the canonical branch. Keep `.delivery/claims/GBD-N.json` in the feature PR. Do not manufacture a new Work ID to bypass an existing claim.

## Resume or explicitly hand off

```bash
python -m delivery.coordinator sync --definition .delivery/stories/GBD-N.json
python -m delivery.coordinator handoff --definition .delivery/stories/GBD-N.json --new-token claim-token:new-executor --reason 'Explicit verified handoff from the owning execution'
```

These use `GBD_CLAIM_TOKEN` or `--token`. Sync recovers an interrupted Notion/PR link write with the same Git-recorded owner. Handoff requires the old owner and a reason, preserves history in Git, and uses GraphQL `updateRefs` with `beforeOid` (atomic CAS). Concurrent changes reject the update. A timestamp or missing chat is not permission to take over. After a handoff's Notion failure, the **new** owner runs sync. Never remove another owner's branch to recover it. Git remains the exclusive guard while Notion catches up; cross-service updates are not claimed to be atomic.

## Publish one reviewable PR

```bash
python -m delivery.coordinator publish --definition .delivery/stories/GBD-N.json --title 'GBD-N: complete user outcome' --body-file /path/to/description.md
```

The body gains exactly one `<!-- gbd:GBD-N -->` marker and its Notion link. Existing same-story or same-branch PRs are reused/reconciled, not duplicated; a closed PR cannot be bypassed with a replacement. Simultaneous same-branch creates are arbitrated by GitHub's one-open-PR constraint. Interrupted responses recover the existing PR. A genuinely new remediation outcome needs its own refined story, as GBD-7 did for stranded #16.

Main is the default target. Intentional stacking needs `--base canonical-parent-branch --parent-pr NUMBER`; the parent must still be open and own that branch. Once a parent merges, retarget the child to main **before** human merge. Do not assume GitHub will retarget a child after a merge into a still-existing parent branch.

GitHub's existing ruleset requests the single Copilot review. There is deliberately no review-request API in this tool. Assess comments, fix valid findings in the same PR, reply with evidence, and resolve original threads through the normal GitHub tools. The gate rejects zero/multiple Copilot reviews and unresolved threads. It does not demand a re-review after fixes or treat every Copilot claim as correct.

## Verify actual delivery

```bash
python -m delivery.coordinator pr-check --definition .delivery/stories/GBD-N.json --pr NUMBER
python -m delivery.coordinator verify --definition .delivery/stories/GBD-N.json --pr NUMBER --receipt /tmp/verified-delivery.json
python -m delivery.coordinator finish --definition .delivery/stories/GBD-N.json --pr NUMBER
```

`verify` is read-only. It requires the canonical PR identity, exactly one story marker, a main-targeted human merge, merge-commit reachability from **current main**, exactly one completed Copilot review, resolved threads, the successful GitHub Actions `build-and-test` check on that exact main SHA, and declared source/test artifacts on main. It hashes artifact contents into the receipt and checks main did not move during verification. Squash/rebase integration uses GitHub's actual `merge_commit_sha`, not an assumption that every original feature commit survives. Missing, pending or failed evidence blocks Done with an actionable message. Artifact markers are evidence of integration, not semantic proof; meaningful tests/browser verification remain necessary.

`finish` additionally checks original claim ownership and Notion's canonical PR link before setting Done with the receipt. It can read the preserved PR-head claim after automatic branch deletion. An executor with connected app tools can instead inspect the read-only receipt and update the same Notion story; preserve its provenance. Neither route bypasses human-controlled merging.

## CI operation and boundaries

- Required CI runs coordinator/protocol tests alongside product tests. It does not expose an API audit token to PR-controlled code. The new trusted-main workflow cannot execute this newly introduced coordinator until GBD-8 is human-merged; that bootstrap activation is explicit. After merge, the main Required CI completion performs the first live integration audit. Standalone verification can also run with an operator's scoped token.
- `Story delivery integrity` checks PR identity, exclusivity and review readiness through supported `pull_request_target` and PR `issue_comment` events, or a `workflow_dispatch` PR-number refresh. It always checks out trusted main; never the PR head/merge code. A pending automatic review blocks readiness. After resolving review threads, update the PR description with the evidence, add the normal disposition comment, or dispatch a refresh. GitHub's review-thread webhook is not assumed to be an Actions trigger. This read-only feedback is separate from `build-and-test`; no branch ruleset was modified or mandatory head-check enforcement claimed.
- A merged/closed event checks integration; a pending main check may initially block it. The subsequent **main push Required CI completion** runs the authoritative audit again and produces `delivery-receipt.json` plus a job summary. Merges to feature branches cannot masquerade as main deliveries. The receipt is deliberately retained even on failure.
- Workflow-run handling checks out reviewed main, accepts only main push CI events and uses read-only permissions. PR-provided metadata is parsed as data; no story-provided shell command runs and no Notion/other long-lived secret enters public PR CI.
- The canonical marker/definition/claim gates cover managed GBD stories. Historic PR #18 is explicitly mapped as GBD-7's migration baseline. Other historical technical PRs are not retroactively represented as completed new stories.

GBD-8's own initial claim was bootstrapped with connected GitHub/Notion tools before this code existed; its record is committed with that fact. This story stays In Review until its human-approved main merge and successful main verification. The GraphQL mergeCommit field normalizes current REST responses that omit merge_commit_sha. The coordinator improves reliability within a shared trusted repository; it is not tenant isolation, access control against repository writers, a hidden background service, or automatic financial-product functionality.

API contracts: https://docs.github.com/en/rest/git/refs · https://docs.github.com/en/graphql/reference/git#updaterefs · https://developers.notion.com/reference/patch-page
