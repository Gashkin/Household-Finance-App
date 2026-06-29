from openpyxl import load_workbook
from pathlib import Path

path = Path('Docs/House_v2 (3).xlsx')
wb = load_workbook(path, data_only=False, read_only=True)

selected = ['MonthlyBudget', 'Becky2024', 'TaxCalcs', 'Renovation Financing', 'CC Txns', 'BeckyDublins', 'Oct_18', 'Jan_19', 'Jan_24', 'Jan_25', 'Jan_26']
for name in selected:
    if name not in wb.sheetnames:
        continue
    ws = wb[name]
    print(f'=== {name} ===')
    print('rows', ws.max_row, 'cols', ws.max_column)
    for row in ws.iter_rows(min_row=1, max_row=min(ws.max_row, 20), values_only=False):
        parts = []
        for cell in row:
            value = cell.value
            if value is None:
                continue
            if isinstance(value, str):
                text = value[:120]
            else:
                text = str(value)[:120]
            if cell.data_type == 'f':
                parts.append(f'FORMULA:{text}')
            else:
                parts.append(text)
        if parts:
            print(' | '.join(parts))
    print()

print('defined names:')
for dn in wb.defined_names:
    print(dn)
