---
type: Runbook
title: Schema and Release Compatibility
description: Versioned migrations, startup rejection and recovery across supported schema versions.
resource: backend/src/main/resources/db/migration/V1__workspace.sql
tags: [okf, database, migration, recovery]
timestamp: 2026-11-16T00:00:00Z
---

# Schema and Release Compatibility

Flyway is the sole schema writer. Version 1 contains the reviewed thirteen-table
schema without financial seed data. Hibernate validates mappings and never uses
`ddl-auto=update` in the release. Startup validates migration checksums, refuses
automatic baselining of a nonempty unversioned database and rejects unknown future
migrations (the Flyway default future-ignore pattern is explicitly disabled).
Cleaning is disabled. The packaged launcher overrides inherited migration/DDL
development settings. Do not repair/baseline a failing database to force startup.

An unversioned legacy database must remain untouched: export and verify an
encrypted recovery archive under its original reviewed release, retain that
original instance offline, and restore into an empty versioned database under the
new release. No automatic in-place upgrade of historical H2 files is supported.
Before a real migration, prove this on disposable synthetic copies of the exact
old/new releases and compare full record identities and values. Never use live
records or exported content in engineering fixtures.

Migration history stays local to the target schema and is excluded from financial
recovery archives. Recovery checks the complete allowed column identity set and
rejects duplicate/unknown/missing columns; it accepts column reordering caused by
ORM versions and inserts values by their validated archive column identity.
Constraint or validation failure rolls back all imported records.

`SchemaMigrationTest` exercises real file-backed startup failure for unversioned,
future and checksum-mismatched schemas while keeping synthetic balances unchanged.
Restart/recovery tests cover fresh initialization, restart and encrypted recovery.
These checks protect this migration-aware release. A historical binary with no
compatibility guard cannot be retroactively made safe; retire its launcher and
deny it access to the new production database through the required host identity
boundary. Do not claim arbitrary old binaries are prevented from opening data.

Flyway's H2 support currently logs that H2 2.4.240 is newer than its verified
2.3.232 baseline. Passing local tests are recorded evidence, not upstream support
certification; retain this compatibility limitation in independent release review.

See [Workspace recoverability](../services/workspace-recoverability.md),
[legacy amounts](../services/legacy-amount-admission.md) and
[the operating guide](../../../product/stories/persistent-aib-instance.md).
