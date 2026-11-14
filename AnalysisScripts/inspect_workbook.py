"""Inspect an explicitly provided local workbook; no personal workbook ships in Git."""
import argparse
from pathlib import Path
from openpyxl import load_workbook

parser = argparse.ArgumentParser(description="List workbook sheets and preview nonempty cells")
parser.add_argument("--input", required=True, type=Path, help="Local workbook path, stored outside the repository")
args = parser.parse_args()
path = args.input.expanduser()
if not path.is_file():
    parser.error(f"Workbook not found: {path}")
print("loading", path)
wb = load_workbook(path, data_only=False, read_only=True)
print("sheets", wb.sheetnames)
for ws in wb.worksheets:
    print(f"\n=== SHEET {ws.title} ===")
    print("rows", ws.max_row, "cols", ws.max_column)
    for r_idx, row in enumerate(ws.iter_rows(min_row=1, max_row=min(ws.max_row, 20)), start=1):
        rendered = []
        for c_idx, cell in enumerate(row, start=1):
            value = cell.value
            if value is None:
                continue
            if isinstance(value, str):
                text = value[:140].replace("\n", " ")
            else:
                text = str(value)[:140]
            if text:
                rendered.append(f"{c_idx}:{text}")
        if rendered:
            print(r_idx, " | ".join(rendered))
