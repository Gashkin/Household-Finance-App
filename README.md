# Household-Finance-App

## Dated cash evidence → debt decision (current product slice)

First configure `WORKSPACE_PASSWORD` (16–256 characters) in the backend environment. Then run the Java 21 / Spring Boot backend with `cd backend && mvn spring-boot:run`. In another terminal run
`cd frontend && npm ci && npm run dev`, then open http://localhost:5173.

The **Debt decision** workspace lets you save a dated household cash position (cash accounts, next 30 days'
commitments, source/status and protected reserve), then explicitly apply that position to the loan comparison.
Both the cash totals and loan calculation run in the backend. Saved snapshots remain in local H2 storage;
the comparison itself is not saved. See [the journey and API notes](product/dated-cash-position-journey.md).

**Security boundary:** one password-protected local workspace, with server-enforced sessions and encrypted recovery backups. Use a private OS profile and encrypted disk: the live H2 database is not application-encrypted. No multi-user or network deployment. See [setup, backup and tested recovery](product/stories/private-workspace.md).

## OKF Knowledge Model

This repository uses an OKF-first knowledge model for contributor and agent context.

- Start here: `knowledge/okf/index.md`
- Day-to-day usage: `knowledge/okf/quickstart.md`
- Validation command: `.venv/Scripts/python.exe AnalysisScripts/validate_okf.py`

Rule of use:
- Use OKF concepts first, then consult legacy docs (`spec/`, `ops/`, `adr/`, `backlog/`) only when verification or gap resolution is needed.

## Importing saved account and commitment data

Use the authenticated **Planner** and **Migrater** workspaces for imports. The historical `AnalysisScripts/load_extracted_data.py` helper predates workspace sessions and cannot write to the protected backend; its unauthenticated requests now receive 401. Do not disable the workspace gate to run it.

## Portable debt decision reports

Use the Debt decision workspace to record cash evidence, compare repayment options, review the report and download its JSON record. See [GBD-7 local use, report contract and verification](product/stories/debt-report.md). This is a local-development application; see [security limitations](SECURITY.md) before entering real household data.

## Autonomous delivery coordination

See [claim, publish, recover and verify a story](delivery/README.md). Run `python -m delivery.demo` for a credential-free two-worker lifecycle demonstration. CI independently checks story identity/reviews and verifies that human-merged capabilities actually reach main.

## Review source evidence before updating cash

The Debt decision workspace can turn a short source excerpt into editable, cited cash/bill suggestions, then save and apply only explicitly reviewed facts. [Evidence intake setup, manual walkthrough and optional model configuration](product/stories/evidence-intake.md). Intake is locked until the local instance owner configures its access key; external model extraction is off by default.

## Private records and recovery

Unlock once to access financial workspaces; lock when finished. **Backup & recovery** downloads a passphrase-encrypted archive and restores it into an empty local workspace without overwriting existing records. [Configuration, restart/recovery walkthrough and limits](product/stories/private-workspace.md).

## Reviewed household money position

Open **Money position** to record account evidence, review manual/CSV activity, reconcile a closing statement, and reuse reconciled cash in the debt report. Pending payments, transfers, overdrafts and corrections stay explicit. [Workflow, import format, backup compatibility and banking boundary](product/stories/reviewed-money-position.md). Live bank connectivity is not included or claimed.


## GBD-10 sandbox connection

Read-only Enable Banking SANDBOX / Mock ASPSP connection is included in the same GBD-10 PR: [setup, consent, review and protection details](product/stories/enable-banking-sandbox.md). Synthetic protocol/browser evidence is distinct from an owner-run external sandbox smoke test. Production/AIB is tracked by [GBD-14](https://www.notion.so/418d3419fef081808864ca465d4eff7a), after GBD-10 integration and production security readiness/consent. No production access is enabled.
