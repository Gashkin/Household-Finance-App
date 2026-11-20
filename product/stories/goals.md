# Goals and irregular costs — GBD-22

Financial editors use **Goals & irregular costs** to plan an annual bill, holiday, purchase or emergency buffer. Each goal has a target, date, priority (1 highest), reserved existing cash and a planned monthly contribution. Amounts are exact EUR cents. Reservations do not move money, create assets, record spending or change a budget. Contributions are plans; no automated transfer or investment instruction runs.

The overview counts owned cash once, subtracts pending debits once and shows reservations within that cash. An optional savings account limits competing reservations to that account's existing cash; its balance is context, never an additional asset. Increases require cash backing. If later activity reduces cash, warnings identify the shortfall; explicit releases remain possible. Cancellation releases the goal's reservation and contribution and retains its history.

Funding gaps compare target and reserved cash. Monthly recommendations divide the remaining gap, rounding cents upward, over calendar months including the current and target month (minimum one). They are illustrative contributions, not a guarantee of affordability. Compare total contributions with the current budget's unallocated **plan**, review account reconciliation and use Plan ahead for dated account risks. Reallocate budgets explicitly; these totals never rewrite allocations. Goals are sorted by priority then date so competing choices are visible; no automatic priority-based withdrawal occurs.

An expected annual or irregular bill creates one dated expense in the existing commitments system. Plan the next actual due bill; annual automatic renewal is not implied. Goal name, target, date, account, category and estimate changes update that same schedule atomically with the goal. It appears once in the six-month outlook when in its horizon, with no fabricated actual transaction. Match an approved payment in **Plan ahead → Upcoming/match**; its actual cost and evidence warnings appear with the goal, and the existing outlook suppresses the already-booked movement. Cost differences never silently change budgets or goal reservations. Savings targets do not create forecast expenses. Reservations are displayed separately from the cash forecast; subtracting a bill's reserve again from an outlook already containing the bill would double count it.

Matched, overridden or past bill facts cannot be rewritten from the goal editor. External bill edits produce a warning or reject conflicting cost changes. Use the existing bill matching/override workflow to review the evidence; create the next bill as a new goal after the previous bill is historical. Cancelling a future bill goal also cancels its expectation. Cancelling an old savings target remains possible without erasing a payment.

Saved revisions, operation IDs and reasons protect changes. A stale save retains its draft: refresh saved cards, compare and explicitly reopen the current saved goal. Unknown responses require **Check save status** before a new write. Confirmed operation retries do not duplicate goals, schedules or audit. Access loss clears private drafts and history. Owner scope follows existing budgets/commitments: another reader/editor or an administrator gains no access to private goals or linked private accounts. Shared account visibility does not grant mutation or ownership of another person's goals.

Goals, operation receipts and audits use the existing version-7 encrypted workspace backup tables. Restart and restore retain them together with commitments and ledger evidence. No schema migration, bank connection, cloud financial-data transmission or real-data release is introduced.

An approved matched bill has no remaining reservation, funding gap or active contribution. Its original target and reservation decision stay recorded rather than becoming an extra deduction after payment. Explicit unlinking restores the unpaid reservation and exposes any resulting shortfall. Closing a paid goal retains its matched commitment and actual payment; it does not cancel or erase the booked fact.

## OKF traceability

- knowledge/okf/index.md
- knowledge/okf/domain/index.md
- knowledge/okf/workflows/index.md
- knowledge/okf/workflows/budget-allocations.md
- knowledge/okf/workflows/recurring-bills.md
- knowledge/okf/rules/domain-invariants-and-calculations.md
- Live Notion Product Build authority and canonical GBD-22; integration base `2e730545e48f8783ec3f3c8172ad53da1d3951c5`.

## Local verification — 20 November 2026

- Complete backend `verify` at opening head `c4bff32`: 265 discovered, zero failures/errors, 16 existing platform/conditional skips. Includes eight goal HTTP/domain/concurrency cases and one file restart/encrypted restore scenario.
- Complete desktop/mobile browser suite: 43 passed, one existing intentional mobile restore skip. Goal cases cover an annual bill, holiday and emergency buffer, lost successful response/status recovery, stale draft retention, cancellation, account evidence and access-loss redaction. Both goal screenshots were visually inspected.
- Frontend build and 20 domain tests; 37 delivery coordination tests; nine advisory tests and current advisory gate; exposure-script test and declared delivery artifact checks passed. OKF: 43 files, zero errors/warnings.
- Initial sandbox runs could not establish loopback connections; reruns with the required local access passed. Early browser locator/route synchronization defects were corrected. The goal fixture now leaves its independent cash account reconciled; final full browser run passed with all UI edits complete.
- Current base main build and Windows packaging are green. Its inherited trusted delivery audit returned GitHub HTTP 504 while listing PRs; that failure is not a GBD-22 test result. Remote feature-head CI, independent opening review and human merge are separate evidence after publication.

Owner handoff correction: a matched paid bill now has zero remaining reservation, gap and active contribution, while retaining original goal decisions. Unlink restores the unpaid reservation; closing a paid goal preserves its commitment/payment. New match → competing reservation → unlink → rematch → close regression verifies exact cash, capacity, audit and preserved actuals. Related budget/commitment/goal/recovery subset: 44 passed. Final goal/recovery rerun: 10 passed. Frontend rebuild and six desktop/mobile goal/bill/money-position journeys passed; updated screenshot inspected. No second PR or independent review was requested for this correction.
