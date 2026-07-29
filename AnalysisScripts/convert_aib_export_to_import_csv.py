from __future__ import annotations

import argparse
import csv
import datetime as dt
from pathlib import Path


def get_value(row: dict[str, str], key: str) -> str:
    if key in row:
        return row.get(key, "")
    trimmed = key.strip()
    for existing_key, value in row.items():
        if (existing_key or "").strip() == trimmed:
            return value or ""
    return ""


def parse_amount(raw: str) -> float:
    text = (raw or "").strip()
    if not text:
        return 0.0
    text = text.replace("€", "").replace(",", "")
    return float(text)


def parse_date_iso(processed: str, txn_datetime: str) -> str:
    processed = (processed or "").strip().strip('"')
    txn_datetime = (txn_datetime or "").strip().strip('"')

    if processed:
        parsed = dt.datetime.strptime(processed, "%d/%m/%Y").date()
        return parsed.isoformat()

    if txn_datetime:
        # Expected sample: "08:45, 16/06/2026"
        if "," in txn_datetime:
            txn_datetime = txn_datetime.split(",", 1)[1].strip()
        parsed = dt.datetime.strptime(txn_datetime, "%d/%m/%Y").date()
        return parsed.isoformat()

    raise ValueError("Missing both Processed and Transaction date/time columns")


def normalize_spaces(value: str) -> str:
    return " ".join((value or "").split())


def convert_rows(input_path: Path, output_path: Path, account: str, category_in_description: bool) -> tuple[int, int]:
    written = 0
    skipped = 0

    output_path.parent.mkdir(parents=True, exist_ok=True)

    with input_path.open("r", encoding="utf-8-sig", newline="") as src, output_path.open(
        "w", encoding="utf-8", newline=""
    ) as dst:
        reader = csv.DictReader(src)
        writer = csv.writer(dst)
        writer.writerow(["date", "description", "amount", "account"])

        for row in reader:
            try:
                paid_out = parse_amount(get_value(row, "Paid out"))
                paid_in = parse_amount(get_value(row, "Paid in"))
                amount = round(paid_in - paid_out, 2)

                if amount == 0:
                    skipped += 1
                    continue

                date_iso = parse_date_iso(
                    get_value(row, "Processed"),
                    get_value(row, "Transaction date/time"),
                )

                description = normalize_spaces(get_value(row, "Description"))
                category = normalize_spaces(get_value(row, "Category"))

                if category_in_description and category:
                    description = f"{description} [{category}]"

                if not description:
                    skipped += 1
                    continue

                writer.writerow([date_iso, description, f"{amount:.2f}", account])
                written += 1
            except Exception:
                skipped += 1

    return written, skipped


def main() -> int:
    parser = argparse.ArgumentParser(description="Convert AIB card export CSV to app import CSV format.")
    parser.add_argument("--input", required=True, help="Path to AIB export CSV")
    parser.add_argument("--output", required=True, help="Path to output import CSV")
    parser.add_argument("--account", default="AIB", help="Account name to write in output rows")
    parser.add_argument(
        "--category-in-description",
        action="store_true",
        help="Append source category to description to help manual categorization",
    )

    args = parser.parse_args()
    input_path = Path(args.input).resolve()
    output_path = Path(args.output).resolve()

    if not input_path.exists():
        raise FileNotFoundError(f"Input file not found: {input_path}")

    written, skipped = convert_rows(
        input_path=input_path,
        output_path=output_path,
        account=args.account,
        category_in_description=args.category_in_description,
    )

    print(f"Input: {input_path}")
    print(f"Output: {output_path}")
    print(f"Written rows: {written}")
    print(f"Skipped rows: {skipped}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
