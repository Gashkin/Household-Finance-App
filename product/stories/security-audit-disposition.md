# GBD-15 independent audit intake and disposition

Source: separate local task **GBD-15 independent security audit**,
`01a1e195-ecd7-7723-8f01-7885fa2a7ca1`.
Reviewed baseline: `9f607c4d2fbea6224ae47671c52cbf3a3e3e0845`.
Canonical story: https://www.notion.so/419d3419fef08126ba7ce9278f572384

## Independent report and intake state

The complete [independent report](https://www.notion.so/41ad3419fef081509c03ee1adf09c0ba)
was fetched on 16 November 2026. Verdict: **CHANGES REQUESTED** for the baseline;
production readiness: **UNABLE TO VERIFY**. Its original findings, coverage,
reproduction evidence and limitations remain authoritative audit evidence. This
register tracks implementation disposition; it does not replace that report or
turn provisional checks into an independent final-head PASS.

The owner directed audit incorporation before proper testing on 16 November.
The local package command was interrupted. Previous build/regression results are
provisional owned evidence. No complete package, independent final-head PASS,
production readiness, account activation or story completion is claimed.

| Audit finding | Synthetic baseline proof | Current disposition | Required verification after remediation |
| --- | --- | --- | --- |
| F1 — High: Consumer summary invents actual income and spendable money | Empty planner reports actual inflow 3200 and allowable spend 3077.69 | Prepared, unverified: retired manufactured summary; API returns null monetary fields / UNAVAILABLE and UI links to reviewed dated evidence | Empty and populated workspace HTTP/UI regressions; date/source/unknown state must remain explicit |
| F2 — Medium: Legacy CSV replay applies one debit twice | One -10 row accepted/resolved twice changes account 100 to 80 | Prepared, unverified: unsafe legacy financial mutation HTTP routes return 410 without service calls; matching actions removed. Session-only acceptance is explicitly labelled. Durable Money position manual/CSV remains the financial route | Duplicate selections/requests, cross-import facts, restart, failure rollback and maintained modern manual/CSV fallback |
| F3 — Medium: Non-plan growth disables encrypted backup | 400 legal cash-position snapshots commit, then full backup is rejected | Candidate in `e6f54a8`: application-managed transaction admission and full-budget validation; final independent verification pending | Exact cash-snapshot reproduction, mixed ledger/audit/migration growth, concurrency, rollback and encrypted recovery |
| F4 — Medium: POI accepts ambiguous duplicate worksheet ZIP entries | Two same-name sheet entries containing 100 and 999 are accepted, yielding 999 | Prepared, unverified: POI 6.0.0 and bounded shared OOXML preflight before preview/map/validate; adversarial real-upload-path regressions added. Absolute parser CPU/heap containment remains for independent assessment | Exact application upload-path rejection, normal workbooks, bounded sheets/rows/cells/artifacts and parse time/memory |
| F5 — High remediation priority: untriaged dependency advisory backlog | 101 Maven coordinates queried, 17 affected coordinates / 92 distinct advisory IDs; npm 7 affected packages | Prepared, unverified: Boot 4.1.2 / POI 6.0.0 platform migration, Vite 8.3.8. Jackson 2 compatibility module intentionally preserves backup/decimal contracts; deprecated module support and exact resolved advisories need verification. CI pins Node 22.23.5 / Temurin 21.0.13+9 / Python 3.12.16; local JDK and Maven inventory still pending | Current Maven/npm scans with per-advisory fix/reachability decisions and parser/auth/HTTP/recovery/package regressions |
| F6 — Medium: inspected main ruleset does not enforce CI/review gates | Required approving reviews 0; no required-status-check rule. Separate branch-protection inspection returned 403 | Open external configuration/evidence gate: independently inventory effective protections; prepare intended checks/approvals for owner-authorised configuration. Do not weaken future gates to clear a historical receipt mismatch | Reject missing/stale/failing evidence; reconcile baseline's two-review receipt failure with its authorised exception |
| F7 — Medium: package hashes do not authenticate provenance; stale ignored output may be packaged | Package supplies its own verifier/hashes; Maven packaging omits clean | Prepared, unverified: clean generated outputs, Maven clean, immutable action SHAs and exact CI runtime versions; workflow publishes source-bound inner ZIP digest separately in authenticated job summary before package script execution. Actual runner trust, final artifact verification and installed integrity remain open | Verify downloaded/installed artifact/runtime against the accepted release; package self-hashes alone are insufficient |

## Other audit evidence requiring final disposition

- Baseline npm scan: seven affected packages, five High and two Moderate. Candidate
  Vite/plugin-react/lockfile replacement is in the branch. Clean npm output alone
  does not resolve backend or release risks.
- Independent Java advisory inventory queried 101 coordinates. Preserve exact
  versions, scopes and advisory IDs; distinguish scanner matches from reproduced
  reachable defects and establish patched, supported runtime dependencies.
- Auditor's live GitHub inspection found absent enforced CI/review requirements.
  Readiness automation and written rules are not equivalent to enforced protection.
- Actual Windows production identity isolation/encryption, historical exposure
  containment and specific provider-enforced AIS-only/stolen-key entitlement
  remain external evidence gates. No code-only waiver is implied.

## Mandatory release gates

| Gate | State and evidence owner |
| --- | --- |
| B1 — Historical exposure/containment, High confidentiality concern; extent unknown | Unverified. Owner-private classification/containment and issuer rotation/revocation evidence; public history and zero reported forks do not establish containment |
| B2 — Actual Windows protection | Unverified. Dedicated runtime identity, real excluded-identity access denial, encryption, trusted host administration, protected browser/backups and exact-release synthetic install/update/recovery |
| B3 — Specific provider AIS-only/stolen-key rights | Unverified. Independent actual-application entitlement, inability to self-enable payment capability, Irish eligibility, personal-use terms, privacy/retention and account coverage. Public generic documentation is insufficient |
| B4 — Final assembled release and activation | Unverified. Independent exact-final-head review, exact-head CI and Windows synthetic evidence; only then the story's owner-only consent sequence. Windows and Java hard holds remain intact |

## Additional risk disposition required by the report

- AI recipient/retention disclosure and a minimised preview before any real-evidence
  opt-in; regex masking is not de-identification. Packaged runtime keeps AI disabled.
- Legacy doubles/permissive validation still require review. Follow-up remediation
  adds complete before/after planner-state checks to workbook execution/rollback,
  consumes validation tokens once, expires them after five minutes and bounds
  pending reviews to four. Real-persistence/controlled-clock regressions passed
  below; modern reviewed ledger controls do not protect legacy routes.
- Live database upgrade/downgrade safety: `ddl-auto=update` is not a versioned
  migration or old-binary rejection boundary. Verify failure/recovery on disposable
  exact releases before deployment.
- Plaintext live H2/process-memory secrets, ACL check/use races, same-user and
  administrator compromise remain host/trust-boundary concerns; encrypted archives
  and same-user DPAPI do not resolve them.
- Mutable action tags, package lifecycle scripts, JDK/build-plugin inventory and
  actual runner/automation administration need explicit supply-chain disposition.

The audit performed bounded manual/source searches, version scans and four
synthetic probes, not exhaustive SAST or a commercial penetration test. It did
not rerun browser/owner-device installation tests or prove live provider rights.
Preserve these limits during final re-verification.

## Execution order

1. Incorporate the independent consolidated report without losing findings,
   severity, coverage or blind spots.
2. Give every finding a concrete fix, justified disposition or explicit unresolved
evidence owner/gate; preserve one canonical story/branch/PR.
3. Run focused regressions and full build/browser/Linux/Windows verification on
   the corrected exact commit.
4. Obtain independent re-verification of security-sensitive fixes and the final
   assembled release. Keep production holds until all mandatory pre-authentication
   evidence passes, then follow the canonical story's owner-consent sequence.

Related implementation evidence:
[Production security assessment](production-security-assessment.md).

## Remediation checkpoint — source prepared, verification pending

F1–F5/F7 changes above are candidates, not closed findings. The separate audit is
fully incorporated before a new focused regression pass. F6 and B1–B4 remain
explicit external gates. Additional legacy amount-validation, database
migration/downgrade, host isolation and AI opt-in risks remain unresolved and
block production readiness; they have not been silently waived or promised to
an unnamed future story. The production holds remain enforced.

Official version sources inspected: [Spring Boot releases](https://github.com/spring-projects/spring-boot/releases),
[Apache POI](https://poi.apache.org/),
[Temurin 21 releases](https://github.com/adoptium/temurin21-binaries/releases),
[Node 22 tags](https://github.com/nodejs/node/tags),
[CPython tags](https://github.com/python/cpython/tags).

## Owned remediation verification — 16 November 2026

These checks ran after audit incorporation, on the uncommitted remediation tree
following `e6f54a88517806eff95b245866deb8bfdba7449a`; the subsequent source
checkpoint records those changes. They are owned local results, not independent
exact-head review, hosted CI, or an assembled release test.

- Focused audit regressions: 12 passed. Actual workbook HTTP preview/map/validate
  reject duplicate ZIP parts, extreme sparse references, expanded parts and XML
  entities before persistence; an ordinary synthetic workbook still succeeds.
  Empty/populated legacy summary fields remain unknown. Retired matching routes
  return 410 without service calls. Cash-snapshot growth rejects before backup
  failure, rolls back related rows and remains decryptable/restorable.
- Full Windows Java suite: 137 discovered, 121 passed, 16 OS-conditional skips,
  zero failures/errors. Linux-only protocol/recovery/route evidence is still absent.
  Used local BellSoft Java 21.0.11 and Maven 3.9.12, not the pinned final CI JDK.
- Frontend domain tests: 19 passed. TypeScript/Vite production build passed.
- Synthetic desktop/mobile browser suite: 29 passed, one existing recovery duplicate
  skipped. The mobile consumer unknown-evidence screenshot was visually inspected.
- Maven OSV query: 121 exact resolved dependency coordinates, zero advisory matches.
  [Inventory](evidence/gbd-15-maven-dependencies-2026-11-16.txt) and
  [timestamped scan](evidence/gbd-15-maven-advisories-2026-11-16.json) preserve versions,
  scopes, inventory hash and scanner limits. This does not scan the JDK, plugins,
  GitHub actions or prove absence of reachable unknown/unindexed defects.
- Refreshed npm lockfile audit: zero advisory matches. OKF: zero errors/warnings.

F1–F4 now have passing owned regressions; independent closure is pending. F5 has
passing platform regressions and dependency scan, with bundled runtime/build-tool
and exact assembled artifact evidence still open. F7's changed package builder,
stale-output probes and external-digest sequence have **not** been executed or
independently verified. No new complete package is claimed.

## F6 concrete enforcement gap and proposed configuration

Live GET of GitHub ruleset `27256994` on 16 November still shows `active`, required
approving reviews `0`, resolved threads required, deletion/force-push protection,
and no required-status-check rule. This is the inspected ruleset, not a claim
that all effective repository/organisation protections were visible.

The proposed owner-reviewed configuration is: require current-head `build-and-test`,
`windows-package` and trusted-main `Story merge readiness` checks, restrict their
accepted app identities to GitHub Actions, require the branch to be current with
main, preserve resolved threads/deletion/force-push protections, and require a
distinct authorised human approval. Before enabling, remove the Windows workflow
path filter or establish an always-published check for every eligible PR; prove
the human can approve an independently authored PR, since an author cannot approve
their own PR. Do not configure an impossible self-approval gate or add bypasses
to make the current workflow pass.

Required disposable-PR rejection evidence: missing check, failing check, stale
head/base check, missing independent-review evidence, unresolved threads and absent
human approval. `Story merge readiness` currently binds owned/Copilot evidence but
does not authenticate the independent GPT receipt, so a green check alone cannot
close this gate. The historical two-review main receipt mismatch remains open
pending source-grounded exception reconciliation; no global count rule was weakened.
No repository protection was changed by this checkpoint.

## Follow-up: workbook review and rollback integrity

After `e98284d7f726bf222760b8ee2024c017953d0f31`, workbook validation gained a
five-minute expiry, four-review bound and complete sorted planner-state capture.
Execution consumes its token once and rejects any changed planner state before
replacement. Successful loads atomically retain before/after planner snapshots;
rollback rejects later changes and historical receipts lacking after-state
evidence. The shared write-admission transaction serializes managed writers during
comparison/restoration. External writers/processes remain outside that boundary.

Full Windows backend suite after this follow-up: **141 discovered /125 passed /
16 OS-conditional skips**, zero failures/errors. New tests prove expired tokens
cannot execute/occupy review capacity, changed planner evidence prevents REPLACE,
later edits prevent rollback, and unchanged loads roll back once. OKF remains
zero errors/warnings. Previous browser/frontend evidence applies to the preceding
checkpoint; no new exact-final-head browser/package/independent PASS is implied.
The advisory inventory hash now normalizes CRLF to LF for reproducibility across
Git checkouts; refreshed query still reports zero matches for 121 coordinates.

## Amount, migration and toolchain remediation — source checkpoint

Continued under the same claim after the owner's instruction to proceed. Live
Notion authority/story and GitHub main were re-fetched; main remains
`9f607c4d2fbea6224ae47671c52cbf3a3e3e0845`, with no open story PR.

Strict decimal admission now rejects missing/null, non-finite, fractional-cent,
out-of-range and double-unrepresentable legacy values. The UI sends decimal text.
Workbook amounts use raw numeric values and reject formula caches, malformed and
missing amounts rather than manufacturing zero. Backup import applies the same
legacy amount validation before inserts.

Flyway owns a versioned, seed-free V1 schema; Hibernate only validates. Unversioned
nonempty, future-version and checksum-mismatched databases block startup without
changing synthetic records. Legacy recovery accepts physical column reordering
but still validates exact column identities and rolls back failed imports. Three
older tests that implicitly used a persistent default database now explicitly use
isolated synthetic memory databases. The schema-export-only probe initially failed
its subsequent insert because exporting suppresses database creation; it is not
counted as a passing regression. The first migration run also correctly rejected
those unversioned test databases; the fixture configuration was then corrected.

Verification before packaging: full Windows backend **148 discovered /132 passed /
16 OS-conditional skips**, zero failures/errors. Pinned-toolchain desktop/mobile
browser **29 passed /one existing duplicate recovery skip**. Frontend production
build passed. Refreshed Maven inventory **124 coordinates /zero OSV matches**.
These results do not establish independent closure or arbitrary old-binary safety.
Flyway warns that H2 2.4.240 is newer than its upstream-verified H2 baseline; this
remains explicit compatibility evidence for independent review.

Java/Node archives were downloaded from official release endpoints and verified
against pinned SHA256 values before extraction. Maven 3.10.1 is pinned to its
official SHA512, using a workspace-local preparation script. Release builds enforce
Temurin 21.0.13+9-LTS, Node22.23.5 and Maven3.10.1 and record them in the manifest.
Build and browser resolution now use a single reviewed Maven Central mirror;
dependency-declared vendor repositories cannot redirect release resolution.

The synthetic Windows test now uses a temporary shortcut directory and dynamically
allocated loopback port, so it can avoid the owner's desktop and occupied 8080.
Background helpers are hidden; move/delete targets are checked within the synthetic
temporary instance. Testing a standard identity predicate does not certify actual
production isolation. Updated package assembly and installed tests are still pending
at this source checkpoint. Both production holds remain intact; F6/B1–B4 remain open.

### Standard-user Windows DACL correction

The clean `ccad3f5` candidate assembled successfully with the pinned toolchain,
all 148 backend tests discovered (132 passed /16 skips), and stale resource/Vite
configuration probes excluded. Installed verification stopped while preparing
and re-tightening a deliberately weak synthetic DACL: `Set-Acl` requested the
unavailable `SeSecurityPrivilege`. A native DACL-only weak grant isolated the
same failure in the packaged `Set-PrivatePath`, so this was not treated as a
passing installed test or worked around by elevating the runtime identity.

`Set-PrivatePath` now applies the same owner-only protected access descriptor via
Windows `SetNamedSecurityInfoW`, explicitly selecting owner and DACL flags only,
never SACL/audit flags. The helper pins/frees descriptor memory, rejects null DACLs
and owners, checks native error codes and verifies the private path afterward.
A standard-user disposable-directory probe passed weak-DACL rejection and
re-tightening. Full installed/package re-verification remains required, including
Windows PowerShell 5.1; this security-sensitive native boundary requires the
separate independent review. No real-user path or production permissions were changed.

API reference: [Microsoft SetNamedSecurityInfoW](https://learn.microsoft.com/en-us/windows/win32/api/aclapi/nf-aclapi-setnamedsecurityinfow).
