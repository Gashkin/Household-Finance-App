# Production deployment commissioning (GBD-30)

Canonical requirements, plan and actual evidence: [GBD-30 in Notion](https://www.notion.so/41ad3419fef081f2a6a6f653a2bbc6d9).
This is an operator runbook, not proof that a production instance is ready.

## Establish the actual host boundary

Run the read-only `windows/Inspect-DeploymentHost.ps1` under each actual development,
agent and runner logon. It emits aggregate metadata, never identity values or file contents.
Failure to obtain encryption or runner-owner metadata means UNKNOWN, not PASS.
No running runner is not proof that another configured or scheduled privileged runner
cannot start. Group enumeration is local-only and does not establish all effective
nested/domain/elevation rights. Inventory those and host/VM management separately.

A runner or excluded development identity with administrative control can cross ordinary
account ACLs. A new standard user or VM on an administrator-controlled host is insufficient
against that administrator. Use a separately trusted device/management boundary or remove
and verify the excluded identities' privileged capabilities before introducing credentials.
Do not stop or reconfigure unrelated runners automatically.

Use a short private installation root outside repositories/synced folders. Establish the
runtime standard logon, protected browser profile, volume encryption and encrypted recovery.
Only synthetic fixtures may be used for commissioning until B1–B4 pass. Existing
`Test-IdentityBoundary.ps1` Runtime/Excluded modes verify the actual runtime tree and all
five denied-access probes; this discovery script does not replace them.

## Source and package hygiene

Run `python AnalysisScripts/audit_repository_hygiene.py` from the repository root.
It rejects tracked installed dependencies, generated outputs, release archives and common
private runtime material without printing file names or contents. Intentional source,
lockfiles, migration SQL and reviewed synthetic source fixtures remain versioned.
The rule examines the Git index: .gitignore alone cannot remove previously tracked files.
Preserve local files when untracking an accidental artifact.

Build from a clean checkout at an exact commit with the pinned toolchain. Verify the ZIP,
manifest and all intended payload hashes before execution. Source hygiene is not a secret
scan, historical containment or a package-content guarantee; verify those separately.
Ignored stale build contamination must remain covered by release tests.

## Provider evidence request — prepared, not sent

Before production key provisioning or account linking, obtain non-secret written/control-plane
evidence for the intended application. Ask the provider to confirm:

- Production AIS-only services and enforceable denial of PIS/write features, including
  whether a stolen application signing key can self-enable broader services.
- A way to establish these permissions before any account-linking activation step.
- Irish personal-use eligibility, applicable costs/terms/privacy/retention and the supported
  AIB account types. Do not assume credit-card support.
- Key/session revocation and the specific incident/rotation process.

Reference the API's application `services` metadata and its separately enabled production
PIS model. Generic documentation does not prove this application's permissions.
Do not include a signing key, certificate private part, bank account identifiers, real
financial records or existing session references in the request. Do not issue payment
requests to test denial. This file does not authorise sending a message to the provider.

Primary references (checked 17 November 2026):
[API reference](https://enablebanking.com/docs/api/reference/),
[FAQ](https://enablebanking.com/docs/faq/),
[Terms](https://enablebanking.com/terms/).
Restricted account linking is itself a gated external action. If the provider cannot
supply prerequisite evidence without linking, keep activation Blocked rather than bypass
the sequence.

## Current release limits and emergency operation

Both unconditional Windows and direct-Java production holds remain in place. This runbook
does not remove them. Live activation requires a separately tested, exact-release readiness
change within this story, actual B1–B4 evidence and specific owner consent.
Keep R1 explicit: current workbook preflight does not impose an absolute POI heap/deadline.
Do not accept arbitrary third-party workbooks in a real-data environment without a verified
restriction/containment disposition. No residual-risk acceptance is inferred here.

Reuse the existing independent audit and owned N1 fix evidence. Do not create a new audit
round simply to refresh a receipt. F6 historical review reconciliation remains an explicit
inherited finding and cannot be resolved by deleting reviews or silently reducing gates.

On an incident, stop the instance, use `Disable-Production.ps1`, then follow provider/bank
revocation from the owner-controlled account. Preserve records and encrypted recovery.
Never run an older binary against a migrated live database.
