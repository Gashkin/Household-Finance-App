import assert from 'node:assert/strict';
import test from 'node:test';
import { calculateDebtScenario } from '../src/domain/debtScenario.ts';

const base = {
  balanceMinor: 1_000_00,
  annualRatePercent: 12,
  monthlyPaymentMinor: 100_00,
  extraPaymentMinor: 200_00,
  liquidCashMinor: 2_000_00,
  minimumReserveMinor: 1_000_00,
};

test('extra payment reduces interest and payoff time without mutating input', () => {
  const input = { ...base };
  const result = calculateDebtScenario(input);
  assert.equal(result.baseline.months, 11);
  assert.equal(result.withExtraPayment.months, 9);
  assert.ok(result.interestSavedMinor > 0);
  assert.equal(result.monthsSaved, 2);
  assert.deepEqual(input, base);
});

test('full payoff incurs no further interest', () => {
  const result = calculateDebtScenario({ ...base, extraPaymentMinor: base.balanceMinor });
  assert.equal(result.withExtraPayment.months, 0);
  assert.equal(result.withExtraPayment.interestMinor, 0);
});

test('reserve shortfall is explicit, not silently blocked', () => {
  const result = calculateDebtScenario({ ...base, liquidCashMinor: 250_00, minimumReserveMinor: 200_00 });
  assert.equal(result.reserveShortfallMinor, 150_00);
  assert.equal(result.warnings.length, 1);
});

test('zero interest yields no interest saving', () => {
  const result = calculateDebtScenario({ ...base, annualRatePercent: 0 });
  assert.equal(result.interestSavedMinor, 0);
  assert.ok(result.warnings.some(w => w.includes('0%')));
});

test('rejects payments that never reduce principal', () => {
  assert.throws(() => calculateDebtScenario({ ...base, monthlyPaymentMinor: 1_00 }), /does not cover interest/);
});

test('rejects invalid, unsafe and unavailable amounts', () => {
  assert.throws(() => calculateDebtScenario({ ...base, balanceMinor: 1.2 }), /safe integer/);
  assert.throws(() => calculateDebtScenario({ ...base, liquidCashMinor: 100_00 }), /available liquid cash/);
  assert.throws(() => calculateDebtScenario({ ...base, annualRatePercent: NaN }), /annualRatePercent/);
  assert.throws(() => calculateDebtScenario({ ...base, maximumMonths: 0 }), /maximumMonths/);
});

test('respects calculation horizon', () => {
  assert.throws(() => calculateDebtScenario({ ...base, maximumMonths: 1 }), /maximumMonths/);
});
