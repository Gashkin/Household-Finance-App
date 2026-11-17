import { test, expect } from '@playwright/test';
import { unlockFixture, BACKEND_URL } from './workspace-helper';

test.beforeEach(unlockFixture);

test('legacy consumer summary never turns missing evidence into actual income or spendable money', async ({ page, request }, testInfo) => {
  const created = await request.post(`${BACKEND_URL}/planner/accounts`, {
    headers: { 'X-Workspace-Request': '1' },
    data: { id: `synthetic-consumer-${testInfo.project.name}`, name: 'Synthetic consumer evidence', type: 'Current Account', balance: 10000 },
  });
  expect(created.ok()).toBeTruthy();
  const response = await request.get(`${BACKEND_URL}/reports/monthly-overview`);
  expect(response.ok()).toBeTruthy();
  const summary = await response.json();
  expect(summary.status).toBe('UNAVAILABLE');
  expect(summary.actualInflow).toBeNull();
  expect(summary.allowableSpend).toBeNull();
  expect(summary.recentTransactions).toEqual([]);
  await page.goto('/');
  await page.getByRole('button', { name: 'Consumer', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'No verified monthly summary' })).toBeVisible();
  await expect(page.getByText('On Track', { exact: false })).toHaveCount(0);
  await expect(page.getByRole('heading', { name: 'Allowable spend' })).toHaveCount(0);
  await page.screenshot({ path: testInfo.outputPath('consumer-unknown-evidence.png'), fullPage: true });
  await page.getByRole('button', { name: 'View reviewed money position' }).click();
  await expect(page.getByRole('heading', { name: 'Household money position', exact: true })).toBeVisible();
});
