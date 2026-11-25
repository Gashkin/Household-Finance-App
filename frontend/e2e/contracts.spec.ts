import { expect, test } from '@playwright/test';
import { BACKEND_URL, unlockFixture } from './workspace-helper';
import { openWorkspace } from './workspace-navigation';

const headers = { 'X-Workspace-Request': '1' };
test.beforeEach(unlockFixture);
test.afterEach(async ({ request }) => {
  await request.post(BACKEND_URL + '/api/workspace/lock', { headers });
});

test('review a rolling subscription, follow the alert, and record provider closure', async ({ page }, info) => {
  const name = 'Synthetic streaming ' + info.project.name + ' ' + Date.now();
  const inboxResponse = await page.request.get(BACKEND_URL + '/api/contracts/alerts');
  expect(inboxResponse.ok()).toBeTruthy();
  const { today } = await inboxResponse.json();
  const tomorrow = new Date(Date.parse(today) + 86_400_000).toISOString().slice(0, 10);
  await page.goto('/');
  await openWorkspace(page, 'Contracts & subscriptions');
  await page.getByRole('button', { name: 'Add contract or subscription' }).click();
  await page.getByLabel('Contract name').fill(name);
  await page.getByLabel('Record type').selectOption('SUBSCRIPTION');
  await page.getByLabel('Provider', { exact: true }).fill('Synthetic Video');
  await page.getByLabel('Billing frequency').selectOption('MONTHLY');
  await page.getByLabel('Review target').fill(today);
  await page.getByLabel('Level of usage').selectOption('UNUSED');
  await page.getByLabel('Cancellation assessment').selectOption('CONSIDER_CANCELLING');
  await page.getByText('Dates and evidence', { exact: true }).click();
  const notice = page.getByRole('group', { name: 'Notice deadline', exact: true });
  await notice.getByLabel('Certainty').selectOption('ESTIMATED');
  await notice.getByLabel('Date', { exact: true }).fill(tomorrow);
  await notice.getByLabel('Source', { exact: true }).fill('Synthetic estimate');
  await page.getByRole('button', { name: 'Save contract', exact: true }).click();
  await expect(page.getByRole('heading', { name, exact: true })).toBeVisible();
  await expect(page.getByText('No verified cutoff', { exact: true })).toBeVisible();
  await expect(page.getByText('Estimated · ' + tomorrow)).toBeVisible();

  await page.getByRole('button', { name: /Review inbox/ }).click();
  const alert = page.getByRole('listitem').filter({ has: page.getByRole('link', { name, exact: true }) });
  await expect(alert).toContainText('Due · Unread');
  await alert.getByRole('button', { name: 'Mark read' }).click();
  await expect(alert.getByRole('button', { name: 'Mark read' })).toHaveCount(0);
  await alert.getByLabel('Snooze until').fill(tomorrow);
  await alert.getByRole('button', { name: 'Snooze', exact: true }).click();
  await expect(alert).toContainText('Snoozed until ' + tomorrow);
  await page.reload();
  await expect(alert).toContainText('Snoozed until ' + tomorrow);
  await alert.getByRole('link', { name, exact: true }).click();
  await expect(page).toHaveURL(/contract=.*&term=/);
  await expect(page.getByRole('heading', { name, exact: true })).toBeVisible();

  await page.getByRole('button', { name: 'Review decision', exact: true }).click();
  await page.getByRole('combobox', { name: 'Decision', exact: true }).selectOption('CANCEL');
  await page.getByLabel('Decision notes or evidence').fill('Synthetic assessment');
  await page.getByRole('button', { name: 'Record decision', exact: true }).click();
  await expect(page.getByText('Subscription · Active · Private')).toBeVisible();
  await expect(page.getByText(/Decision: Cancel/)).toBeVisible();

  await page.getByRole('button', { name: 'Log attempted action', exact: true }).click();
  await page.getByLabel('Action record').fill('Synthetic request sent');
  await page.getByRole('button', { name: 'Record attempted action', exact: true }).click();
  await expect(page.getByText(/Attempted: Cancel/)).toBeVisible();
  await page.getByRole('button', { name: 'Record provider confirmation', exact: true }).click();
  await page.getByRole('combobox', { name: 'Provider outcome', exact: true }).selectOption('CANCELLED');
  await page.getByLabel('Provider evidence').fill('Synthetic confirmation reference');
  await page.getByRole('button', { name: 'Save provider confirmation', exact: true }).click();
  await expect(page.getByText('Subscription · Closed · Private')).toBeVisible();
  await expect(page.getByText(/Provider confirmed cancelled/)).toBeVisible();
  await page.getByRole('button', { name: /Review inbox/ }).click();
  await expect(alert).toContainText('Completed');
  await expect(alert.getByRole('button')).toHaveCount(0);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy();
});
