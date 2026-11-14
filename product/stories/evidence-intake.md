# GBD-4: Reviewable evidence intake

Canonical story: https://app.notion.com/p/418d3419fef0811b9da6ead4cf827e25

Paste a short excerpt, inspect line-cited suggestions, correct them, and explicitly approve a cash position that can be used in the debt comparison and portable report. Local extraction always works without a model; model assistance is optional and never writes financial facts directly.

## Local setup and walkthrough

Requires Java 21, Maven and Node 22. This remains a single-household local-development app. Set `EVIDENCE_INTAKE_ACCESS_KEY` to a randomly generated key of at least 16 characters before starting the backend (32+ random characters recommended). For example, generate one locally with `python -c "import secrets; print(secrets.token_urlsafe(32))"`; keep it private. Without a configured key, intake is disabled. Existing backend/planner routes are not globally authenticated; do not expose this instance. The backend now binds loopback by default. See SECURITY.md.

1. Run the backend (`mvn org.springframework.boot:spring-boot-maven-plugin:3.2.5:run` in backend), then `npm ci && npm run dev` in frontend.
2. In Debt decision, expand **Paste evidence and review suggested facts**, enter the intake key, and unlock.
3. Enter a reference such as “Demo statement” and paste synthetic text:

```text
Current account: EUR 1,000.00
Savings account: EUR 2,500.00
Electricity bill due: EUR 100.00
```

4. Choose Review suggestions. Compare every editable fact with the original passage and quoted line. Correct amounts/labels/type, remove nonfacts, or add a fact tied to an original line. Explicit credit-limit/income lines are not suggested as cash. Missing/ambiguous text never becomes an authoritative fact automatically.
5. Confirm the actual balance date and protected reserve, then acknowledge that you checked the facts and unpaid bills. **Approve and use cash position** saves one reviewed cash snapshot and applies its available cash/reserve to the debt comparison.
6. Compare the debt options and inspect/download the report. It references the accepted snapshot/reference, not the original source excerpt.
7. Lock intake to clear its key and unsaved excerpt from browser memory.

Source/reference edits invalidate prior suggestions, model consent and review acknowledgement. Model consent resets after each extraction; each provider request requires a fresh checkbox opt-in. Late extraction responses cannot restore stale proposals. Approval is a deliberate record write; while it is running its source/facts cannot be edited or locked. After a network error retry the same approval: it returns the original saved snapshot; conflicting edits return 409. A lost draft after restart/expiry must be extracted again; an old ID never creates another snapshot.

## Privacy and retention

- Every protected intake request is checked by a filter **before** controller/body processing, using the instance key in `X-Intake-Key`. Keys are held only in UI memory, never browser storage or report downloads. API responses use no-store.
- This key protects intake, not the whole legacy application or multiple tenants. Accepted cash facts remain subject to the existing local-only application boundary.
- Raw source excerpts are never written to H2/files/logs by this feature. Drafts are bounded to 4000 characters/80 lines, 30 suggestions and 32 simultaneous drafts; actual request bodies over 32 KiB are rejected before JSON parsing, including chunked transfer. Drafts expire after 10 minutes and scheduled cleanup runs each minute. Approval immediately releases backend references to original lines. This is memory retention control, not guaranteed byte-level erasure by the JVM/browser.
- Approved labels/amounts and the user-supplied reference are intentionally persisted as a normal append-only cash snapshot. Do not put private document contents into the reference. The optional provider has its own retention/privacy terms outside this app's control.
- Dates, reserve, source meaning, account eligibility and whether a bill is unpaid require human review. Model suggestions and local parsing are not bank verification. Negative/unsupported amounts or currencies need separate handling; only nonnegative EUR cash/commitments are supported by this existing snapshot model.

## Optional model configuration

Off by default. Configure these **server-side only**, then restart:

- `EVIDENCE_AI_ENABLED=true`
- `EVIDENCE_AI_ENDPOINT`: complete OpenAI-compatible chat-completion endpoint URL. HTTPS and an API key are required for external hosts; HTTP without a key is allowed only for literal loopback/localhost local-model endpoints.
- `EVIDENCE_AI_MODEL`: operator-selected compatible model; no default paid model.
- `EVIDENCE_AI_KEY`: provider credential (never in frontend/Git).
- `EVIDENCE_AI_DAILY_LIMIT`: 0–20 requests per UTC day per running process; default 5. Restart resets this in-memory budget, so it is not an account-wide monetary cap.

The endpoint/model must support chat-completion JSON mode and `max_tokens`. Each request needs explicit user opt-in and uses at most 4000 source characters, a 1200 output-token limit, a bounded 32 KiB response, a 10-second deadline and no automatic retries or redirects. Common email/account/IBAN-like identifiers are redacted, but redaction is incomplete: users must remove other personal information. No model key, subscription or provider connection is needed for local suggestions. Model costs are provider/model-specific; no price estimate or free usage is promised.

Only JSON facts with bounded fields/line numbers are accepted. Every model amount must exactly occur as EUR on its cited original line; labels must occur there too. No tools, payments or arithmetic are delegated to the model. Refusal, truncation, invalid source citations/schema, missing configuration, exhausted budget, failure or timeout returns local suggestions with an explanation. There is no automatic provider disclosure merely because a provider is configured.

## API and verification

Public GET `/api/evidence-intake/capabilities` reveals availability/limits, not keys/endpoints. Protected GET `/session` validates the key; POST `/drafts` accepts `source`, `reference`, `allowModel`; DELETE `/drafts/{id}` discards memory; POST `/drafts/{id}/approve` requires explicit acknowledgement, ISO balance date, reserve text and reviewed rows with a source line/type/label/euro text. Financial arithmetic and safety limits stay in Java/CashPositionService. JSON numbers are not accepted as euro text, and fractional cents are rejected.

Run backend `mvn verify`, frontend `node --experimental-strip-types --test tests/*.test.ts` and `npm run build`, then `npm run test:e2e` (install Chromium first). Browser fixtures use a synthetic key and an in-memory H2 database. The journey unlocks, corrects source facts, approves/retries idempotently, verifies source text was not persisted, compares and downloads the linked report in desktop/mobile Chromium. Gateway tests call only a local protocol stub, never a paid/external model.

Done requires human main merge, successful main CI and delivery-guard evidence for the implementation/tests on main. One story, one PR, one automatic Copilot review.
