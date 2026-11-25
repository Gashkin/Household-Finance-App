import { openWorkspace } from './workspace-navigation';
import { test, expect } from "@playwright/test";
import { unlockFixture } from "./workspace-helper";
test.use({ extraHTTPHeaders: { "X-Workspace-Request": "1" } });
test.use({ baseURL: "http://127.0.0.1:8082" });
test.beforeEach(async ({page,request}) => {
  const response=await request.post('/api/workspace/unlock',{headers:{'X-Workspace-Request':'1'},data:{password:'synthetic-browser-workspace-password'}});
  expect(response.status()).toBe(204);
  const {cookies}=await request.storageState(); await page.context().addCookies(cookies);
});
test("packaged single-origin production protocol simulation, review and revoke", async ({
  page,
}, testInfo) => {
  test.setTimeout(90000);
  for (const query of ["error=access_denied", "state=synthetic-invalid&code=synthetic-code"]) {
    await page.goto("/bank/callback?" + query);
    const recovery = page.getByRole("region", { name: "AIB bank connection", exact: true });
    await expect(recovery.getByRole("alert")).toBeVisible();
    await expect(recovery.getByRole("checkbox", { name: "I authorise Enable Banking to access only my selected AIB accounts, balances and transactions, and retain reviewed records locally" })).toBeVisible();
    expect(new URL(page.url()).search).toBe("");
  }
  await page.goto("/");
  await openWorkspace(page, "Money position");
  await expect(page.getByText("Loading reviewed position…")).toBeHidden();
  const date = await page
    .getByLabel("Position as at", { exact: true })
    .inputValue();
  const existing = await (await page.request.get("/api/ledger?asOf=" + date)).json();
  // Desktop/mobile reconnect the same synthetic provider account into the same ledger.
  const prior = existing.accounts.find((a: { name: string }) => a.name === "Synthetic production bank desktop");
  let local = prior;
  if (!local) {
    const created = await page.request.post("/api/ledger/accounts", { data: {
      name: "Synthetic production bank desktop", kind: "CASH", openingDate: date,
      openingEuro: "1000", source: "Synthetic booked statement",
    }});
    expect(created.ok()).toBeTruthy(); local = await created.json();
  }
  await page.reload();
  await openWorkspace(page, "Money position");
  const panel = page.getByRole("region", {
    name: "AIB bank connection",
    exact: true,
  });
  const consent = panel.getByLabel(
    "I authorise Enable Banking to access only my selected AIB accounts, balances and transactions, and retain reviewed records locally",
  );
  await consent.check();
  await panel
    .getByRole("button", {
      name: "Check AIB production application",
    })
    .click();
  await panel
    .getByRole("button", {
      name: "Connect my AIB accounts",
      exact: true,
    })
    .click();
  await expect(
    page.getByRole("heading", { name: "Synthetic bank consent" }),
  ).toBeVisible();
  await page
    .getByRole("link", { name: "Authorise synthetic read-only accounts" })
    .click();
  await expect(panel.getByText("CONNECTED", { exact: true })).toBeVisible();
  expect(new URL(page.url()).search).toBe("");
  expect(
    await page.evaluate(
      () =>
        Object.keys(localStorage).length + Object.keys(sessionStorage).length,
    ),
  ).toBe(0);
  await page
    .getByLabel("Review account", { exact: true })
    .selectOption(local.id);
  await consent.check();
  await panel
    .getByRole("button", { name: "Fetch bank activity for review" })
    .click();
  await expect(
    page.getByRole("region", { name: "Review imported activity" }),
  ).toBeVisible();
  await expect(
    page.getByRole("region", { name: "Account balance evidence" }),
  ).toContainText(prior ? "€990.00" : "€1,000.00");
  await expect(panel.getByLabel("Bank balance evidence")).toContainText(
    "990.00",
  );
  if (prior) {
    await expect(page.getByRole("region", { name: "Review imported activity" })).toContainText("DUPLICATE");
    await expect(page.getByRole("button", { name: "Approve selected activity", exact: true })).toBeDisabled();
  } else {
    await page.getByLabel("I reviewed these facts and resolved duplicates or overlaps with existing manual activity", { exact: true }).check();
    await page.getByRole("button", { name: "Approve selected activity", exact: true }).click();
  }
  await expect(
    page.getByRole("region", { name: "Account balance evidence" }),
  ).toContainText("€990.00");
  await page.getByLabel("Statement closing balance (€)", { exact: true }).fill("990");
  await page.getByLabel("Statement evidence source", { exact: true }).fill("Synthetic provider CLBD statement");
  await page.getByRole("button", { name: "Check statement balance", exact: true }).click();
  await expect(page.getByRole("region", { name: "Statement difference" })).toContainText("€0.00");
  await page.getByRole("button", { name: "Confirm reconciliation", exact: true }).click();
  await expect(page.getByRole("region", { name: "Account balance evidence" })).toContainText("Reconciled for this date and activity.");
  await panel
    .getByRole("button", { name: "Disconnect and revoke bank session" })
    .click();
  await expect(panel).toContainText("DISCONNECTED");
  await expect(
    page.getByRole("region", { name: "Account balance evidence" }),
  ).toContainText("€990.00");
  await page.screenshot({
    path: testInfo.outputPath("production-synthetic-position.png"),
    fullPage: true,
  });
});

test("pending revocation cannot be forgotten without separately confirming the external action", async ({page}) => {
  const state={configured:true,environment:"PRODUCTION",state:"REVOCATION_PENDING",expiresAt:null,lastSuccessfulFetch:null,lastError:"",accounts:[],callback:"http://127.0.0.1:8082/bank/callback"};
  await page.route("**/api/banking/status",r=>r.fulfill({json:state}));
  let confirmations=0;
  await page.route("**/api/banking/external-revocation",async r=>{
    expect(r.request().postDataJSON()).toEqual({acknowledged:true}); confirmations++;
    await r.fulfill({json:{...state,state:"DISCONNECTED"}});
  });
  await page.goto("/");
  await openWorkspace(page, "Money position");
  const panel=page.getByRole("region",{name:"AIB bank connection",exact:true});
  await expect(panel).toContainText("REVOCATION_PENDING");
  await panel.getByLabel("I understand local forgetting does not revoke provider consent or erase reviewed ledger records").check();
  await expect(panel.getByRole("button",{name:"Forget local bank connection only",exact:true})).toBeDisabled();
  const confirm=panel.getByRole("button",{name:"Confirm completed external revocation and clear connection",exact:true});
  await expect(confirm).toBeDisabled();
  await panel.getByLabel("I have personally revoked this application's access at the bank or provider").check();
  await confirm.click();
  await expect(panel).toContainText("DISCONNECTED");
  expect(confirmations).toBe(1);
});

test("empty bank refresh keeps balance evidence and clears an older activity review", async ({ page }, testInfo) => {
  const state = {
    configured: true, environment: "PRODUCTION", state: "CONNECTED",
    expiresAt: new Date(Date.now() + 3600000).toISOString(), lastSuccessfulFetch: null,
    lastError: "", callback: "http://127.0.0.1:8082/bank/callback",
    accounts: [{ handle: "synthetic-empty-handle", label: "Synthetic empty-period account", currency: "EUR", kind: "CASH" }],
  };
  await page.route("**/api/banking/status", route => route.fulfill({ json: state }));
  await page.goto("/");
  await openWorkspace(page, "Money position");
  const date = await page.getByLabel("Position as at", { exact: true }).inputValue();
  const created = await page.request.post("/api/ledger/accounts", { data: {
    name: "Synthetic empty period " + testInfo.project.name, kind: "CASH",
    openingDate: date, openingEuro: "1000", source: "Synthetic opening statement",
  }});
  expect(created.ok()).toBeTruthy();
  const account = await created.json();
  let refreshes = 0;
  let approvals = 0;
  await page.route("**/api/ledger/accounts/*/previews/*/approve", async route => {
    approvals++;
    await route.abort();
  });
  await page.route("**/api/banking/preview", async route => {
    refreshes++;
    await route.fulfill({ json: {
      preview: refreshes === 1 ? {
        id: "synthetic-earlier-preview", revision: account.revision,
        expiresAt: new Date(Date.now() + 300000).toISOString(),
        proposals: [{ disposition: "NEW", possibleOverlap: false, row: {
          reference: "synthetic-unapproved", date, description: "Synthetic earlier activity",
          amountEuro: "-10.00", status: "BOOKED", kind: "ACTIVITY",
        }}],
      } : null,
      balances: refreshes < 3 ? [{ type: "CLBD", amountEuro: "990.00", date }] : [],
      warnings: [], fetchedAt: new Date().toISOString(),
    }});
  });
  await page.reload();
  await openWorkspace(page, "Money position");
  await page.getByLabel("Review account", { exact: true }).selectOption(account.id);
  const panel = page.getByRole("region", { name: "AIB bank connection", exact: true });
  await panel.getByLabel("I authorise Enable Banking to access only my selected AIB accounts, balances and transactions, and retain reviewed records locally").check();
  const fetch = panel.getByRole("button", { name: "Fetch bank activity for review", exact: true });
  await fetch.click();
  await expect(page.getByRole("region", { name: "Review imported activity" })).toBeVisible();
  await fetch.click();
  const evidence = panel.getByLabel("Bank balance evidence", { exact: true });
  await expect(evidence).toContainText("990.00");
  await expect(evidence).toContainText("No transactions were returned for this period");
  await expect(page.getByRole("region", { name: "Review imported activity" })).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Approve selected activity", exact: true })).toHaveCount(0);
  await fetch.click();
  await expect(evidence).toContainText("No supported booked balance was returned.");
  expect(approvals).toBe(0);
  const position = await (await page.request.get("/api/ledger?asOf=" + date)).json();
  const unchanged = position.accounts.find((value: { id: string }) => value.id === account.id);
  expect(unchanged.bookedMinor).toBe(100000);
  expect(unchanged.entries).toBe(0);
  expect(unchanged.revision).toBe(account.revision);
});
