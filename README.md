# Household-Finance-App

Household-Finance-App

## Spreadsheet analysis and extraction

Use the one-off analysis/extraction script to inspect workbook tabs and export loader-ready data.

### Command

Install script dependencies first:

```bash
python -m pip install -r AnalysisScripts/requirements.txt
```

```bash
python AnalysisScripts/analyze_extract_workbook.py --workbook "Docs/House_v2 (3).xlsx" --output-dir AnalysisScripts/output --format both
```

### What it extracts

- Target tabs only: `Monthly Budget` (or `MonthlyBudget`) and tabs ending in `26`
- Recurring commitments from `Monthly Budget`
- One-off commitments from `*26` tabs
- Current balances from `Jun26` bank-like labels (checking/savings/bank/credit card)

### Output files

- `AnalysisScripts/output/workbook-analysis-summary.json`
- `AnalysisScripts/output/loader-extract.json`
- `AnalysisScripts/output/accounts.csv`
- `AnalysisScripts/output/commitments-recurring.csv`
- `AnalysisScripts/output/commitments-oneoff.csv`
- `AnalysisScripts/output/warnings.csv`

## Loader script (one-off import)

After extraction, run the loader script to write accounts/commitments into the backend planner store.

### Dry run

```bash
python AnalysisScripts/load_extracted_data.py --input AnalysisScripts/output/loader-extract.json --mode merge --dry-run
```

### Apply merge

```bash
python AnalysisScripts/load_extracted_data.py --input AnalysisScripts/output/loader-extract.json --mode merge
```

### Apply replace

```bash
python AnalysisScripts/load_extracted_data.py --input AnalysisScripts/output/loader-extract.json --mode replace
```

Notes:
- Backend must be running on `http://localhost:8080` (or set `--base-url`).
- Categories are optional and only loaded when `--include-categories` is provided.
- Every run writes a report to `AnalysisScripts/output/load-report.json`.
