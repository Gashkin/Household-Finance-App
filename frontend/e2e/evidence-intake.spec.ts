import { test, expect } from '@playwright/test';
import { readFile } from 'node:fs/promises';
import { unlockFixture, BACKEND_URL } from './workspace-helper';
test.use({ extraHTTPHeaders: { 'X-Workspace-Request': '1' } });
test.beforeEach(unlockFixture);

test('unlock, inspect and correct source facts, approve once, and use them in a report', async ({ page, request }, testInfo) => {
  await page.goto('/');
  await page.getByText('Paste evidence and review suggested facts', {exact:true}).click();
  await page.getByLabel('Intake access key', {exact:true}).fill('wrong');
  await page.getByRole('button', {name:'Unlock evidence intake',exact:true}).click();
  await expect(page.getByRole('alert')).toContainText('access key');
  await page.getByLabel('Intake access key', {exact:true}).fill('synthetic-browser-intake-key');
  await page.getByRole('button', {name:'Unlock evidence intake',exact:true}).click();
  await page.getByLabel('Evidence reference', {exact:true}).fill('Synthetic excerpt');
  await page.getByLabel('Source excerpt', {exact:true}).fill('Current account: EUR 999.00\nElectricity bill due: EUR 100.00\nPRIVATE SYNTHETIC SOURCE MARKER');
  const draftResponse = page.waitForResponse(response => response.url().endsWith('/api/evidence-intake/drafts') && response.request().method()==='POST');
  await page.getByRole('button', {name:'Review suggestions',exact:true}).click();
  const draft = await (await draftResponse).json();
  const review = page.getByRole('region', {name:'Review evidence suggestions'});
  await expect(review).toContainText('Current account: EUR 999.00');
  await expect(page.getByRole('button', {name:'Approve and use cash position'})).toBeDisabled();
  await page.getByLabel('Fact amount 1 (€)',{exact:true}).fill('1000.00');
  await page.getByLabel('Fact label 1',{exact:true}).fill('Reviewed current account');
  await page.getByLabel('Intake protected reserve (€)',{exact:true}).fill('400');
  await page.getByLabel('I checked these facts against the source', {exact:false}).check();
  await page.screenshot({path:testInfo.outputPath('intake-review.png'),fullPage:true});
  const approvalResponse = page.waitForResponse(response => response.url().endsWith('/approve'));
  await page.getByRole('button',{name:'Approve and use cash position',exact:true}).click();
  const response = await approvalResponse;
  const snapshot = await response.json();
  expect(snapshot.availableAfterCommitmentsMinor).toBe(90000);
  expect(JSON.stringify(snapshot)).not.toContain('PRIVATE SYNTHETIC SOURCE MARKER');
  const retry = await request.post(BACKEND_URL+'/api/evidence-intake/drafts/'+draft.id+'/approve', {
    headers:{'X-Intake-Key':'synthetic-browser-intake-key'},data:response.request().postDataJSON(),
  });
  expect(retry.ok()).toBeTruthy(); expect((await retry.json()).id).toBe(snapshot.id);
  await expect(page.getByLabel('Source excerpt',{exact:true})).toHaveValue('');
  await expect(page.getByLabel('Available cash (€)',{exact:true})).toHaveValue('900.00');
  await page.getByLabel('Debt outstanding (€)',{exact:true}).fill('1000');
  await page.getByLabel('Annual interest rate (%)',{exact:true}).fill('12');
  await page.getByLabel('Monthly repayment (€)',{exact:true}).fill('100');
  await page.getByLabel('Extra repayment now (€)',{exact:true}).fill('200');
  await page.getByRole('button',{name:'Compare scenarios',exact:true}).click();
  const preview=page.getByRole('region',{name:'Review your report before downloading'});
  await expect(preview).toContainText('Reviewed intake: Synthetic excerpt');
  const downloading=page.waitForEvent('download');
  await preview.getByRole('button',{name:'Download JSON report',exact:true}).click();
  const file=await downloading; const report=JSON.parse(await readFile((await file.path())!,'utf8'));
  expect(report.cashEvidence.id).toBe(snapshot.id); expect(report.input.liquidCashMinor).toBe(90000);
  expect(JSON.stringify(report)).not.toContain('PRIVATE SYNTHETIC SOURCE MARKER');
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth)).toBeTruthy();
  await page.getByRole('button',{name:'Lock and clear excerpt',exact:true}).click();
  await expect(page.getByLabel('Intake access key',{exact:true})).toHaveValue('');
});

test('editing the source discards old suggestions and an outage cannot approve anything', async ({ page }) => {
  await page.goto('/');
  await page.getByText('Paste evidence and review suggested facts',{exact:true}).click();
  await page.getByLabel('Intake access key',{exact:true}).fill('synthetic-browser-intake-key');
  await page.getByRole('button',{name:'Unlock evidence intake',exact:true}).click();
  await page.getByLabel('Evidence reference',{exact:true}).fill('Demo');
  await page.getByLabel('Source excerpt',{exact:true}).fill('Account: EUR 100');
  await page.getByRole('button',{name:'Review suggestions',exact:true}).click();
  await expect(page.getByRole('region',{name:'Review evidence suggestions'})).toBeVisible();
  await page.getByLabel('Source excerpt',{exact:true}).fill('Account: EUR 200');
  await expect(page.getByRole('region',{name:'Review evidence suggestions'})).toHaveCount(0);
  await page.route('**/api/evidence-intake/drafts',route=>route.abort('failed'));
  await page.getByRole('button',{name:'Review suggestions',exact:true}).click();
  await expect(page.getByRole('alert')).toContainText('Cannot reach the evidence intake service');
  await expect(page.getByRole('button',{name:'Approve and use cash position',exact:true})).toHaveCount(0);
});

test('model disclosure consent is consumed once and source edits clear it', async ({ page }) => {
  // Advertise a configured provider in the UI; the real test server has no external provider.
  await page.route('**/api/evidence-intake/capabilities', route => route.fulfill({json:{enabled:true,modelConfigured:true,modelDailyLimit:5,maxOutputTokens:1200}}));
  await page.goto('/');
  await page.getByText('Paste evidence and review suggested facts',{exact:true}).click();
  await page.getByLabel('Intake access key',{exact:true}).fill('synthetic-browser-intake-key');
  await page.getByRole('button',{name:'Unlock evidence intake',exact:true}).click();
  await page.getByLabel('Evidence reference',{exact:true}).fill('Demo');
  await page.getByLabel('Source excerpt',{exact:true}).fill('Account: EUR 100');
  const consent=page.getByRole('checkbox',{name:'Send this excerpt to the configured model',exact:false});
  await consent.check();
  let pending=page.waitForResponse(r=>r.url().endsWith('/api/evidence-intake/drafts') && r.request().method()==='POST');
  await page.getByRole('button',{name:'Review suggestions',exact:true}).click();
  expect((await pending).request().postDataJSON().allowModel).toBe(true);
  await expect(consent).not.toBeChecked();
  await consent.check();
  await page.getByLabel('Source excerpt',{exact:true}).fill('Account: EUR 200');
  await expect(consent).not.toBeChecked();
  pending=page.waitForResponse(r=>r.url().endsWith('/api/evidence-intake/drafts') && r.request().method()==='POST');
  await page.getByRole('button',{name:'Review suggestions',exact:true}).click();
  expect((await pending).request().postDataJSON().allowModel).toBe(false);
  await expect(page.getByRole('region',{name:'Review evidence suggestions'})).toBeVisible();
});
