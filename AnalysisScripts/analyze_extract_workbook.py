from __future__ import annotations

import argparse
import csv
import json
import re
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any

from openpyxl import load_workbook


CURRENT_MONTH_SHEET = "Jun26"


@dataclass
class ExtractedAccount:
    name: str
    accountType: str
    balance: float
    sourceSheet: str
    sourceRow: int


@dataclass
class ExtractedCommitment:
    title: str
    amount: float
    dueDate: str
    kind: str
    sourceSheet: str
    sourceRow: int


@dataclass
class ExtractionWarning:
    sheetName: str
    rowNumber: int
    message: str


def normalize(value: str) -> str:
    return "".join(ch for ch in value.lower() if ch.isalnum())


def is_monthly_budget_sheet(sheet_name: str) -> bool:
    return normalize(sheet_name) == "monthlybudget"


def is_month_suffix_sheet(sheet_name: str) -> bool:
    return normalize(sheet_name).endswith("26")


def is_current_month_sheet(sheet_name: str) -> bool:
    return normalize(sheet_name) == normalize(CURRENT_MONTH_SHEET)


def is_bank_account_label(label: str) -> bool:
    normalized = normalize(label)
    return (
        "checking" in normalized
        or "savings" in normalized
        or "bank" in normalized
        or "creditcard" in normalized
    )


def parse_amount(raw: Any) -> float:
    if raw is None:
        return 0.0
    if isinstance(raw, (int, float)):
        return round(float(raw), 2)

    text = str(raw).strip()
    if not text:
        return 0.0

    negative = text.startswith("(") and text.endswith(")")
    cleaned = text.replace("$", "").replace(",", "").replace("(", "").replace(")", "")

    match = re.search(r"-?\d+(?:\.\d+)?", cleaned)
    if not match:
        return 0.0

    value = float(match.group(0))
    if negative and value > 0:
        value = -value
    return round(value, 2)


def first_non_blank(values: list[Any]) -> str:
    for value in values:
        if value is None:
            continue
        text = str(value).strip()
        if text:
            return text
    return ""


def build_header_index(header_cells: list[Any]) -> dict[str, int]:
    index: dict[str, int] = {}
    for column_index, value in enumerate(header_cells):
        if value is None:
            continue
        key = normalize(str(value))
        if key and key not in index:
            index[key] = column_index
    return index


def value_from_row(row_values: list[Any], header_index: dict[str, int], aliases: list[str], fallback_col: int | None = None) -> str:
    for alias in aliases:
        key = normalize(alias)
        if key in header_index:
            column = header_index[key]
            if column < len(row_values):
                value = row_values[column]
                if value is not None and str(value).strip():
                    return str(value).strip()
    if fallback_col is not None and fallback_col < len(row_values):
        value = row_values[fallback_col]
        if value is not None and str(value).strip():
            return str(value).strip()
    return ""


def extract_workbook(workbook_path: Path) -> dict[str, Any]:
    workbook = load_workbook(workbook_path, read_only=True, data_only=True)

    targeted_sheets: list[str] = []
    ignored_sheets: list[str] = []
    warnings: list[ExtractionWarning] = []
    accounts: list[ExtractedAccount] = []
    recurring_commitments: list[ExtractedCommitment] = []
    one_off_commitments: list[ExtractedCommitment] = []

    for sheet_name in workbook.sheetnames:
        sheet = workbook[sheet_name]

        rows = list(sheet.iter_rows(values_only=True))
        if not rows:
            ignored_sheets.append(sheet_name)
            warnings.append(ExtractionWarning(sheet_name, 1, "Sheet is empty and ignored."))
            continue

        header_index = build_header_index(list(rows[0]))
        data_rows = rows[1:]

        if is_monthly_budget_sheet(sheet_name):
            targeted_sheets.append(sheet_name)
            for row_number, row_values in enumerate(data_rows, start=2):
                row_list = list(row_values)
                title = value_from_row(row_list, header_index, ["metric", "commitment", "title", "name", "category"], fallback_col=0)
                amount_text = value_from_row(row_list, header_index, ["amount", "value", "budget"], fallback_col=1)
                amount = parse_amount(amount_text)

                if not title:
                    if first_non_blank(row_list):
                        warnings.append(ExtractionWarning(sheet_name, row_number, "Monthly Budget row skipped: commitment title is missing."))
                    continue

                recurring_commitments.append(
                    ExtractedCommitment(
                        title=title,
                        amount=amount,
                        dueDate="Recurring",
                        kind="Recurring",
                        sourceSheet=sheet_name,
                        sourceRow=row_number,
                    )
                )
            continue

        if is_month_suffix_sheet(sheet_name):
            targeted_sheets.append(sheet_name)
            current_month = is_current_month_sheet(sheet_name)

            for row_number, row_values in enumerate(data_rows, start=2):
                row_list = list(row_values)
                name = value_from_row(row_list, header_index, ["metric", "name", "account", "title", "category"], fallback_col=0)
                amount_text = value_from_row(row_list, header_index, ["amount", "value", "balance", "budget"], fallback_col=1)
                amount = parse_amount(amount_text)

                if not name:
                    if first_non_blank(row_list):
                        warnings.append(ExtractionWarning(sheet_name, row_number, "Month-suffix row skipped: name/metric is missing."))
                    continue

                if current_month and is_bank_account_label(name):
                    accounts.append(
                        ExtractedAccount(
                            name=name,
                            accountType="Bank",
                            balance=amount,
                            sourceSheet=sheet_name,
                            sourceRow=row_number,
                        )
                    )
                else:
                    one_off_commitments.append(
                        ExtractedCommitment(
                            title=name,
                            amount=amount,
                            dueDate=sheet_name,
                            kind="OneOff",
                            sourceSheet=sheet_name,
                            sourceRow=row_number,
                        )
                    )
            continue

        ignored_sheets.append(sheet_name)
        warnings.append(
            ExtractionWarning(
                sheet_name,
                1,
                "Sheet ignored by targeted upload rules. Only Monthly Budget and tabs ending in 26 are extracted.",
            )
        )

    workbook.close()

    all_commitments = recurring_commitments + one_off_commitments
    return {
        "summary": {
            "workbook": str(workbook_path),
            "sheetCount": len(targeted_sheets) + len(ignored_sheets),
            "targetedSheetCount": len(targeted_sheets),
            "ignoredSheetCount": len(ignored_sheets),
            "targetedSheets": targeted_sheets,
            "ignoredSheets": ignored_sheets,
            "accounts": len(accounts),
            "recurringCommitments": len(recurring_commitments),
            "oneOffCommitments": len(one_off_commitments),
            "allCommitments": len(all_commitments),
            "warnings": len(warnings),
        },
        "accounts": [asdict(item) for item in accounts],
        "commitmentsRecurring": [asdict(item) for item in recurring_commitments],
        "commitmentsOneOff": [asdict(item) for item in one_off_commitments],
        "commitments": [asdict(item) for item in all_commitments],
        "warnings": [asdict(item) for item in warnings],
    }


def write_json(path: Path, payload: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2), encoding="utf-8")


def write_csv(path: Path, rows: list[dict[str, Any]], columns: list[str]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=columns)
        writer.writeheader()
        for row in rows:
            writer.writerow({column: row.get(column, "") for column in columns})


def main() -> None:
    parser = argparse.ArgumentParser(description="Analyze workbook and extract loader-ready targeted data.")
    parser.add_argument(
        "--workbook",
        default="Docs/House_v2 (3).xlsx",
        help="Path to the source workbook (.xlsx).",
    )
    parser.add_argument(
        "--output-dir",
        default="AnalysisScripts/output",
        help="Directory where analysis and extraction files are written.",
    )
    parser.add_argument(
        "--format",
        choices=["json", "csv", "both"],
        default="both",
        help="Output format for extracted artifacts.",
    )
    args = parser.parse_args()

    workbook_path = Path(args.workbook)
    if not workbook_path.exists():
        raise SystemExit(f"Workbook not found: {workbook_path}")

    output_dir = Path(args.output_dir)
    extraction = extract_workbook(workbook_path)

    summary = extraction["summary"]
    print("Workbook analysis complete")
    print(f"- Workbook: {summary['workbook']}")
    print(f"- Targeted tabs: {summary['targetedSheetCount']} / {summary['sheetCount']}")
    print(f"- Ignored tabs: {summary['ignoredSheetCount']}")
    print(f"- Accounts extracted: {summary['accounts']}")
    print(f"- Recurring commitments extracted: {summary['recurringCommitments']}")
    print(f"- One-off commitments extracted: {summary['oneOffCommitments']}")
    print(f"- Warnings: {summary['warnings']}")

    write_json(output_dir / "workbook-analysis-summary.json", summary)

    if args.format in ("json", "both"):
        write_json(output_dir / "loader-extract.json", extraction)

    if args.format in ("csv", "both"):
        write_csv(
            output_dir / "accounts.csv",
            extraction["accounts"],
            ["name", "accountType", "balance", "sourceSheet", "sourceRow"],
        )
        write_csv(
            output_dir / "commitments-recurring.csv",
            extraction["commitmentsRecurring"],
            ["title", "amount", "dueDate", "kind", "sourceSheet", "sourceRow"],
        )
        write_csv(
            output_dir / "commitments-oneoff.csv",
            extraction["commitmentsOneOff"],
            ["title", "amount", "dueDate", "kind", "sourceSheet", "sourceRow"],
        )
        write_csv(
            output_dir / "warnings.csv",
            extraction["warnings"],
            ["sheetName", "rowNumber", "message"],
        )

    print(f"Output written to: {output_dir}")


if __name__ == "__main__":
    main()
