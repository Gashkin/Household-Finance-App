"""Inspect selected sheets in an explicitly supplied local workbook."""
import argparse
from pathlib import Path
from openpyxl import load_workbook

parser = argparse.ArgumentParser(description="Inspect workbook formulas and sample values")
parser.add_argument("--input", required=True, type=Path, help="Local workbook path, stored outside the repository")
parser.add_argument("--sheets", nargs="*", default=[], help="Optional sheet names; default is all sheets")
args = parser.parse_args()
path = args.input.expanduser()
if not path.is_file():
    parser.error(f"Workbook not found: {path}")
wb = load_workbook(path, data_only=False, read_only=True)
selected = args.sheets or wb.sheetnames
for name in selected:
    if name not in wb.sheetnames:
        print(f"Sheet not found: {name}")
        continue
    ws = wb[name]
    print(f"=== {name} ===")
    print("rows", ws.max_row, "cols", ws.max_column)
    for row in ws.iter_rows(min_row=1, max_row=min(ws.max_row, 20), values_only=False):
        parts = []
        for cell in row:
            value = cell.value
            if value is None:
                continue
            text = (value if isinstance(value, str) else str(value))[:120]
            parts.append(f"FORMULA:{text}" if cell.data_type == "f" else text)
        if parts:
            print(" | ".join(parts))
    print()

print("defined names:")
for defined_name in wb.defined_names:
    print(defined_name)
