# GBD-7: Portable, reproducible debt decisions

Canonical story: https://app.notion.com/p/418d3419fef081db956af0c5d54865a0

A household member can calculate, inspect and download a debt decision with its inputs, evidence and limitations intact. This complete story restores the code stranded by PR #16 and adds the preview and real browser verification. Its PR targets `main` directly; no feature-branch target or additional UI/API/test PR is needed.

## Use locally

Requires Java 21, Maven and Node 22. Use synthetic data until the security limitations in SECURITY.md are addressed.

1. In `backend`: `mvn org.springframework.boot:spring-boot-maven-plugin:3.2.5:run`.
2. In `frontend`: `npm ci` then `npm run dev`. Open http://localhost:5173.
3. Optionally record dated cash accounts, next-30-day commitments, evidence source and reserve; explicitly apply the saved position.
4. Enter the debt, exact annual rate, monthly payment and extra repayment; choose Compare scenarios.
5. Review the comparison and report preview: inputs, source/verification state, warnings, assumptions, timestamp and calculation version. Choose Download JSON report. Store the file privately.

Edits, a newly applied snapshot or a new request clear the prior report. Late responses cannot restore stale results. An unavailable calculation service shows an actionable error. Draft cash edits do not change previously saved immutable evidence. No decision is persisted, payment initiated or external data shared by exporting.

## Contract and reproducibility

`POST /api/debt-scenarios/report` accepts the same strict inputs as `/compare`. Amounts are integer euro cents. Annual rates can be exact decimal strings (recommended) or JSON numbers. The response contains schema/calculation versions, generation time, EUR currency, validated inputs, backend results and an optional complete cash snapshot. Rates are exported as strings so JSON/JavaScript does not round them. A manual input omits the snapshot ID; a linked input must match the local saved snapshot's cash and reserve. Client-supplied results never determine calculations.

Re-post the exported `input` to `/api/debt-scenarios/report` on the same local instance to reproduce `result`; timestamps naturally change. The embedded evidence remains readable without that instance. Another instance cannot verify the original snapshot ID: no report-import or cross-instance trust mechanism is supplied. Manual calculations without evidence may be reproduced elsewhere, but must not claim verified source linkage. Assumptions exclude fees, tax, variable rates and future income.

## Verification

- `cd frontend && node --experimental-strip-types --test tests/*.test.ts && npm run build`
- `cd backend && mvn verify`
- `cd frontend && npx playwright install --with-deps chromium && npm run test:e2e`

Playwright starts a loopback backend with a fresh in-memory H2 store plus Vite. Desktop and mobile Chromium journeys record synthetic evidence, compare at an exact rounding boundary, inspect the preview, download/read JSON, reproduce results via the real API, invalidate edited results, simulate an outage and reject delayed responses. CI retains synthetic screenshots and failure traces. Browser setup belongs to this feature story.

## Integration and completion

Implementation is **In Review**, not Done, until the human merges its main-targeted PR, main CI passes and `/report`, the preview/download, backend regressions and browser tests are verified in the `main` tree. PR #16's feature-branch-only merge is historical evidence, not completion. Claim branch `story/7-portable-debt-decisions` retains execution ownership through review. Broader automated claim/post-merge enforcement belongs to GBD-8.
