# Public repository data hygiene

## Tracked files and exposure
The repository historically included a local H2 database, its trace file, a household workbook, manually supplied SQL account/payment fixtures and generated analysis artifacts. This cleanup prevents those files from remaining in the latest source tree or being accidentally recommitted. **It does not remove them from the earlier Git history or copies already downloaded.**

Do not commit live financial records, statements, bank exports, credentials, private workbook extracts, payment/commitment SQL fixtures, .env files, or database files. Workbook inspection scripts require an explicit local --input path; private source workbooks are never loaded from the repository by default. Use synthetic test fixtures when examples are needed.

## Important before merging or pulling the cleanup
Back up local copies of the database and workbook outside the repository first. When Git removes tracked files on checkout/pull, those local files may also be removed. The application will create a new local H2 store when started after cleanup.

## What the owner must still do if the old files contain sensitive information
1. Immediately consider returning repository visibility to private while exposure is assessed. Turning private later cannot revoke previously downloaded copies.
2. Inventory every historical file, SQL fixture and commit for personal data and secrets. Rotate exposed credentials.
3. Coordinate a separately reviewed Git history rewrite using git-filter-repo or an equivalent tool, with backups and a collaborator/clone impact plan. Force-pushes and branch protections require owner-controlled action; opening this PR is not a history rewrite.
4. Inspect GitHub forks, releases, cached references and other published copies; contact GitHub Support if historical content must be purged from cached references.
5. Keep real household evidence in protected local storage until application authentication, tenant isolation, retention and deployment security have been implemented and audited.

## Current application boundary

GBD-14 currently has an explicit production release hold. The prepared Windows
package cannot clear readiness or launch a previously enabled production config.
Owner-requested identity isolation, provider-side entitlement, historical exposure
containment, actual-device evidence and additional whole-PR independent reviews
are still prerequisites. See [the current security completion evidence and local
audit instructions](product/stories/persistent-aib-instance.md#amended-security-completion--evidence-required-to-remove-the-hold).
Current-user DPAPI is not isolation from coding agents or runners under that user;
an administrator/SYSTEM runner on the host can bypass ordinary user ACLs.
The household finance backend is a single-operator local system. Password sessions protect financial HTTP endpoints, while live database files depend on private OS permissions and full-disk encryption. No household tenancy or network deployment is supplied. Do not expose the backend or Vite frontend publicly.

## Private local workspace (GBD-13)

Whole-app loopback-only password sessions now protect every financial backend route, including legacy routes. The H2 console is disabled. Versioned backups are passphrase-encrypted and restore transactionally into empty workspaces. See [the operating boundary and recovery guide](product/stories/private-workspace.md). The live H2 database relies on OS file permissions/full-disk encryption; this is not tenant isolation or internet deployment. Older statements above describe the pre-GBD-13 baseline.

## Reviewed ledger (GBD-10)

The ledger retains reviewed financial facts/correction history behind the existing local session gate and includes them in encrypted backup/recovery. The sandbox extension uses protected server-side signing material and bounded AIS transport, with no payment calls or automatic financial-data forwarding. Sandbox registration/MFA is not live-bank readiness. [Trust boundaries, retention, recovery and remaining bank-readiness requirements](product/stories/reviewed-money-position.md).


## GBD-10 sandbox connection

Read-only Enable Banking SANDBOX / Mock ASPSP connection is included in the same GBD-10 PR: [setup, consent, review and protection details](product/stories/enable-banking-sandbox.md). Synthetic protocol/browser evidence is distinct from an owner-run external sandbox smoke test. Production/AIB is tracked by [GBD-14](https://www.notion.so/418d3419fef081808864ca465d4eff7a), after GBD-10 integration and production security readiness/consent. No production access is enabled.
