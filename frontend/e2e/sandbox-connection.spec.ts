import { openWorkspace } from './workspace-navigation';
import { test, expect } from "@playwright/test";
import { unlockFixture } from "./workspace-helper";
test.use({ extraHTTPHeaders: { "X-Workspace-Request": "1" } });
test.beforeEach(unlockFixture);
test("connect synthetic read-only accounts, review paginated activity and disconnect", async ({
  page,
}, testInfo) => {
  test.setTimeout(90000);
  for (const query of ["error=access_denied", "state=synthetic-invalid&code=synthetic-code"]) {
    await page.goto("/bank/callback?" + query);
    const recovery = page.getByRole("region", { name: "Enable Banking sandbox connection", exact: true });
    await expect(recovery.getByRole("alert")).toBeVisible();
    await expect(recovery.getByRole("checkbox", { name: "I will use only synthetic sandbox accounts and allow read-only account, balance and transaction access" })).toBeVisible();
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
  const prior = existing.accounts.find((a: { name: string }) => a.name === "Synthetic bank desktop");
  let local = prior;
  if (!local) {
    const created = await page.request.post("/api/ledger/accounts", { data: {
      name: "Synthetic bank desktop", kind: "CASH", openingDate: date,
      openingEuro: "1000", source: "Synthetic booked statement",
    }});
    expect(created.ok()).toBeTruthy(); local = await created.json();
  }
  await page.reload();
  await openWorkspace(page, "Money position");
  const panel = page.getByRole("region", {
    name: "Enable Banking sandbox connection",
    exact: true,
  });
  const consent = panel.getByLabel(
    "I will use only synthetic sandbox accounts and allow read-only account, balance and transaction access",
  );
  await consent.check();
  await panel
    .getByRole("button", {
      name: "Check sandbox application and load mock banks",
    })
    .click();
  await panel
    .getByRole("button", {
      name: "Connect to Enable Banking sandbox",
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
    .getByRole("button", { name: "Fetch sandbox activity for review" })
    .click();
  await expect(
    page.getByRole("region", { name: "Review imported activity" }),
  ).toBeVisible();
  await expect(
    page.getByRole("region", { name: "Account balance evidence" }),
  ).toContainText(prior ? "€990.00" : "€1,000.00");
  await expect(panel.getByLabel("Sandbox balance evidence")).toContainText(
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
    .getByRole("button", { name: "Disconnect and revoke sandbox session" })
    .click();
  await expect(panel).toContainText("DISCONNECTED");
  await expect(
    page.getByRole("region", { name: "Account balance evidence" }),
  ).toContainText("€990.00");
  await page.screenshot({
    path: testInfo.outputPath("sandbox-reviewed-position.png"),
    fullPage: true,
  });
});
