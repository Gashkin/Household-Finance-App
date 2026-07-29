# ITEM-030: Add OKF Contributor Onboarding to README

## Objective
Add a concise OKF-first onboarding section to the repository README so contributors and agents discover the knowledge model entry points immediately.

## Scope
- Add an OKF section in README with:
  - OKF root index path
  - quickstart path
  - validation command
  - brief rule to use OKF concepts before legacy docs
- Keep existing loader-script instructions intact.

## Non-goals
- Changing application behavior or build configuration.
- Rewriting existing workflow/process docs.

## Inputs
- README.md
- knowledge/okf/index.md
- knowledge/okf/quickstart.md
- AnalysisScripts/validate_okf.py

## Checklist
- [x] README includes a dedicated OKF onboarding section.
- [x] OKF section links to root index and quickstart.
- [x] Validation command uses workspace venv python executable path.
- [x] Existing loader section remains unchanged.

## Deliverables
- README.md
- backlog/ordered-backlog.md
- backlog/work-orders/ITEM-030.md
- progress.md

## Evidence
- Date: 2026-07-27
- Status: Completed
- Commands run:
  - .venv/Scripts/python.exe AnalysisScripts/validate_okf.py
- Key outputs:
  - Files scanned: 37
  - Errors: 0
  - Warnings: 0
