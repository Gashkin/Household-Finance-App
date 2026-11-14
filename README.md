# Household-Finance-App

## Dated cash evidence → debt decision (current product slice)

Run the Java 21 / Spring Boot backend with `cd backend && mvn spring-boot:run`. In another terminal run
`cd frontend && npm ci && npm run dev`, then open http://localhost:5173.

The **Debt decision** workspace lets you save a dated household cash position (cash accounts, next 30 days'
commitments, source/status and protected reserve), then explicitly apply that position to the loan comparison.
Both the cash totals and loan calculation run in the backend. Saved snapshots remain in local H2 storage;
the comparison itself is not saved. See [the journey and API notes](product/dated-cash-position-journey.md).

**Security boundary:** the current app has no real authentication or household isolation. It is for local
development only; do not expose the backend publicly or enter sensitive data into an internet-facing instance.

## OKF Knowledge Model

This repository uses an OKF-first knowledge model for contributor and agent context.

- Start here: `knowledge/okf/index.md`
- Day-to-day usage: `knowledge/okf/quickstart.md`
- Validation command: `.venv/Scripts/python.exe AnalysisScripts/validate_okf.py`

Rule of use:
- Use OKF concepts first, then consult legacy docs (`spec/`, `ops/`, `adr/`, `backlog/`) only when verification or gap resolution is needed.

## Loader script (one-off import)

Prepare a manual JSON file with the values you want loaded, then run the loader script to write accounts/commitments into the backend planner store.

### Dry run

```bash
python AnalysisScripts/load_extracted_data.py --input AnalysisScripts/manual-load.json --mode merge --dry-run
```

### Apply merge

```bash
python AnalysisScripts/load_extracted_data.py --input AnalysisScripts/manual-load.json --mode merge
```

### Apply replace

```bash
python AnalysisScripts/load_extracted_data.py --input AnalysisScripts/manual-load.json --mode replace
```

Notes:
- Backend must be running on `http://localhost:8080` (or set `--base-url`).
- Categories are optional and only loaded when `--include-categories` is provided.
- Every run writes a report to `AnalysisScripts/output/load-report.json`.

## Portable debt decision reports

Use the Debt decision workspace to record cash evidence, compare repayment options, review the report and download its JSON record. See [GBD-7 local use, report contract and verification](product/stories/debt-report.md). This is a local-development application; see [security limitations](SECURITY.md) before entering real household data.

## Autonomous delivery coordination

See [claim, publish, recover and verify a story](delivery/README.md). Run `python -m delivery.demo` for a credential-free two-worker lifecycle demonstration. CI independently checks story identity/reviews and verifies that human-merged capabilities actually reach main.
