# GBD-10 — reviewed household money position

## The usable journey

Run the password-protected local app as described in [private workspace setup](private-workspace.md). Open **Money position**. This is a separate, explicitly reviewed ledger: old Planner balances/imports and free-form intake facts are not silently migrated or added to it.

1. Create EUR cash/current/savings or debt/card/loan accounts. Provide a signed opening balance, date and source. The opening balance is **before activity on that date** (normally the previous day's closing statement). Debt owed and overdrafts are negative. A borrowing/credit limit is not a balance.
2. Enter reviewed activity manually or paste/upload normalized UTF-8 CSV. Preview does not change balances. Inspect dates, signed amounts, classifications and possible same-date/amount overlaps; correct facts and exclude duplicates/conflicts before explicitly approving selected rows.
3. Check the calculated booked closing balance against a dated statement. A mismatch is shown as a difference; it does not disappear automatically. Correct saved activity with a reason, or explicitly record a sourced reconciliation adjustment. Corrections preserve previous facts. VOID a cancelled/duplicate entry rather than deleting its provenance. Any changed activity invalidates that account's reconciliation.
4. Review dated cash, signed debt, net recorded position and income/spending since each account's opening date. These are not a monthly budget or bank-verified totals. Transfers and explicit reconciliation adjustments change balances but are excluded from income/spending. Tag both sides of an own-account transfer as TRANSFER; pairing is reviewed by the operator, not inferred automatically. Pending activity is shown separately and never included in booked balances; pending credits are not cash.
5. Once every included cash account is reconciled for the selected date/current revision, explicitly save a position for Debt decision. Enter reserve and unpaid commitments, checking them against pending debits. This creates a **new latest snapshot**, not a merge with an older snapshot: old unpaid commitments are not silently carried forward. The backend adds pending cash debits once as commitments, excludes pending credits and every debt account from spendable cash, and preserves signed overdrafts. Negative cash remains negative and cannot fund an extra repayment. Open Debt decision, explicitly apply the saved position, compare a repayment and download the normal evidence-linked report.

A saved snapshot is immutable evidence of the selected date/revision; later ledger edits do not rewrite old reports. Re-publish after new activity. Source reference in the snapshot links to retained publication provenance in **Published position provenance**, including account names/IDs, revisions, statement sources and pending protection.

## CSV contract and identity

Exact header (case-sensitive), six columns:

```csv
reference,date,description,amount,status,kind
sample-001,2026-11-14,Synthetic groceries,-12.34,BOOKED,ACTIVITY
sample-002,2026-11-14,Synthetic own-account transfer,-50.00,BOOKED,TRANSFER
sample-003,2026-11-14,Synthetic pending debit,-20.00,PENDING,ACTIVITY
```

Use ISO dates, signed EUR decimal text (maximum two decimal places), BOOKED/PENDING and ACTIVITY/TRANSFER. Quote fields containing commas, quotes or newlines using standard doubled-quote escaping. Bank-specific CSVs need conversion to this explicit contract; this feature does not guess AIB column meanings or claim native AIB-format support. Dates must be on/after the opening date and no later than today in Europe/Dublin. Transfers/standing-order labels are bookkeeping classifications only; the app cannot initiate a bank transfer or change an instruction.

References are stable and unique **within the chosen account**, not raw bank credentials/account numbers. Same reference + same facts is a duplicate. Same reference changing PENDING → BOOKED replaces one reviewed pending fact, retaining the old/new evidence. A different booked fact under an existing reference is a conflict and must be excluded or explicitly corrected. Different references with the same date/amount are possible duplicates, not automatically proven duplicates: the review acknowledgement requires the operator to resolve overlaps with manual activity. If a provider uses a new ID on settlement, explicitly VOID the old pending fact after checking the new booked fact; the app never guesses that two different references are one payment.

Preview edits retain the original reference. Source/preview changes clear review acknowledgement. Previews expire after ten minutes and are purged periodically; raw CSV text is not persisted. Normalized reviewed entries and audit before/after facts are persisted. Repeated approval of the same preview/facts returns its original result; a changed retry is rejected. A rollback cannot cache a successful approval. Stale revisions are rejected before mutation, and publication has a durable request ID. After an uncertain network response, reload before retrying.

Limits: 30 accounts, 500 rows/import, 100,000 CSV characters, 32 active ten-minute previews, 50,000 entries/account. Individual entered EUR amounts are bounded to ±€10 billion; totals remain within JavaScript-safe integer cents and backend checked arithmetic. Activity/history displays show latest 500/100 records; balances include all eligible saved entries through the selected date. Earlier-than-opening accounts are explicitly excluded. Historical positions older than 30 days are labelled and remain historical evidence, not current available-to-spend claims.

## Private storage, retention and recovery

All ledger endpoints inherit the whole-app authenticated loopback/Host/origin/request-header boundary; there is no unauthenticated banking bypass or client-side financial authority. New input handling rejects fractional/string revisions, coerced numeric money fields, string booleans, null/incomplete requests and unknown properties. Error messages do not echo source rows or private account data. There is no provider client, external HTTP fetch, automatic AI forwarding, payment endpoint, background bank refresh, credential-entry form, or bank token/key storage in this feature.

| Boundary | Data and control |
| --- | --- |
| Operator → local backend | Explicit account/normalized activity/statement source; authenticated requests and review acknowledgements |
| Backend → local database | Integer financial facts, sources, revisions, retained correction/reconciliation/publication evidence; OS-protected H2 files |
| Backend → UI / decision | Derived signed totals, status/uncertainty and explicit immutable snapshot link; no borrowed headroom |
| Backend → backup file | Passphrase-encrypted authenticated archive; no plaintext temporary export file |
| Provider / model / cloud | No calls or user signing material required for GBD-10; optional pre-existing intake remains separately opted in |

Backup payload version 2 adds `LEDGER_ACCOUNTS`, `LEDGER_ENTRIES` and `LEDGER_AUDIT` to the prior eight financial tables. The authenticated encryption envelope remains version 1. Restore accepts a complete version-2 payload or a complete legacy version-1 payload (the latter restores prior tables and leaves the new ledger empty), into an entirely empty workspace only. Unsupported/incomplete schemas fail closed. Close/reopen, encrypted round trip, audit/snapshot identity and old-format recovery are tested with synthetic data.

Facts/audits are retained for the lifetime of this local workspace; VOID is an integrity correction, not privacy erasure. To dispose of the full workspace, stop the backend and remove its private database using normal OS controls, together with exports/backups you no longer need. Copies/old backups can reintroduce deleted data; retain/revoke them deliberately. This is not provider consent revocation. Live H2 files are **not application-encrypted**: check private OS permissions and device/full-disk encryption before real-data use. No tenant, public-hosting or security-certification claim.

## Banking research and release boundary

The owner's Enable Banking registration and MFA are complete in **SANDBOX**. The key stays with the owner; no private signing material or real accounts were requested, ingested, logged, committed or used in tests. Sandbox registration does not establish API authentication, consent or AIB/card production connectivity.

Official [personal-use terms](https://enablebanking.com/terms/) and [linked-account activation](https://enablebanking.com/docs/api/linked-accounts/) support investigating restricted own-account production use. [Sandbox documentation](https://enablebanking.com/docs/api/sandbox/) describes synthetic Mock ASPSPs. [Registration](https://enablebanking.com/docs/api/control-panel/) requires local key management and provider-specific application details. This research is part of this story; no extra research PR.

**Selected boundary:** finish the coherent reviewed ledger and offline import/reconciliation capability now. A live provider connection is not implemented or validated by this PR. It needs a separately assessed complete connection capability: AIS-only endpoints/scopes, server-side protected secrets, state-bound consent callback, account selection, expiry/status, stable IDs/pagination, refresh/retry, disconnect/revoke/local deletion and negative security tests. Do not collect bank credentials or use payment endpoints. Actual AIB current/card/joint coverage, usage limits/costs and local callback acceptance remain unverified. Owner-controlled production registration/consent and a passed readiness review are prerequisites to a narrowly scoped real-account smoke test; they do not block this synthetic ledger delivery.

## Verification

Backend domain/real-filter HTTP cases cover review → cash/debt/net position → reconciliation → snapshot → debt report; stable-ID duplicates/settlement, overlap acknowledgement, atomic conflict rollback, correction/VOID history, explicit adjustment, stale revisions/types, negative cash, encrypted new/legacy recovery and approval rollback/retry. File-backed application-context tests close/reopen both source and recovered databases including ledger entries/audits. Frontend transport tests preserve exact signed amounts/server authority and explain uncertain outcomes. Real desktop/mobile browser journeys edit proposed facts, replay approval, identify duplicates, correct a saved entry, acknowledge a statement difference and download the resulting evidence-linked debt report. CI uses only synthetic records and GitHub-hosted runners; no external banking or model calls.
