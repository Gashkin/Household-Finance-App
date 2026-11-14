const euro = new Intl.NumberFormat('en-IE', { style: 'currency', currency: 'EUR' });

export function money(minor: number): string {
  return euro.format(minor / 100);
}

export function parseMoney(value: string, label: string): number {
  if (!/^(?:0|[1-9]\d*)(?:\.\d{1,2})?$/.test(value)) {
    throw new RangeError(label + ' must be a non-negative euro amount with up to two decimals');
  }
  const [whole, fraction = ''] = value.split('.');
  const cents = Number(whole) * 100 + Number(fraction.padEnd(2, '0'));
  if (!Number.isSafeInteger(cents)) throw new RangeError(label + ' is too large');
  return cents;
}

export function minorToInput(minor: number): string {
  if (!Number.isSafeInteger(minor) || minor < 0) throw new RangeError('Cash amount is not usable');
  return (minor / 100).toFixed(2);
}
