# Household-Finance-App

Household-Finance-App

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
