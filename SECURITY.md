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
The household finance backend is a local-development system, with no real authentication or tenancy enforcement. Do not expose port 8080 on a public host or use it to store real financial records in an internet-facing instance.
