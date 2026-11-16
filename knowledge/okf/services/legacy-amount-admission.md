---
type: Service
title: Legacy Amount Admission
description: Strict cent-precision admission for legacy planner and workbook values.
resource: backend/src/main/java/com/householdfinance/service/LegacyMoney.java
tags: [okf, money, validation, legacy]
timestamp: 2026-11-16T00:00:00Z
---

# Legacy Amount Admission

Planner requests retain decimal input as `BigDecimal`, including missing/null
values. `LegacyMoney` requires explicit whole cents, bounded minor units and an
exact decimal round-trip through the legacy double storage representation.
It never rounds, accepts non-finite values or replaces missing values with zero.
Balances may be signed; budgets and future commitments must be non-negative.
The UI sends the entered decimal text rather than converting it to a JS number.

Workbook mapping reads the underlying numeric cell value, not its rounded display
format, and rejects formula cells rather than trusting stale cached values.
Malformed grouping, missing values and fractional cents reject mapping/validation.
Encrypted recovery applies the same legacy amount checks before any inserts.
Existing legacy columns remain doubles; this is strict admission, not a conversion
of historical arithmetic to exact decimal storage. The reviewed ledger remains
the authoritative manual/CSV activity route.

See [Workbook ingestion](../workflows/workbook-ingestion.md),
[Workspace recovery](workspace-recoverability.md) and
[schema/release compatibility](../runbooks/schema-compatibility.md).
