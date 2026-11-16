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

Workbook mapping reads the original OOXML numeric text (`XSSFCell.getRawValue`),
validates that exact decimal before any double conversion, and only then normalizes
admitted values. This rejects both fractional lexemes hidden by double rounding and
whole-cent values that the legacy store cannot represent. Exact exponent cents and
zero remain valid; nonzero underflow/overflow is rejected before decimal expansion.
Errors identify the sheet number and cell/row without echoing the amount. Formula
cells remain rejected rather than trusting stale cached values or display formats.
Malformed grouping, missing values and fractional cents reject mapping/validation.
Encrypted recovery applies the same legacy amount checks before any inserts.
Existing legacy columns remain doubles; this is strict admission, not a conversion
of historical arithmetic to exact decimal storage. The reviewed ledger remains
the authoritative manual/CSV activity route.

See [Workbook ingestion](../workflows/workbook-ingestion.md),
[Workspace recovery](workspace-recoverability.md) and
[schema/release compatibility](../runbooks/schema-compatibility.md).
