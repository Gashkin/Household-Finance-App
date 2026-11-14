import assert from 'node:assert/strict';
import test from 'node:test';
import { money, minorToInput, parseMoney } from '../src/domain/money.ts';

test('parses euros as exact minor units', () => {
  assert.equal(parseMoney('0', 'Cash'), 0);
  assert.equal(parseMoney('1.2', 'Cash'), 120);
  assert.equal(parseMoney('1234.56', 'Cash'), 123456);
  assert.equal(minorToInput(123456), '1234.56');
});

test('rejects invalid and unsafe money values before an HTTP call', () => {
  for (const invalid of ['-1', '1.234', '1e3', 'abc', '1000000000000000', '']) {
    assert.throws(() => parseMoney(invalid, 'Cash'));
  }
  assert.throws(() => minorToInput(-1));
});

test('renders signed cent precision at safe integer limits without floating point loss', () => {
  assert.equal(money(9007199254740991), '€90,071,992,547,409.91');
  assert.equal(money(-9007199254740991), '-€90,071,992,547,409.91');
  assert.equal(money(-1), '-€0.01');
});
