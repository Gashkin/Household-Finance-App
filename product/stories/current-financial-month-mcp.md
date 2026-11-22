# GBD-33 — current financial month MCP contract v1.0

Canonical scope: [live Notion story](https://www.notion.so/41fd3419fef0810f8d86fb75173f17fc). Setup, threat model, credential contract and owner acceptance: [connection guide](../../ops/mcp-connection.md).

`get_current_financial_month` is the only tool. Input is an object with optional UUID `approvalId`, no additional fields. Omit it to request a local one-use approval. That call returns `isError=true`, `OWNER_APPROVAL_REQUIRED`, `approvalId` and `expiresAt`; it never reads financial data. After the owner approves that exact request locally, invoke once with its ID. The same access token and unchanged local read permission revision are required. Every subsequent read starts again.

Transport: stateless Streamable HTTP JSON-RPC 2.0 at the dedicated `/mcp` listener; JSON response, no session cookies, no background subscriptions, no financial cache. Protocol version 2025-03-26; initialize, notifications/initialized, ping, tools/list and tools/call only. OAuth protected-resource metadata is available separately. Unsupported methods and arbitrary tools are denied. Discovery has no user data. Each financial tools/call is authenticated online, including approval requests and retries.

Successful tool output uses `structuredContent`:

| Field | Meaning |
| --- | --- |
| `schemaVersion` | `1.0` |
| `state` | `PARTIAL` or `PERIOD_UNAVAILABLE`; never claims comprehensive coverage |
| `asOf` | Snapshot capture timestamp in UTC; applies to all included records |
| `period` | Saved budget `periodStart`, exclusive `periodEnd`, basis `SAVED_BUDGET_PERIOD`, time zone `Europe/Dublin`; null if not established |
| `accounts` | Opaque account ID, generated safe alias, CASH/DEBT, EUR, ledger balance in cents (null if unresolved), balance date, reconciliation evidence date, ledger provenance, REVIEWED/ESTIMATED/STALE/UNAVAILABLE state, pending debit reserve and unresolved count |
| `accounts.openingBalanceMinor` | Only present when the backend has a reconciliation at the day before period start; otherwise null. It is never reconstructed from a guessed opening balance |
| `events` | Stable ordering by effective ISO date then ID. Only current-period commitments/posted movements and explicitly unverified plan alternatives |
| `undated` | Undated published reserves and budget allocation/income exceptions. Unknown timing is visible; `cashFlow=false` prevents fabricated timeline entries/double counting |
| `coverage` | Always explicit: unreconciled/unreviewed activity, stale accounts, unavailable balances, excluded sources and warning codes; `comprehensive=false` |
| `forecasts` | Backend-calculated account-specific daily points, baseline and anchor (`BOOKED_LESS_RESERVED_PENDING_DEBITS`), verified flag from backend evidence plus activity review. No new forecast arithmetic in the MCP transport |

Each event carries `id`, `accountId` and `destinationAccountId` where known, allocation certainty, direction, movement semantics, EUR amount in cents, `date`/`dateBasis`, independent `dueDate`/`paymentDate`, status, date/amount certainty, fixed provenance code, last review timestamp if recorded (otherwise null), `incorporatedInAnchor`, `outstanding` related-record ID and `cashImpact`. Cash impact distinguishes source cash, destination cash, transfers between cash accounts, debt-only activity and unverified alternatives; a card charge is not a second cash payment. No names, payees, narratives or arbitrary provenance text are exposed.

Due/expected dates are estimated timing; posted settlement dates are confirmed activity dates. Last review time is unavailable for ledger/recurring records because their source DTOs do not record a per-event review timestamp; `asOf` is capture time, not a fabricated review time. Imported/manual records are ledger-derived, never live bank verified. Account `balanceAsOf` is the backend ledger date, not a claim that a bank refreshed at that instant.

Card metadata distinguishes card charges/refunds and repayments from other debt movements where available. A paid commitment uses the actual payment amount/date, retains the due date, and replaces its linked posted event(s). Incorporated paid events must not be subtracted from the current anchor again. A pending reservation is not a second outstanding debit. Changed matches are NEEDS_REVIEW and are not treated as ordinary outstanding cash flows. Reciprocal transfers and debt settlements are one movement, with a destination reference. Debt interest/principal splits are not supplied by the existing cash-flow read service and are explicitly excluded; credit limits are never cash. Unknown transfer allocation remains explicit. Current debt expectations reuse backend statement/settlement rules.

Saved manual-plan events lack account/matching information, so `UNRECONCILED_PROJECTION` and `outstanding=false` identify alternatives that can overlap actuals/schedules. They must not be summed into a verified cash-flow total. Published undated reserves are exceptions for the same reason. Backend forecasts use the canonical commitment/debt path, not these alternative plans. Forecasts may incorporate earlier overdue obligations; their source references explain this while the current-period event list stays bounded. Coverage remains partial; ChatGPT may visualise, but must preserve uncertainty and cannot assert that an independently calculated total is app-verified.

Financial errors use `isError=true` with `structuredContent.error`: `APPROVAL_REQUIRED`, `DISABLED`, `APPROVAL_LIMIT`, `ACCESS_REVOKED`, `ACCOUNT_LIMIT`, `EVENT_LIMIT`, `SOURCE_LIMIT`, `PERIOD_LIMIT`, or `SNAPSHOT_UNAVAILABLE`. Invalid/expired/revoked tokens and issuer outages use HTTP 401 with a Bearer metadata challenge and `AUTH_REQUIRED`, `TOKEN_DENIED` or `AUTH_UNAVAILABLE`. Transport validation returns bounded HTTP errors. No failed operation returns a partial payload, credential or internal exception text. A successfully read empty current period is distinct from missing period, missing balances and transport failure.

All amounts and dates originate in backend records or are marked estimated. Mutations, arbitrary account/date selection, historical export, AIB feeds, local installation and LifeOS consumer implementation are outside this tool. Automated tests use synthetic data and exercise the protocol separately from actual OAuth/tunnel/client acceptance.
