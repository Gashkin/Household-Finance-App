# GBD-20 — Review and reconcile activity

Use **Money position** to add accounts and review manual/CSV source facts. Use
**Review & reconcile** to resolve classifications and relationships before
confirming a statement balance and completing a dated household review.

## Operator journey

1. Filter owned activity by account, dates, issue and risk. High risk includes an
   unmatched transfer or an amount of at least €1,000. Source lookalikes remain
   separate when their stable references differ. Pending and provisional facts
   remain visible; a suggestion never confirms them automatically.
2. Create categories and one level of subcategories. **Interest & Fees** is the
   suggested category for bank/card fees and interest. Assign top-level monthly
   budgets; subcategory budgets are not added again to the household total.
   Rename keeps the stable category identity. Retirement preserves historical
   assignments. Merge explicitly moves owned, unprotected assignments and
   disables affected rules until they are reviewed again. Move child categories
   before merging or retiring their parent.
3. Confirm each source-backed classification with a reason. Optionally offer an
   exact-description rule after a confirmed correction. Approve that rule as a
   separate decision. Approved rules only suggest: exact match is labelled high
   confidence, conflicting rules low confidence, and no approved match none.
   These are deterministic explanations, not statistical probabilities or AI.
4. Link booked equal/opposite amounts across two owned accounts as a transfer;
   card settlement requires one cash and one debt account. Link a refund to the
   original debit in the same account. Reversals may oppose an original debit or
   credit. The cumulative absolute refunds/reversals cannot exceed that original
   amount. Transfers are excluded from income/spending. Debit refunds reduce
   spending and credit reversals reduce income; balances always retain both
   signed source entries. An absent counterpart stays in the issue queue.
5. Compare each account against its dated statement in **Money position**. A
   difference needs explicit acceptance and statement evidence before an
   adjustment can be recorded. Reconciliation protects entries through that
   date, including later backdated imports and classification changes. To
   correct protected facts, confirm your password in **Household access**, then
   reopen that account with a reason in **Review & reconcile**. Correct linked
   facts only after explicitly unlinking them. Reconcile again afterwards.
6. The guided review shows local import/review freshness, unresolved items,
   statement checks, month spending against the category budget, and the saved
   forecast's evidence/warnings/shortfall and reserve breach dates. Local review
   timestamps do not prove bank freshness. Check the actual source yourself.
   An absent forecast or budget remains explicitly unknown. Review completion
   records that these checks were acknowledged; it never resolves a forecast
   risk. Pending/unresolved activity or unreconciled owned balances block
   completion. Later changes do not rewrite earlier completed review evidence.

## Evidence, privacy and recovery

Every mutation records actor, reason/source and before/after evidence. Account
audit history is available to its owner without the old 100-event truncation.
Classification, rule and completion evidence is local to the owning editor.
Shared readers receive the permitted financial facts with relationship/category
identifiers removed; they cannot inspect private counterpart audit payloads.
Account and record revisions reject stale decisions, under the existing
transaction-lifetime workspace mutation guard. A dated completion also checks
the owned audit revision count before recording its evidence.

Flyway V4 adds review metadata without changing original amounts/references.
Existing activity starts provisional. Encrypted archive version 7 includes
categories, rules, classifications, relationships and completed reviews;
versions 1–6 remain recoverable with provisional defaults. Restore retains the
existing identity/session and recovery protections. The existing 10 MiB
recoverability admission applies to the new records and audits too.

The review workspace uses the canonical ledger for every financial fact and
mutation. Legacy planner/import history is retained as compatibility evidence;
it is not imported automatically or mixed into reviewed cash. GBD-15's retired
legacy matching routes remain retired. No duplicate security-remediation path,
bank connection, remote AI classification or real household fixture is added.

## Verification and operating context

Read live Product Build authority and GBD-20 acceptance/ownership before work,
along with the GBD-16 dependency and GitHub PR #27 integration. Integration base:
`ef0c702ebec03d873214953b10f04a9e6426281d`. The successful create-only GitHub
branch claim precedes the Notion claim confirmation. Product Build's current
one-opening-review limit supersedes the coordinator's stale Copilot gate.
Human approval is required for merge; passing tests do not approve real data.

Automated coverage includes legitimate lookalikes, inactive/approved suggestions,
stale rule/account revisions, transfer/card totals, cumulative partial refunds,
protected corrections, merge lineage, shared-reader privacy and concurrent
classification. File-backed tests verify restart, encrypted recovery, actual V6
compatibility and additive V3 database migration. Desktop/mobile browser coverage
exercises classification, separate rule approval, protected-route denial,
explicit reopening and actor-aware audit history.

Dependencies stay local: `frontend/node_modules/`, frontend build output,
TypeScript build caches, Maven targets and browser artifacts are ignored.
`package.json` and the lockfile define reproducible installation. The old
`feature/work_order_001` checkout predates the dependency-cleanup integration;
it must not be used as the base for new delivery.

Opening implementation local verification: backend 208 discovered, 192 passed, 16 platform skips;
frontend production build and 20 transport/domain tests passed; full browser
suite 37 passed and one intentional mobile recovery skip. Stage `frontend/dist`
in `backend/target/classes/static` as CI does before the packaged single-origin
browser tests. The review test closes its session instead of exhausting the
existing 32-session admission cap.

The unchanged frontend dependency manifest/lockfile initially failed the high
severity npm advisory gate: transitive picomatch
[GHSA-fc5q-39xc-vjpf](https://github.com/advisories/GHSA-fc5q-39xc-vjpf), reviewed
17 November 2026, affects versions through 4.0.7 and lists no patched release.
The five npm findings are that package and its build-tool dependants. On
18 November the owner explicitly approved a temporary, scoped exception after
the build-only reachability assessment. The advisory still exists; it is not
described as patched or absent.

`AnalysisScripts/check_frontend_advisories.mjs` evaluates the live npm report.
Only this precise advisory and findings derived exclusively from it qualify,
using the exact development package versions recorded in
`ops/security/npm-advisory-exception.json`. Production dependencies, other
high/critical advisories, changed dependency scope/versions, malformed reports,
unresolved provenance and failed network scans still block. A visible warning
names the exception and its expiry. The exception expires at 00:00 UTC on
18 December 2026 and also fails if the registry's latest Picomatch release
changes from 4.0.7, requiring upgrade or renewed assessment. A clean audit passes
without using the exception; remove the policy when it is no longer needed.
No dependency downgrade or additional review is requested. Hosted verification
must report the actual current-head result.

The single opening review at `78518d7` returned **UNABLE TO VERIFY**, including
two confirmed integrity findings. Failing-before regressions reproduced both.
Relationship review now preserves the source row's original kind, so an
unchanged CSV/bank refresh remains a duplicate after linking, reconciliation and
restart. Relationship-aware ledger/guide totals and member matching interpret
the separate link metadata; linking cannot turn settlements into purchases.
Unlink also retains the original source kind. Adjustments are rejected on both
sides of a proposed link before any mutation. Added regressions cover transfer,
card-settlement and partial-refund replay, real amount/status conflicts, durable
replay, member match invalidation and atomic counterpart-adjustment rejection.
Owner verification and current-head CI are recorded in PR #29. The opening SHA
and verdict remain unchanged; no additional independent review is requested.
