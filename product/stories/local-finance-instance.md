# Local Finance App — installation and operation (GBD-30)

This release is a local-only finance workspace for manually reviewed statement
updates. It does not connect to Enable Banking or AIB. The bank integration is
disabled and remains outside this release.

## Where the application keeps data

Install and start the app while signed in as the separate, standard Windows
account **Dev Server One**. The app stores its private files under:

```text
%LOCALAPPDATA%\HouseholdFinance
```

The account configuration is protected with Windows DPAPI and a private DACL.
The H2 database is not application-encrypted; its at-rest protection depends on
the Windows volume being fully encrypted with protection on. The statement
backup/export is separately encrypted with the passphrase selected in the app.
Do not put the instance or exported backups in Git, OneDrive, Downloads, a shared
folder or another user's profile.

Development, Codex, build tools and CI stay in the main Windows account. Do not
install development tools, an agent or a CI runner in Dev Server One. After
starting the app in Dev Server One, use Fast User Switching to return to the
main account without signing out of the runtime session; the app continues to
run under Dev Server One while development continues in the main account.

Run Codex and CI with their normal non-elevated Windows tokens. The owner uses
an administrator account for development, so the excluded-identity probe
accepts that account only when the tested process is not elevated and is
distinct from Dev Server One. It verifies ordinary file access is denied by the
runtime DACL. An elevated administrator or SYSTEM can take ownership or
otherwise override these permissions; this local account arrangement does not
protect against a privileged administrator or a compromised host. Do not use
the instance for real records until `Test-IdentityBoundary.ps1` passes under
the actual runtime identity, the actual non-elevated development/Codex token and
the actual CI runner token, and the device-encryption state is verified. If a
runner executes with an elevated token, it cannot pass this boundary.

## Install, launch, stop and update

1. Sign in to **Dev Server One** and confirm that Windows reports a standard
   account. Keep the session private and do not enable profile or browser sync.
2. Obtain the ZIP from the GBD-30 reviewed build and compare its SHA-256 with
   the exact value recorded in the review evidence. Do this before extracting or
   running any included script.
3. Extract the verified ZIP to a temporary local folder. `Install.ps1` checks
   each packaged file against `manifest.json` before installing it.
4. Run `Install.ps1` from that folder. Choose a strong workspace password and
   keep it in your password manager. The installer creates private configuration
   plus protected backup and secrets directories, and a desktop shortcut. It
   does not copy or replace the database on update.
5. Open **Household Finance**. Keep the launcher window open while using the app;
   closing it or pressing Ctrl+C stops the local server. The app is available at
   `http://127.0.0.1:8080` on this computer only. You can then switch back to
   the main account without signing out of Dev Server One and continue
   development there. The runtime stops when Dev Server One is signed out or
   Windows shuts down; after a restart, sign in to Dev Server One and launch it
   again.
6. For an update, first create and verify an encrypted recovery backup. Run the
   new release's `Install.ps1` while the application is stopped and confirm the
   backup prompt. The previous version is retained for rollback; never run an
   old binary against a database after a schema upgrade.

Initial commissioning must use synthetic records. `Test-Release.ps1` exercises
the packaged launcher, CSV review and duplicate detection, reconciliation,
restart persistence and encrypted backup/restore using synthetic data. Hosted
or local test results do not prove the owner's Windows identities, ACLs, device
encryption or actual denied access; record those separately before real data.

## Import an AIB statement or interchange CSV

The Money position importer accepts AIB's standard downloaded current-account
and credit-card CSV exports directly, as well as the app's interchange CSV
with these headers:

```csv
reference,date,description,amount,status,kind
```

- `reference`: stable bank statement row identifier, unique within the account.
- `date`: booked date in `YYYY-MM-DD` format.
- `description`: short transaction description.
- `amount`: signed EUR amount, with up to two decimal places. Outgoing payments
  are negative and incoming funds are positive. Do not include `€`, separators
  or formulas.
- `status`: `BOOKED` or `PENDING`.
- `kind`: `ACTIVITY` or `TRANSFER`.

For AIB files, the importer recognises the booked date, description and either
separate debit/credit columns or an amount plus debit/credit transaction type.
When the export includes a running balance, the preview shows the latest dated
balance only when that date has one unambiguous balance row. If multiple rows
share the latest date, or that date has no balance, enter the statement closing
balance manually before reconciling. Duplicate semantic headers and unsupported
or ambiguous headers are rejected before any ledger changes. The account is
selected explicitly in the app; no account number is needed in the source label.

To import:

1. Sign in to Dev Server One, launch the app and unlock it with the workspace
   password.
2. Open **Money position**. Select the account that matches the statement; this
   account selection is the import mapping. Create the account first if needed,
   using a dated opening balance and its statement source.
3. Select **Preview and review statement CSV** and choose the downloaded AIB
   CSV or paste the statement rows. Enter a source label that identifies the
   institution/file period without exposing an account number, then select
   **Preview CSV**. No column conversion is needed for supported AIB exports.
4. Review every proposed row, account, date, amount, status and duplicate or
   overlap warning. Correct any row only with its source visible. Do not approve
   an incorrect account mapping; return to the correct account and preview
   again. A duplicate or conflicting reference must be investigated, not
   bypassed.
5. Check the acknowledgement that you reviewed the statement and resolved
   possible overlaps, then select **Approve selected activity**. Approval is
   explicit and only adds the selected reviewed rows.
6. Enter the statement's dated closing balance and a source label, or use the
   balance shown from the AIB export. Check the displayed difference. Confirm
   reconciliation only when the number is understood; if there is a genuine
   adjustment, select the explicit adjustment option so the app records its
   date, reason and source.
7. Confirm the account position and its reconciliation date/source. The dated
   commitments and cash forecast remain separate from imported transactions;
   do not manually add a commitment for an already posted payment.
8. Use **Backup & recovery** in the app navigation to create an encrypted
   backup. Save it into `%LOCALAPPDATA%\HouseholdFinance\backups` and keep its
   passphrase separately. The directory ACL denies other standard users access.
   Test restore only in an empty synthetic workspace, never over live records.

Preview errors do not write any rows. Repeating an accepted file is safe: stable
account-scoped references are reported as duplicates. Before activity is
imported, **Correct opening balance and date** can update a stale opening figure
with an audit entry. After activity exists, use statement reconciliation and
its explicit sourced adjustment; do not overwrite imported transactions.
**Remove this unused account** removes a mistaken duplicate only when it has no
activity, reconciliation, sharing/link or published-position history.
Unsupported or malformed files must be corrected at source; do not edit the
database or use the retired workbook migration path to force an import.

## Recovery and limits

Backups contain private household records even though they are encrypted. Keep a
separate recovery copy and passphrase; loss of both the instance and backup
means the data cannot be recovered. A failed update leaves the prior active
version and data in place. A corrupted database must be restored from a verified
backup into an empty, version-compatible workspace.

Loopback binding prevents direct network access from other devices, but it does
not isolate the app from another process already running as Dev Server One, an
elevated main-account administrator, SYSTEM, malware or a compromised host. There is no
automatic bank sync, no provider credential, and no automatic transmission of
financial records to ChatGPT. GBD-33's bounded read-only MCP support remains a
separate, disabled-by-default local feature and requires its own owner setup and
approval.
