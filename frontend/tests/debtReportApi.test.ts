import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createDebtReport } from '../src/api/debtScenarios.ts';
const input = { balanceMinor: 6000, annualRatePercent: '0.69999999999999999', monthlyPaymentMinor: 6000, extraPaymentMinor: 0, liquidCashMinor: 50000, minimumReserveMinor: 0 };
test('report transport preserves exact rate text and the authoritative response', async () => {
  const original = globalThis.fetch;
  const report = { input, result: { interestSavedMinor: 0 }, calculationVersion: 'v1' };
  try {
    globalThis.fetch = async (_url, options) => {
      assert.equal(JSON.parse(String(options?.body)).annualRatePercent, '0.69999999999999999');
      return new Response(JSON.stringify(report));
    };
    assert.deepEqual(await createDebtReport(input), report);
  } finally { globalThis.fetch = original; }
});
test('report transport explains a service outage', async () => {
  const original = globalThis.fetch;
  try {
    globalThis.fetch = async () => { throw new TypeError('Failed to fetch'); };
    await assert.rejects(createDebtReport(input), /Cannot reach the financial calculation service/);
  } finally { globalThis.fetch = original; }
});
test('report transport retains validation feedback and masks server errors', async () => {
  const original = globalThis.fetch;
  try {
    globalThis.fetch = async () => new Response('Balance is required', { status: 400 });
    await assert.rejects(createDebtReport(input), /Balance is required/);
    globalThis.fetch = async () => new Response('Internal details', { status: 500 });
    await assert.rejects(createDebtReport(input), /Unable to prepare the comparison report/);
  } finally { globalThis.fetch = original; }
});
