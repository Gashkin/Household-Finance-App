# GBD-13 — private local records and recovery

One operator, one local workspace. Every backend route except the nonfinancial `/health` and workspace status/unlock endpoints requires an opaque server-side session. Missing or short instance passwords fail closed. Legacy planner/import/report/auth routes are included; the development role response is available only after unlocking and represents this single operator, not household roles. H2's browser console is disabled and blocked by the access filter. H2 automatic TCP sharing is removed.

## Set up and use (Java 21, Maven, Node 22)

Keep the database under your private OS user profile on an encrypted disk, outside shared/cloud-synced folders. Do not commit passwords, `.hfb` backups, downloaded reports or database files. On Unix use `umask 077` before starting; on Windows use a directory restricted to your account. The live H2 database is **not application-encrypted**. The OS account/full-disk encryption protects files, while this application protects HTTP access. A process or person with access to your OS account can read them. Do not claim internet, multi-user, malware or administrative-user isolation.

PowerShell, from `backend` (the password is prompted rather than stored in command history):

```powershell
$env:WORKSPACE_PASSWORD = [System.Net.NetworkCredential]::new('', (Read-Host 'Workspace password (16–256 characters)' -AsSecureString)).Password
mvn spring-boot:run
```

On Unix set `WORKSPACE_PASSWORD` using your preferred secret/environment manager before `mvn spring-boot:run`. Use a unique long password stored in your password manager. No hardcoded/default password is accepted. Reset the instance password by stopping the backend, setting a new environment value and restarting; all previous sessions are revoked. This changes access to existing records, not the passphrase of an existing backup.

From `frontend`, run `npm ci` and `npm run dev`. Open **http://127.0.0.1:5173**, unlock, and use Debt decision, Planner or the other views. Legacy views now use the same-origin proxy. Lock when finished: the server revokes the cookie and React unmounts financial screens and temporary forms. A tab returning from the background rechecks the session; an API 401 locks the screen. Explicit lock broadcasts to other open tabs in modern browsers. Server expiry is detected when a tab becomes active or requests data; an idle visible tab can retain rendered data until that check. Close the browser when leaving a shared device.

Sessions are in memory: 256-bit random cookie, HttpOnly, SameSite=Strict, Secure when using HTTPS; 30-minute idle and eight-hour absolute expiry. Passwords are PBKDF2-HMAC-SHA256 hashed with a random in-memory salt; login allows five failed attempts per minute globally, max 32 sessions. Backend requests require a loopback peer and loopback Host. Configured browser origins are exact loopback URLs; unsafe requests need a custom header. No wildcard CORS. Defaults allow localhost/127.0.0.1 port 5173. To use a different **local** frontend port set `WORKSPACE_ORIGINS` to its exact origin. Do not expose Vite or backend on a network or configure reverse proxies; this is a local capability, not a hosted release. Application/session responses use `Cache-Control: no-store`.

The existing intake key is still independently required to paste source excerpts. Model extraction remains off by default and requires its existing consent/configuration; workspace backup/recovery makes no external calls. Backend optional AI traffic is unrelated to recovery and remains an explicit opt-in.

## Make and test a backup

1. Open **Backup & recovery**. Enter and repeat a separate backup passphrase (16–256 characters).
2. Download `household-workspace.hfb`. Keep a second copy away from this device and store its passphrase separately in a password manager. There is no forgotten-passphrase recovery.
3. The file is authenticated AES-256-GCM with a fresh 16-byte salt, 12-byte nonce and PBKDF2-HMAC-SHA256/600,000 iterations. Version 1 is authenticated as associated data; wrong passwords and modifications fail verification before any insert. The plaintext payload never becomes a temporary file.
4. All saved financial tables are included: accounts, categories, commitments, monthly commitments, transactions, migration history/payloads, cash snapshots and their ordered lines, plus reviewed ledger accounts, entries, audit history and the saved dated forecast plan (backup payload version 3). Legacy payload versions 1 and 2 remain recoverable into a fresh workspace with no forecast; version 1 also has an empty ledger. Stable IDs, provenance, dates and exact integer cash amounts are preserved. Internal migration-artifact row IDs are regenerated because nothing references them; import IDs/history remain intact. Temporary intake drafts, instance passwords, sessions, provider keys, external source documents and unsaved debt comparisons are excluded. Existing downloaded debt JSON reports remain unencrypted exports.

The local format is limited to 10 MiB encrypted, 50,000 rows per table and this exact schema. Limits are enforced before JSON parsing/restore. Unsupported schema changes fail closed rather than silently dropping tables/columns. Counts are record counts, not financial totals. Backups are serialized with application mutations; do not run multiple app processes against one database or modify it externally during export/restore.

## Recover after an application/device failure

Keep the original database and backup untouched. Stop the app. Configure a **new** empty database location, for example in PowerShell:

```powershell
$env:SPRING_DATASOURCE_URL = 'jdbc:h2:file:./data/recovered-household;DB_CLOSE_ON_EXIT=FALSE'
mvn spring-boot:run
```

Use a private directory, set `WORKSPACE_PASSWORD` for this instance, then unlock. Open **Backup & recovery**, choose the `.hfb` file, enter its original backup passphrase, tick the explicit recovery confirmation and restore. The new instance password can differ. A nonempty target is rejected; this is not a merge or overwrite operation. All schema/manifest checks precede inserts, and inserts run in one serializable transaction. Wrong passphrase, tampering, missing tables/columns or a database constraint error leaves no partial records. Review recovered cash evidence and Planner data. Stop/restart and check again before retiring old files. Deleting old data or changing your production location is a human operational decision; the app has no delete-all recovery shortcut.

## Verification

`mvn verify`: original financial tests plus real-filter MockMvc requests covering legacy/API authorization, logout, cross-origin/Host/peer rejection, no-store, encrypted all-table round trip, damage/wrong-password/missing-table rejection and late-constraint rollback. Mutable-clock unit checks cover failed-login throttling, idle expiry and revoked sessions after restart. File-backed application-context restart tests close/reopen the original database and restore/reopen a distinct database.

`cd frontend && npm run build`, `node --experimental-strip-types --test tests/*.test.ts`, `npm run test:e2e`: full-stack real HTTP desktop/mobile regression journeys, lock/unlock, encrypted download, no browser-stored password, nonempty recovery protection, and desktop wrong-passphrase then successful recovery on an independent empty instance. Browser test data/passwords are synthetic. Local browser override: `PLAYWRIGHT_CHROMIUM_EXECUTABLE=/usr/bin/chromium`; CI uses the pinned Playwright Chromium. Original domain/controller tests isolate filters; the new security suite and browser journeys exercise the complete boundary.

JDK implementation references: [Cipher](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/Cipher.html), [GCM parameters](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/spec/GCMParameterSpec.html), [standard algorithm names](https://docs.oracle.com/en/java/javase/21/docs/specs/security/standard-names.html). This is not a cryptographic/security certification or a production deployment claim.
