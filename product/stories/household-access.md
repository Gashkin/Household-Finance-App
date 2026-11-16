# Named household access (GBD-16)

One local workspace can serve named people without granting everyone access to private finances. This story delivers local enrolment, combined roles, private/shared ledger accounts, revocation and account recovery. It retains the existing loopback-only deployment and synthetic-data development boundary.

## Start and invite

Set `WORKSPACE_PASSWORD` to 16–256 characters and start the application as described in the README. Sign in as `owner`. The first sign-in creates a salted password verifier in the database. Once initialized, the environment bootstrap password can be removed; changing it does not overwrite a persisted password. Existing financial records belong to `owner` and remain private.

Open **Household access**, confirm your current password, and create an invitation with a unique username (3–40 letters, digits, dots, underscores or hyphens; stored lowercase). Pass the private code to its intended person. It expires after seven days and works once. Replacing or revoking it invalidates the previous code. No mail service or external identity provider receives credentials.

The person chooses **Accept invitation**, explicitly accepts, and sets their own password. A new account has no roles. Reload Household access, then grant the needed roles. You can disable and reactivate accepted accounts. Another administrator must change your own roles; the last active administrator cannot be removed. Conflicting changes require reload rather than overwriting another administrator's decision.

## Capabilities and record scope

| Capability | Admin | Editor | Reader |
| --- | --- | --- | --- |
| Invite, grant roles, disable/reactivate, inspect access events | Yes | No | No |
| Read permitted canonical financial data | No | Yes | Yes |
| Create own ledger accounts, cash evidence, forecasts and decision plans | No | Yes | No |
| Change/reconcile/import activity into own accounts | No | Yes | No |
| Change someone else's account | No | No | No |
| Share or withdraw own account | No | Yes | No |

Roles combine by union. Admin+Reader can manage people and read permitted data; Admin alone cannot read finances. Ownership and explicit account sharing limit financial roles. New accounts are private, including when created by the original owner. Shared account data includes its activity, reconciliation and account audit history. Sharing does not transfer ownership.

The Reader's **Permitted money position** counts only explicitly shared accounts, excluding everyone's private accounts, including their own. It always states its limited scope. It never implies a complete household picture or supplies an invented spending allowance. Editors' existing money-position and forecast tools include their own records plus shared accounts, with a limited-scope notice. Cash snapshots, saved forecasts and decision plans stay personal. Derived snapshots retain source-account IDs; withdrawal also hides forecasts, reports, historical plan evidence and publication audit entries that depend on a source no longer permitted. An editor can explicitly replace a hidden baseline after reviewing new evidence.

The original `owner` identity additionally has a fixed **recovery custodian** capability. Whole-workspace recovery archives contain every person's records and password verifiers; this capability is disclosed separately and is not granted to other Admins. It does not grant ordinary API access to another person's private accounts. Original-owner financial roles alone can use legacy planner/migration/import/monthly-report/intake and bank surfaces, whose data predates record ownership. They cannot expose these surfaces to another Admin or Editor. Existing banking release holds remain in force.

## Sessions and recovery

Passwords use PBKDF2-HMAC-SHA256 (600,000 iterations, independent random salt). Session cookies are HttpOnly/SameSite Strict, expire after 30 idle minutes or eight hours total, and do not survive server restart. Status polling does not keep an idle session alive. Sensitive access changes, sharing and backup/restore require password confirmation within five minutes. Sign-in and public acceptance/recovery attempts are bounded. Unknown identities receive generic sign-in/recovery failure responses.

Role changes, disabling and password recovery revoke that person's existing sessions. Sharing changes revoke other active sessions so already-open views are cleared at the next request or visible-tab check (every 15 seconds). Manual locking broadcasts across tabs. Previously viewed, copied or downloaded information cannot be erased from someone else's memory or device.

Each person can create a random, single-use recovery code after recent password confirmation. Store it offline and separately from the device. **Recover access** changes their password, consumes the code and revokes previous sessions while retaining roles. Generating a replacement invalidates the previous code. Disabled people cannot use recovery to bypass an administrator. There is no administrator password-reset or shared-password backdoor. If neither a password nor a valid recovery code remains available, app-level account recovery is unavailable.

Access events record actor/target identity, action, outcome and time; raw passwords and codes are excluded. The recent UI shows 20 events, the endpoint up to 200, with bounded database retention of 5,000 events.

Encrypted version-5 archives retain identities and record ownership. Restore requires an empty financial workspace on a fresh installation without other named users. The fresh installation keeps its owner password; imported members are disabled pending administrator review. Invitation and recovery codes are invalidated, and all sessions are revoked after restore. After checking each person's access, an administrator can reactivate accepted members. Version 1–4 archives are supported and migrate their records to the original owner. Backup-key recovery remains separate: account recovery cannot decrypt an archive whose passphrase was lost. Older app versions cannot restore version-5 archives.

## Verification and delivery boundary

`HouseholdAccessTest` exercises combined roles, no-role accounts, private account IDs, audit/totals inference, unauthorized writes, dependent evidence withdrawal, invitation expiry/replay/revocation, role races, recovery/session revocation, denied legacy/bank/export paths and direct-service access checks. `AccessRecoveryRestartTest` uses file databases to prove named identities survive restart without the bootstrap secret and recover with ownership preserved and members disabled. Existing recovery fixtures exercise archive versions 1–4. Browser tests exercise invitations, grants, read-only shared accounts, withdrawal across already-open tabs and local password recovery on desktop/mobile.

The richer member spending report, planned purchases and requests remain [GBD-17](https://app.notion.com/p/41ad3419fef081d29fd4e32b6b713a93), currently Backlog and dependent on GBD-16. This basic shared Reader view supplies immediate usable access without claiming that later journey is delivered.

This branch starts from main `9f607c4` and preserves the independent GBD-15 security work. Its ownership/schema/recovery changes must be reconciled with that story when either lands. No real accounts, bank credentials or actual financial records were used. Live H2 files remain unencrypted by the application; use device encryption and OS permissions. Approval of this PR does not authorize real-bank activation or internet exposure. Only a human may merge.
