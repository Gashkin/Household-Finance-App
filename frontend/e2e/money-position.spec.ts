import { test, expect } from "@playwright/test";
import { readFile } from "node:fs/promises";
import { unlockFixture } from "./workspace-helper";

test.use({ extraHTTPHeaders: { "X-Workspace-Request": "1" } });
test.beforeEach(unlockFixture);
test("review activity, correct facts, reconcile an explicit difference and reuse a pending-safe cash position", async ({
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
  const original = await (
    await page.request.get("/api/ledger?asOf=" + date)
  ).json();
  if (!(await page.getByLabel("Account name", { exact: true }).isVisible()))
    await page.getByText("Add account evidence", { exact: true }).click();
  const name = "Synthetic current " + testInfo.project.name;
  await page.getByLabel("Account name", { exact: true }).fill(name);
  await page
    .getByLabel("Opening balance (€; debt owed is negative)", { exact: true })
    .fill("1000");
  await page
    .getByLabel("Opening evidence source", { exact: true })
    .fill("Synthetic opening statement");
  await page
    .getByRole("button", { name: "Create ledger account", exact: true })
    .click();
  const evidence = page.getByRole("region", {
    name: "Account balance evidence",
  });
  await expect(evidence).toContainText("€1,000.00");
  await page
    .getByText("Preview and review CSV activity", { exact: true })
    .click();
  let csv =
    "reference,date,description,amount,status,kind\n" +
    `coffee-${testInfo.project.name},${date},Coffee,-20,BOOKED,ACTIVITY\ntransfer-${testInfo.project.name},${date},Own account transfer,-100,BOOKED,TRANSFER\npending-${testInfo.project.name},${date},Pending card authorisation,-50,PENDING,ACTIVITY\n`;
  await page.getByLabel("Activity CSV", { exact: true }).fill(csv);
  await page
    .getByLabel("CSV source reference", { exact: true })
    .fill("Synthetic account export");
  await page.getByRole("button", { name: "Preview CSV", exact: true }).click();
  const review = page.getByRole("region", { name: "Review imported activity" });
  await expect(review).toBeVisible();
  await expect(evidence).toContainText("€1,000.00");
  await page.getByLabel("Import row 1 amountEuro", { exact: true }).fill("-25");
  await page
    .getByLabel(
      "I reviewed these facts and resolved duplicates or overlaps with existing manual activity",
      { exact: true },
    )
    .check();
  const approval = page.waitForResponse(
    (r) => r.url().includes("/previews/") && r.url().endsWith("/approve"),
  );
  await page
    .getByRole("button", { name: "Approve selected activity", exact: true })
    .click();
  const response = await approval;
  expect(response.ok()).toBeTruthy();
  const retry = await page.request.post(response.url(), {
    data: response.request().postDataJSON(),
  });
  expect(retry.ok()).toBeTruthy();
  expect(await retry.json()).toEqual(await response.json());
  await expect(evidence).toContainText("€875.00");
  // Stable account-scoped identities make the same reviewed feed duplicates on re-import.
  csv = csv.replace("Coffee,-20", "Coffee,-25");
  await page.getByLabel("Activity CSV", { exact: true }).fill(csv);
  await page.getByRole("button", { name: "Preview CSV", exact: true }).click();
  await expect(review).toContainText("DUPLICATE");
  await expect(
    page.getByRole("button", {
      name: "Approve selected activity",
      exact: true,
    }),
  ).toBeDisabled();
  await page
    .getByRole("button", {
      name: "Correct coffee-" + testInfo.project.name,
      exact: true,
    })
    .click();
  await page
    .getByLabel("Signed activity amount (€)", { exact: true })
    .fill("-30");
  await page
    .getByLabel("Activity source or correction reason", { exact: true })
    .fill("Synthetic corrected receipt");
  await page
    .getByLabel("I checked the source and existing activity for duplication", {
      exact: true,
    })
    .check();
  await page
    .getByRole("button", { name: "Save correction", exact: true })
    .click();
  await expect(evidence).toContainText("€870.00");
  await page
    .getByLabel("Statement closing balance (€)", { exact: true })
    .fill("865");
  await page
    .getByLabel("Statement evidence source", { exact: true })
    .fill("Synthetic closing statement");
  await page
    .getByRole("button", { name: "Check statement balance", exact: true })
    .click();
  const difference = page.getByRole("region", { name: "Statement difference" });
  await expect(difference).toContainText("-€5.00");
  await expect(
    page.getByRole("button", { name: "Confirm reconciliation", exact: true }),
  ).toBeDisabled();
  await page
    .getByLabel(
      "Record this difference as an explicit adjustment with the statement source",
      { exact: true },
    )
    .check();
  await page
    .getByRole("button", { name: "Confirm reconciliation", exact: true })
    .click();
  await expect(evidence).toContainText("€865.00");
  await expect(evidence).toContainText(
    "Reconciled for this date and activity.",
  );
  await page
    .getByLabel("Ledger protected reserve (€)", { exact: true })
    .fill("100");
  await page
    .getByRole("button", { name: "Add unpaid commitment", exact: true })
    .click();
  await page
    .getByLabel("Unpaid commitment 1", { exact: true })
    .fill("Unpaid utility");
  await page.getByLabel("Commitment 1 amount (€)", { exact: true }).fill("50");
  const publicationConsent = page.getByLabel(
      "I checked reconciled cash and unpaid commitments against pending debits to avoid counting payments twice",
      { exact: true },
    );
  await publicationConsent.locator('..').click();
  await expect(publicationConsent).toBeChecked();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBeTruthy();
  await page.screenshot({
    path: testInfo.outputPath("reviewed-money-position.png"),
    fullPage: true,
  });
  const publication = page.waitForResponse((r) =>
    r.url().endsWith("/api/ledger/publish"),
  );
  await page
    .getByRole("button", {
      name: "Save position and open debt decision",
      exact: true,
    })
    .click();
  const snapshot = await (await publication).json();
  expect(snapshot.liquidCashMinor).toBe(original.cashMinor + 86500);
  expect(snapshot.committedMinor).toBe(original.cashPendingDebitMinor + 10000);
  await page
    .getByRole("button", {
      name: "Use this cash position in debt comparison",
      exact: true,
    })
    .click();
  await expect(
    page.getByLabel("Available cash (€)", { exact: true }),
  ).toHaveValue(
    ((snapshot.liquidCashMinor - snapshot.committedMinor) / 100).toFixed(2),
  );
  await page.getByLabel("Extra repayment now (€)", { exact: true }).fill("100");
  await page
    .getByRole("button", { name: "Compare scenarios", exact: true })
    .click();
  const download = page.waitForEvent("download");
  await page
    .getByRole("button", { name: "Download JSON report", exact: true })
    .click();
  const file = await download;
  const report = JSON.parse(await readFile((await file.path())!, "utf8"));
  expect(report.cashEvidence.id).toBe(snapshot.id);
  expect(report.cashEvidence.sourceNote).toContain("Reviewed ledger");
  expect(report.input.liquidCashMinor).toBe(
    snapshot.availableAfterCommitmentsMinor,
  );
});
