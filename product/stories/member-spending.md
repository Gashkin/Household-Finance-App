# Member spending and purchases (GBD-17)

Open **Spending & purchases** after signing in with Reader or Editor access. Admin alone grants no financial access. The workspace shows only explicitly shared ledger accounts, an explicitly published forecast, your personal purchases and requests addressed to you. It never impersonates another member for an administrator preview.

## Establish shared evidence

An editor shares the intended cash accounts in Household access, records and reconciles today's shared ledger evidence, and saves a forecast in Plan ahead using **Reconciled shared cash only**. Private accounts can coexist and are excluded from this mode. The forecast must contain exactly the currently shared cash sources; any private starting source prevents publication. Manual estimates cannot become a trusted shared allowance.

In Spending & purchases, expand **Publish a reviewed shared forecast**, review the disclosure and confirm publication. The publisher explicitly shares the forecast's labels, source descriptions, dates, bills, income assumptions and reserve. Remove private wording before publishing. The original publishing editor controls replacements. Publication never makes the editor's personal forecasts generally readable through other endpoints.

The report gives its date, shared-only scope, reconciliation dates, published horizon and warnings. Spending room is unknown without current published evidence, after source/forecast changes, after the publisher loses Editor access, or on a later day. Account withdrawal hides the dependent publication and request history immediately on the next authorized read. Existing session revocation clears already-open views through the workspace gate. Export downloads the same identity-scoped JSON report; it does not create a public link.

Conservative room = current shared cash − pending cash debits − published expenses − reserve − included unmatched purchases. Expected and uncertain income are excluded. Debt and credit limits never increase cash. A negative result remains a visible shortfall. Included purchases are your own active plans/requests plus approved shared requests, counted once by purchase identity. Other members' private plans and unknown household costs are explicitly excluded; this limited report is not an assured household-wide allowance or permission to spend.

Complete categorised period coverage is not established by today's reconciliation. Trends are therefore explicitly unavailable rather than inferred from partial activity or manufactured zeroes.

## Plan, request and record what happened

Enter a title, exact EUR amount, date and personal category. Confirm that the additional cost is absent from published bills, pending debits and other purchase plans. Saving is advisory, including when room is unknown or insufficient. A purchase beyond the forecast horizon is labelled accordingly. To correct an unbought plan, cancel it and create a replacement; the original history remains.

Personal plans stay private. Opting into a shared request sends its details and history to the publishing editor. Pending requests reserve the requester's view; they do not silently alter other members' plans. The receiving editor records an approval or decline and reason. Approval creates a shared additional-cost reservation, not a ledger entry, bill edit, budget reallocation or money movement. No Admin role is required for routine personal actions or for a receiving Editor decision.

Mark **bought** to record an actual purchase, including while a request is pending or declined. Purchase status and request decision are separate: a decline never prevents recording reality or changes an existing transaction. Bought items remain reserved until explicitly matched to a booked outgoing activity in a permitted shared cash account. Choose the actual account and entry and confirm. The actual amount and signed variance are computed by the server. One ledger entry can match only one purchase. Matching changes no balance; it releases the overlay because the actual debit already appears in the current ledger. A changed/void/inaccessible match is no longer trusted and its reservation returns. Review and republish changed ledger evidence before spending room becomes known again.

Every change keeps actor, timestamp, action, decision snapshot and evidence fingerprint/state. Revisions and current financial-evidence fingerprints reject stale requests. Stable purchase IDs make an identical create retry idempotent. Simultaneous decisions cannot overwrite each other. Decision history and notifications obey the same live access checks as the purchase; historical private source details do not bypass withdrawal.

## Personal settings and recovery

Choose your purchase category labels, compact/detailed dashboard and local update preferences. These labels do not recategorise ledger activity or alter budgets. Notifications appear on refresh inside the app; there is no email, push service or external data transmission. Marking updates read affects only your identity.

Flyway V3 adds `member_records` to the already-versioned V2 schema without changing prior migrations. Encrypted archive format v6 includes publications, purchases, preferences and histories in the existing whole-workspace capacity budget. Archives v1–v5 remain recoverable. Restore still requires an empty workspace and the recovery custodian, preserves the fresh owner's password, disables imported members and invalidates their old recovery codes. Older app versions cannot restore v6 archives. Live H2 still relies on OS/device encryption and permissions.

Budget-specific requests and allocations remain planned [GBD-18](https://app.notion.com/p/41ad3419fef0812a9895ec3efd2f45a3). Access from other home devices remains planned [GBD-29](https://app.notion.com/p/41ad3419fef0817d9052ffdc5ed36f9e). This story retains loopback-only operation and does not activate production banking or ingest real financial data.

## Evidence and operation

Use the existing README startup commands and named-user setup. `MemberJourneyTest` covers strict money/consent input, all relevant permission boundaries, unknown data, private-source exclusion, publication, request decisions, no purchase blocking, once-only reservations, matching/variance, stale financial evidence, concurrent changes, withdrawal, reports/exports and personal preferences. `MemberRecoveryTest` covers real file-database restart, v6 archive recovery, actual v5 compatibility and additive V2→V3 migration. The desktop/mobile browser journey publishes through the UI, requests and approves a purchase, records it bought, matches actual activity, revisits history/preferences and downloads the scoped report.

Context read for this execution: live Product Build authority (17 November single-independent-review correction), canonical GBD-17, independent-review trial record, appraisal record, repository AGENTS, delivery runbook and OKF index. Starting main: `f21daa24c07a9077cd628bc8316c1b122d2a5790`, human-integrated PR #27. This branch has not been merged; current-head CI and the single independent review are distinct evidence from local tests. The delivery coordinator's Copilot-specific gate is stale relative to the live single-review policy; no extra reviewer is authorized to satisfy it.
