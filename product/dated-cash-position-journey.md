# Dated household cash position → debt decision

## User outcome
Enter a cash position once and reuse cash available after commitments and the protected reserve when comparing an extra loan repayment with keeping household cash.

## Scope and deliberate limitations
- Local, single-household proving ground. **This application currently lacks real authentication, tenant isolation and deployment hardening. Do not expose its backend publicly or put sensitive live household data into a publicly deployed instance.**
- Cash is deliberately separate from legacy HBM accounts/commitments: those tables use floating point and have no reliable as-of date or evidence source. They must not automatically be considered verified evidence.
- Users manually record cash balances and commitments due **in the 30 days after the as-of date**, along with a protected reserve, source note and self-declared REVIEWED or ESTIMATED status. Never count bills already deducted from cash balances.
- Each save creates a new dated snapshot with a UUID and timestamp; previous snapshots remain available for linked decisions. No silent overwrite.
- Amounts are integer euro cents; backend rejects fractional, negative, out-of-range or malformed inputs, limits lines and performs checked integer arithmetic.
- Cash after commitments may be negative. It is shown and warned about; negative liquidity cannot be applied to debt scenarios. Above-reserve discretionary cash is clamped to zero for display.
- REVIEWED is self-reported, not bank verification. An estimate or a snapshot over 30 days old is visibly flagged; applying either requires an explicit click.
- Applying a snapshot copies **cash after upcoming commitments** and its protected reserve into the debt scenario, and sends its ID to the backend. The backend refuses unknown IDs and mismatches between source and submitted values.
- Editing cash or reserve removes provenance and invalidates the comparison. Editing other scenario fields also invalidates the result, but retains the cash evidence reference.
- Debt scenarios remain transient. No payments, third-party bank links, uploads, automated source reconciliation, recurring commitment deduplication or forecasts in this story.

## API
- GET /api/cash-position/latest: HTTP 204 when empty; otherwise latest stored snapshot, lines, source, totals and warnings.
- POST /api/cash-position/snapshots: HTTP 201 with a saved snapshot.
- POST /api/debt-scenarios/compare: optional cashPositionSnapshotId must resolve to a saved snapshot and match submitted liquidCashMinor and minimumReserveMinor.

Example cash snapshot:

    {
      "asOfDate": "2026-11-14",
      "sourceNote": "Balances checked against banking app",
      "verification": "REVIEWED",
      "cashBalances": [{"label":"Current account","amountMinor":500000},{"label":"Savings","amountMinor":200000}],
      "upcomingCommitments": [{"label":"Rent due","amountMinor":120000}],
      "minimumReserveMinor": 150000
    }

Gross cash €7,000; commitments €1,200; cash after commitments €5,800; protected reserve €1,500; beyond-reserve cash €4,300.

## Run locally
- Start backend: cd backend && mvn spring-boot:run
- In another terminal: cd frontend && npm ci && npm run dev
- Open http://localhost:5173. Save a cash position, explicitly apply it, then compare debt scenarios.
- Backend verification: cd backend && mvn verify
- Frontend verification: cd frontend && npm run build, then node --experimental-strip-types --test tests/*.test.ts

## Follow-up
Next work should reconcile actual transactions and scheduled commitments into a dependable baseline, removing manual duplication. Real deployment requires authentication, household scoping, retention policies, secure migrations and removing tracked financial data from public Git history if sensitive.
