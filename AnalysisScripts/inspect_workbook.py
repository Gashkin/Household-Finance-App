from openpyxl import load_workbook
from pathlib import Path

path = Path('Docs/House_v2 (3).xlsx')
print('loading', path)
wb = load_workbook(path, data_only=False, read_only=True)
print('sheets', wb.sheetnames)
for ws in wb.worksheets:
    print(f'\n=== SHEET {ws.title} ===')
    print('rows', ws.max_row, 'cols', ws.max_column)
    for r_idx, row in enumerate(ws.iter_rows(min_row=1, max_row=min(ws.max_row, 20)), start=1):
        rendered = []
        for c_idx, cell in enumerate(row, start=1):
            value = cell.value
            if value is None:
                continue
            if isinstance(value, str):
                text = value[:140].replace('\n', ' ')
            else:
                text = str(value)[:140]
            if text:
                rendered.append(f'{c_idx}:{text}')
        if rendered:
            print(r_idx, ' | '.join(rendered))
