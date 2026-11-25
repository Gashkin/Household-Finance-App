import { test, expect } from '@playwright/test';
import { openWorkspace } from './workspace-navigation';
import { BACKEND_URL, PASSWORD, unlockFixture } from './workspace-helper';

const headers = { 'X-Workspace-Request': '1' };

test.beforeEach(unlockFixture);
test.afterEach(async ({ request }) => {
  await request.post(BACKEND_URL + '/api/workspace/lock', { headers });
});

test('module links support keyboard navigation, direct links, refresh and history', async ({ page }) => {
  await page.goto('/');
  const modules = page.getByRole('navigation', { name: 'Modules', exact: true });
  await expect(modules.getByRole('link')).toHaveText(['Overview', 'Activity', 'Planning', 'Debt', 'Settings']);
  await expect(page.getByRole('link', { name: 'Money position', exact: true })).toHaveAttribute('aria-current', 'page');

  const planning = modules.getByRole('link', { name: 'Planning', exact: true });
  await planning.focus();
  await page.keyboard.press('Enter');
  await expect(page).toHaveURL(/\/workspaces\/planning\/forecast$/);
  await expect(page.getByRole('main')).toBeFocused();
  const pages = page.getByRole('navigation', { name: 'Planning pages', exact: true });
  await expect(pages.getByRole('link')).toHaveText(['Plan ahead', 'Budgets & allocations', 'Goals & irregular costs', 'Financial plans']);
  await pages.getByRole('link', { name: 'Financial plans', exact: true }).click();
  await expect(page).toHaveURL(/\/workspaces\/planning\/plans$/);
  await expect(page.getByRole('heading', { name: 'Financial plans', exact: true })).toBeVisible();
  await page.reload();
  await expect(pages.getByRole('link', { name: 'Financial plans', exact: true })).toHaveAttribute('aria-current', 'page');
  await page.goBack();
  await expect(page).toHaveURL(/\/workspaces\/planning\/forecast$/);
  await page.goForward();
  await expect(page.getByRole('heading', { name: 'Financial plans', exact: true })).toBeVisible();
  await page.goto('/workspaces/settings/recovery');
  await expect(page.getByRole('heading', { name: 'Backup & recovery', exact: true })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy();
});

test('combined roles, deep-link denial and revoked plans access', async ({ page, browser, baseURL }, info) => {
  await page.goto('/');
  await openWorkspace(page, 'Household access');
  const name = 'navigation' + Date.now();
  const password = PASSWORD + '-member';
  const invitation = await page.request.post('/api/access/invitations', { headers, data: { username: name } });
  expect(invitation.ok()).toBeTruthy();
  const { code } = await invitation.json();
  const accepted = await page.request.post('/api/access/accept', { headers, data: { code, password, accepted: true } });
  expect(accepted.ok()).toBeTruthy();
  const person = page.getByRole('group', { name: name + ' · ACTIVE', exact: true });
  await openWorkspace(page, 'Money position');
  await openWorkspace(page, 'Household access');

  const context = await browser.newContext({
    baseURL,
    viewport: info.project.name === 'mobile' ? { width: 390, height: 844 } : { width: 1280, height: 900 },
  });
  const member = await context.newPage();
  const deniedPath = '/workspaces/planning/plans';
  async function signin(path: string) {
    await member.goto(path);
    await member.getByLabel('Username', { exact: true }).fill(name);
    await member.getByLabel('Workspace password', { exact: true }).fill(password);
    await member.getByRole('button', { name: 'Unlock workspace', exact: true }).click();
  }

  try {
    const modules = member.getByRole('navigation', { name: 'Modules', exact: true });
    const denied = member.getByRole('heading', { name: 'Workspace unavailable', exact: true });
    await signin(deniedPath);
    await expect(denied).toBeVisible();
    await expect(modules.getByRole('link')).toHaveText(['Settings']);
    expect((await member.request.get('/api/plans')).status()).toBe(403);

    await person.getByLabel('ADMIN', { exact: true }).check();
    await person.getByLabel('EDITOR', { exact: true }).check();
    await person.getByRole('button', { name: 'Save permissions', exact: true }).click();
    await expect(page.getByText('Account updated. Previous sessions must sign in again.')).toBeVisible();
    await signin(deniedPath);
    await expect(member.getByRole('heading', { name: 'Financial plans', exact: true })).toBeVisible();
    expect((await member.request.get('/api/access/users')).status()).toBe(200);
    await member.goto('/workspaces/settings/recovery');
    await expect(denied).toBeVisible();
    expect((await member.request.get('/api/workspace/records')).status()).toBe(403);

    await person.getByLabel('EDITOR', { exact: true }).uncheck();
    await person.getByLabel('READER', { exact: true }).check();
    await person.getByRole('button', { name: 'Save permissions', exact: true }).click();
    await expect(page.getByText('Account updated. Previous sessions must sign in again.')).toBeVisible();
    expect((await member.request.post('/api/plans', { headers, data: {} })).status()).toBe(401);
    await signin(deniedPath);
    await expect(denied).toBeVisible();
    await expect(modules.getByRole('link')).toHaveText(['Overview', 'Activity', 'Settings']);
    expect((await member.request.post('/api/plans', { headers, data: {} })).status()).toBe(403);
    await modules.getByRole('link', { name: 'Overview', exact: true }).click();
    await expect(member.getByRole('heading', { name: 'Permitted money position', exact: true })).toBeVisible();
  } finally {
    try {
      await member.request.post('/api/workspace/lock', { headers });
    } finally {
      await context.close();
    }
  }
});
