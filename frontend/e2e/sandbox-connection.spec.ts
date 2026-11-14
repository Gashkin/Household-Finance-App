import { test, expect } from "@playwright/test";
import { unlockFixture } from "./workspace-helper";
test.use({ extraHTTPHeaders: { "X-Workspace-Request": "1" } });
test.beforeEach(unlockFixture);
test("connect synthetic read-only accounts, review paginated activity and disconnect", async ({
  page,
}, testInfo) => {
  test.setTimeout(90000);
  await page.goto("/");
  await page
    .getByRole("button", { name: "Money position", exact: true })
    .click();
  await expect(page.getByText("Loading reviewed position…")).toBeHidden();
  const date = await page
    .getByLabel("Position as at", { exact: true })
    .inputValue();
  const created = await page.request.post("/api/ledger/accounts", {
    data: {
      name: "Synthetic bank " + testInfo.project.name,
      kind: "CASH",
      openingDate: date,
      openingEuro: "1000",
      source: "Synthetic booked statement",
    },
  });
  expect(created.ok()).toBeTruthy();
  const local = await created.json();
  await page.reload();
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
  await expect(panel).toContainText("CONNECTED");
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
  ).toContainText("€1,000.00");
  await expect(panel.getByLabel("Sandbox balance evidence")).toContainText(
    "990.00",
  );
  await page
    .getByLabel(
      "I reviewed these facts and resolved duplicates or overlaps with existing manual activity",
      { exact: true },
    )
    .check();
  await page
    .getByRole("button", { name: "Approve selected activity", exact: true })
    .click();
  await expect(
    page.getByRole("region", { name: "Account balance evidence" }),
  ).toContainText("€990.00");
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
