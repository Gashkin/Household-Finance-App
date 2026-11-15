# Dated affordability (GBD-11)

Plan ahead answers a narrower question than current balances: **could a dated bill or proposed purchase leave the household short before expected income arrives?** Open Plan ahead, choose reconciled cash-ledger evidence or an explicitly sourced manual estimate, set a protected reserve/horizon, and enter dated future occurrences. Save the reviewed inputs, inspect daily cash lows, then compare a temporary purchase, delayed receipt or explicitly speculative upside. Correct inputs and save again; old comparisons disappear. Reload preserves the saved plan.

## Financial contract

- EUR values arrive as exact text with at most two decimals, and the backend converts them to checked integer cents. No frontend financial calculation is authoritative. Amounts, dates, event counts, stable UUIDs, revisions and explicit review choices are bounded and validated on the server.
- The baseline is a **closing balance**. Future occurrences must be after its date, within a horizon of 1–90 days. Up to 200 explicit occurrences; no imposed payday or recurring-period rule. The existing sample Planner's float amounts and inferred commitments are not imported.
- Ledger mode captures only reconciled CASH accounts, their current revisions, booked cash and pending debit protection. Debt/credit-card balances, limits and pending credits cannot inflate cash. Pending debits are subtracted once at the start; the operator must exclude the same expense from dated occurrences. Same-label separate bills can be legitimate; duplicate UUIDs are rejected rather than guessed away.
- Income is EXPECTED or UNCERTAIN, never automatically received. Uncertain income is excluded from the base projection. An upside what-if may include it, with an explicit warning and no current-decision readiness. Overdue unreceived income is excluded until retimed or incorporated into new reviewed actual evidence; overdue expenses remain reserved.
- Each day's low subtracts **all expenses before applying that day's income**. The first cash shortfall and protected-reserve breach are shown even when the day's closing balance recovers. No promise that a salary arrives before a bill that morning.
- **Cash-only headroom** reserves all dated expenses and the protected reserve, excluding all future income. **Planning headroom** takes the minimum dated projected cash (including the starting balance), subtracts reserve and floors at zero. It cannot exceed starting cash after pending debits and reserve. Headroom is a one-time planning limit across the full horizon, not a daily allowance or bank spend instruction.
- A manual estimate stays illustrative. A dated baseline is labelled DATED; over seven days old or an expired horizon is STALE. Changed/added/removed cash accounts, changed cash revisions/balances/pending protection or invalidated reconciliation produce SOURCE_CHANGED. Only today's unchanged reconciled ledger baseline is current-decision ready. A reviewed starting point still does not guarantee future income.
- Saving ordinary corrections preserves frozen starting evidence. Replacing it is explicit and requires moving/removing expectations already covered by the new closing balance. This prevents a received salary or settled bill from silently appearing both in actual cash and again in future expectations.
- What-ifs do not write ledger facts, save plan edits or move money. Optimistic plan revisions reject stale edits/comparisons; database versioning prevents lost updates. Network failures instruct the operator to reload because a save may have succeeded.

## Privacy, operation and recovery

The same authenticated loopback workspace, session/CSRF/origin controls and no-default-egress boundary applies. No bank or model request is added. Development, tests, CI artifacts and reviews use synthetic records only. Do not put personal statements, secrets or provider keys in the plan's source labels or repository.

The one saved household plan lives in FORECAST_PLANS with a database version, update timestamp and bounded structured payload. It survives file-backed restarts and is included in encrypted workspace backup payload **v3**. Complete v1/v2 backups restore into an empty workspace with no forecast plan; the encryption envelope is unchanged. Existing records are not overwritten by recovery. Live H2 files still rely on private OS storage/device encryption; there is no public-hosting or production-bank readiness claim. Workspace deletion/backup disposal follows [private-workspace.md](private-workspace.md).

Future expectations are editable planning inputs, not an append-only bank ledger. Actual ledger corrections and their audit history remain separate. No automatic refresh or marking income as received; review actuals explicitly.

## Bootstrap and delivery evidence

The root AGENTS.md is now a retrieval map to the live Notion Product Build authority, canonical backlog/story, relevant security section, subordinate review trial, appraisals and technical runbooks. It contains no copied GBD/review policy or story requirements. delivery/README.md describes CLI mechanics, explicitly distinguishing its executable Copilot count/thread check from the independently orchestrated Work review.

The canonical GBD-11 story and PR carry exact test/commit/review evidence. Synthetic HTTP/domain tests exercise timing, uncertainty, staleness, pending protection, strict input, concurrent corrections and backup compatibility. Desktop/mobile browser tests exercise creation, a delayed-payday purchase, correction invalidation, persistence and stale evidence. A fresh independent reviewer must load its own live context and exact-head evidence; an implementer self-check is not an independent PASS.
