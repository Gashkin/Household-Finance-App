# GBD-15 production security assessment and activation evidence

Canonical story: https://www.notion.so/419d3419fef08126ba7ce9278f572384

This is the implementation session's evidence ledger and audit handoff. It is
**not an independent audit verdict or production authorisation**. Read the live
[Product Build authority](https://www.notion.so/418d3419fef081f79b7bfeeed488eb86)
and story for current policy and acceptance criteria.

## Baseline and ownership

- Baseline: `9f607c4d2fbea6224ae47671c52cbf3a3e3e0845`, human-merged GBD-14 PR #25.
- Canonical claim: `story/15-delivery`; provenance in `.delivery/claims/GBD-15.json`.
- The original development checkout has unrelated uncommitted work. Implementation
  uses a separate checkout; reviewers must inspect an exact committed revision.
- No production-hold removal, real credentials, account linking, banking calls,
  Windows provisioning or history rewrite has been performed in this work.

## Independent audit handoff

A fresh local task, **GBD-15 independent security audit**, was launched on
15 November 2026. Task ID: `01a1e195-ecd7-7723-8f01-7885fa2a7ca1`.
Cloud Work creation returned unavailable for this account. The local task has
read-only source scope, the exact baseline, the live Notion references and the
whole-application acceptance criteria. Starting a task is not a completed review.
Its baseline verdict cannot cover subsequent remediation commits.

The audit scope includes:

| Boundary | Assets and abuse cases | Evidence required |
| --- | --- | --- |
| Browser to loopback server | Session theft/expiry, CSRF, DNS rebinding, origin bypass, malicious input/import, unauthenticated legacy routes | Synthetic HTTP/browser negative tests and exact source review |
| Server to records | Pending versus approved facts, deduplication/conflicts, concurrent writes, stale decisions, backup capacity/integrity | Domain/API tests, restart and complete backup/restore verification |
| Evidence to optional model | Undisclosed financial-data egress, malicious document instructions, secret leakage | Explicit opt-in and minimisation review, synthetic-only outbound tests |
| Server to banking provider | Stolen signing keys, payment entitlements, callback replay, consent expiry/revocation, partial imports | App controls plus independently proven provider-enforced AIS-only registration |
| Windows identities and storage | Same-user malware, agent/runner access, reparse/ACL/race attacks, lost disk, backup theft, administrator/SYSTEM access | Actual production identity, denied-access probes from actual excluded identities, effective ACLs and encryption |
| Source to installed release | Dependency compromise, untrusted CI/runner, replaced artifacts/manifests, update rollback/data loss | Exact-head tests, release provenance/integrity and actual-host synthetic install/update/recovery |
| Repository history to third parties | Historical private records/secrets, forks/copies/caches/artifacts | Private owner assessment, issuer rotation/revocation where needed, sanitised containment evidence |

## Initial implementation-session findings

### Dependency advisories: frontend remediation in progress

Baseline `npm audit --package-lock-only --json` returned seven affected packages:
five High and two Moderate. Affected names were Vite, esbuild, Browserslist,
baseline-browser-mapping, nanoid, PostCSS and source-map-js. Several findings
concern development/build servers and untrusted build inputs; package severity
alone does not establish exploitability of the packaged production application.

Updated Vite to `8.3.8` and `@vitejs/plugin-react` to `6.3.0`, using their compatible
published Node requirements. The lockfile now contains the patched dependency
graph. Repeated live npm audit returned zero advisories. Production build and
all 19 existing frontend tests passed. Browser and independent final-head
verification remain required. Required CI now fails on High/Critical npm
advisories. A failed advisory-service request is not treated as a clean scan.

The baseline synthetic production browser fixture unconditionally used POSIX
permissions and could not start on Windows (`UnsupportedOperationException`).
It now applies an owner-only inheritable Windows ACL when that view is present,
retaining owner-only POSIX permissions otherwise. This changes test setup only;
the production store still validates the real platform permissions.

The local Java 21 Windows backend run discovered 123 tests: 107 passed, 16 were
OS-conditional skips, and none failed or errored. Both Windows ACL/store tests
passed. Linux-only protocol/recovery coverage still needs hosted Linux CI.
All 31 delivery-coordination tests passed with loopback access. Sandbox HTTP
restrictions initially made the simulator fail; the unrestricted synthetic
rerun passed without changing the coordinator.

The corrected synthetic production fixture subsequently started on Windows.
The complete browser suite then stopped during startup because local port 8080
was already occupied; no browser cases completed. No existing application was
terminated to clear the port. Browser verification remains outstanding; do not
represent the successful bundle/unit tests as a browser pass.

Generated `node_modules`, TypeScript build caches and emitted Vite configuration
were tracked at baseline. They are removed from tracking and ignored; local files
are retained. The package manifest and lockfile define installation, and CI uses
`npm ci`. This does not prove upstream package integrity or eliminate supply-chain
risk. Backend dependencies still require current advisory analysis and disposition.

### Historical exposure: not contained

The existing aggregate-only `AnalysisScripts/audit_history_exposure.py` scanned
172 reachable commits and 699 unique non-dependency blobs from a non-shallow local
repository. It counted four SQL paths, four database paths, one generated analysis
path and one workbook path across history. No blobs exceeded its 20 MiB content
limit, and its limited credential patterns found no candidates. No private paths
or blob contents were published.

This is **not proof of absence of secrets or private records**. Binary contents
were not privately interpreted; dependency blobs, hidden refs, deleted remote
objects, forks, caches, artifacts and external copies are not cleared. Removing
files from the current tree does not contain historical exposure. Owner-private
assessment and any issuer-side rotation/revocation remain mandatory. Do not
rewrite public history autonomously.

## External evidence still required

- Separate independent whole-app verdict, remediation disposition and independent
  exact-final-head re-verification; no Copilot-only substitution.
- Actual Windows device encryption and production identity distinct from
  development, coding agents and the runner. Sandbox identity metadata does not
  prove the owner's effective host boundary. Do not provision real keys to obtain
  this evidence. Administrator/SYSTEM residual access must be stated.
- Repository visibility/copies and completed historical containment evidence.
- Specific provider production application entitlement: provider-enforced AIS-only,
  direct stolen-key capabilities, no PIS/write registration, Irish eligibility and
  supported AIB account types. Credit-card coverage is unverified.
- Actual reviewed Windows package install/launch/update/restart and encrypted
  recovery with synthetic records, plus release-bound readiness/negative paths.
- Only after all pre-authentication gates pass: private local provisioning and
  owner-only bank-controlled consent; controlled live import/review/dedupe/revoke
  and forecast verification. Manual/CSV fallback remains available.

## 16 November remediation checkpoint

Reproduced non-plan backup-budget overrun with two failing synthetic regressions:
an oversized write committed, and both concurrent writers consumed capacity that
could hold only one. `WorkspaceMutationGuard` now reserves admission before each
new managed read-write transaction and registers flush/snapshot validation before
commit, releasing admission after commit or rollback. The same complete archive
budget applies to migration artifacts, ledger and other persisted records. Both
regressions now pass, including full encrypted recovery and a later write proving
the rejection released admission. External SQL/file access and multiple processes
remain outside this single-process application control.

Reproduced four legacy import failures: duplicate row selection, concurrent/replayed
acceptance, invalid amounts silently becoming zero, and quoted CSV columns being
misinterpreted. Acceptance now serializes and deduplicates import/row identity,
publishing history only after its receipt commits. The shared bounded quoted CSV
parser validates complete rows, dates, account/description and finite cent-precision
amounts. Resolution now commits related balance/commitment writes together before
publishing history state; a missing-account regression verifies complete rollback.
The legacy history cache remains process-local; this is not a claim of durable
legacy history or a replacement for the reviewed ledger journey.

The synthetic browser backend now uses dedicated port 18080 consistently in its
server, proxy and API helpers, leaving the unrelated application on 8080 intact.
Full local verification after these changes: **130 backend tests discovered,
114 passed / 16 OS-conditional skips**, zero failures/errors; **27 desktop/mobile
browser passes / one existing skip**. Inspected retained mobile synthetic banking
position and desktop financial-plan history screenshots. OKF validation passed
with zero errors/warnings. Linux protocol checks, hosted exact-commit Windows
packaging and independent security re-verification still require separate evidence.

The independent audit task remains active but is waiting for a local tool approval;
no final baseline verdict is claimed. The owner has been notified of that approval
blocker. Findings/remediations above are owned reproduction and verification,
not an independent final-head PASS. Production holds remain intact.

Public provider documentation is research, not proof of a particular registration:
[terms](https://enablebanking.com/terms/) describe limited personal/evaluation use;
[API reference](https://enablebanking.com/docs/api/reference/) documents separate
payment functionality and production application activation. Neither establishes
the actual application's entitlement or permission to authenticate before the
story's audit, host and containment gates pass.

## Release disposition

**Not ready for production activation or story completion.** The source-level
Windows and Java production holds remain in place. Keep this evidence distinct
from the independent report and record exact tested commit/CI receipts before
any release claim. Do not open a premature PR merely to trigger a reviewer.
