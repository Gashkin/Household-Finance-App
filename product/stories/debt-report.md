# Portable debt comparison report

A household can download its current comparison to revisit inputs and assumptions later, without server persistence.

The server validates and recomputes all values using the comparison's existing strict parser and deterministic service. Report v1 includes ISO generation timestamp, EUR currency, schema/calculation versions, inputs, results, assumptions and warnings. The UI exports this exact report as JSON. Editing debt inputs, applying another saved cash snapshot or starting another calculation clears the report; stale responses are ignored. No import, external sharing, database write or execution of financial actions.

Run frontend `npm ci && npm run build`; backend `mvn verify`. HTTP regressions cover a known amortisation fixture, warnings/provenance and strict invalid input rejection. Use Compare scenarios, then Download comparison report. Keep the downloaded report private when entering real figures.

Rates are sent and exported as decimal text to preserve user-entered precision across JSON/JavaScript round trips. The server accepts a validated decimal string as well as the existing numeric request format. Node transport regressions run with `node --experimental-strip-types --test tests/*.test.ts` from frontend. They cover exact-rate transfer, actionable network failures, validation messages and server-error masking.

Integration: this PR is stacked on cash-position PR #14; merge that story first and retarget the report PR to main. Applied immutable cash evidence is embedded in the report with its source identifier. Applying another snapshot or manually editing liquidity/reserve invalidates the report and comparison. Draft evidence edits do not mutate an already applied snapshot.
