# Contract and subscription reviews (GBD-32)

Open **Planning → Contracts & subscriptions**. Add the provider, plan and current term. Use a rolling term for an open-ended subscription. Clear **Review this term** for a record with no household action. New contracts are private. The record owner can share a record with household readers after confirming their password in Household access. Shared terms, assessments and notes are visible to readers. Linked bill and transaction identifiers remain visible only to the contract owner.

Record term end, notice deadline, promotion expiry, price-change date and next charge independently. Mark a verified date only when you can cite the source. The app suggests a review 30 calendar days before the earliest verified term end, notice, promotion or price-change cutoff. Enter an internal review date when you want an earlier review or when the cutoff is uncertain. A date for the next charge is billing evidence. Price changes within a term preserve earlier saved versions.

## Review and update a contract

- Open **Review inbox** or use the unread review link in the workspace header. Open the term directly from the alert. You can mark it read, snooze, dismiss or reopen it. Dismissing an alert keeps the review in the inbox with its disposition.
- Record your decision and any attempted provider action. For a renewal, switch or cancellation, record provider confirmation separately with its evidence and effective date. A renewal or switch creates a replacement term. A future outcome takes effect on its recorded date. Closed records can be reactivated with a new term.
- Link an existing recurring bill or supporting transaction when relevant. Once you have confirmed a change, open the linked bill in **Plan ahead**. Preview and apply the relevant effective-dated change there. The contract register does not write forecast charges, update schedules or contact the provider.

## Calendar and recovery

The server evaluates date-only reviews in `WORKSPACE_TIME_ZONE` (default `Europe/Dublin`). It evaluates at startup, on relevant changes and once a minute while running. After a stop or sleep it catches up on the current date. You can see alerts when you open the app. Email, push and calendar delivery are tracked in GBD-40.

Migration V5 adds contract and review alert records in place. Workspace backup version 8 includes both. Current releases also restore versions 1–7, which predate contract records. **Export visible contracts** downloads the contracts your account can read. Use the encrypted workspace backup for recovery.
