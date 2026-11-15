import { test, expect } from "@playwright/test";
import { unlockFixture } from "./workspace-helper";
test.use({ extraHTTPHeaders: { "X-Workspace-Request": "1" } });
test.beforeEach(unlockFixture);
test("financial goal becomes a decision, observed follow-up and revised evidence with intact history", async ({
  page,
}, testInfo) => {
  test.setTimeout(120000);
  await page.goto("/");
  await page
    .getByRole("button", { name: "Compare scenarios", exact: true })
    .click();
  await expect(
    page.getByRole("button", { name: "Save comparison to financial plan" }),
  ).toBeVisible();
  await page
    .getByRole("button", { name: "Save comparison to financial plan" })
    .click();
  await expect(
    page.getByRole("heading", { name: "Financial plans", exact: true }),
  ).toBeVisible();
  await expect(
    page.getByLabel("Financial plan title", { exact: true }),
  ).toHaveValue("Debt repayment decision");
  await page
    .getByLabel("Financial plan title", { exact: true })
    .fill("Synthetic loan plan " + testInfo.project.name);
  await page
    .getByLabel("Evidence 1 summary", { exact: true })
    .fill("Synthetic statement and current repayment comparison");
  await page
    .getByRole("button", { name: "Save revised evidence and options" })
    .click();
  await expect(
    page.getByText("Saved privately in this workspace."),
  ).toBeVisible();
  await page.getByLabel("Chosen option", { exact: true }).selectOption("extra");
  await page
    .getByLabel("Why this choice?", { exact: true })
    .fill("Synthetic buffer is adequate after confirming lender terms");
  await page
    .getByLabel("Follow-up 1", { exact: true })
    .fill("Confirm synthetic lender terms");
  await page
    .getByLabel("I have reviewed the evidence, impacts and uncertainty")
    .check();
  await page
    .getByRole("button", { name: "Record decision", exact: true })
    .click();
  await expect(
    page.getByText("Decision recorded", { exact: true }),
  ).toBeVisible();
  await page
    .getByLabel("Observed outcome for Confirm synthetic lender terms", {
      exact: true,
    })
    .fill("Synthetic lender confirms no early repayment fee");
  await page.getByRole("button", { name: "Mark follow-up complete" }).click();
  await expect(
    page.getByText(
      /Completed .*Synthetic lender confirms no early repayment fee/,
    ),
  ).toBeVisible();
  await page.reload();
  await page
    .getByRole("button", { name: "Financial plans", exact: true })
    .click();
  await expect(
    page.getByLabel("Financial plan title", { exact: true }),
  ).toHaveValue("Synthetic loan plan " + testInfo.project.name);
  await expect(
    page.getByText(
      /Completed .*Synthetic lender confirms no early repayment fee/,
    ),
  ).toBeVisible();
  const tab = await page.context().newPage();
  await tab.goto("/");
  await tab
    .getByRole("button", { name: "Financial plans", exact: true })
    .click();
  await expect(
    tab.getByLabel("Financial plan title", { exact: true }),
  ).toHaveValue("Synthetic loan plan " + testInfo.project.name);
  await page
    .getByLabel("Evidence 1 summary", { exact: true })
    .fill("New synthetic statement: cash assumptions changed");
  await expect(
    page.getByText("Decision recorded", { exact: true }),
  ).toBeHidden();
  await page
    .getByRole("button", { name: "Save revised evidence and options" })
    .click();
  await expect(
    page.getByRole("heading", { name: "3. Record an explicit decision" }),
  ).toBeVisible();
  await expect(
    page.getByText("Decision recorded", { exact: true }),
  ).toBeHidden();
  // A second tab cannot overwrite the revision or erase decision history.
  await tab
    .getByLabel("Evidence 1 summary", { exact: true })
    .fill("Stale correction from another tab");
  await tab
    .getByRole("button", { name: "Save revised evidence and options" })
    .click();
  await expect(tab.getByRole("alert")).toContainText("Plan changed; reload");
  await tab.close();
  const completedHistory = page
    .locator(".plan-history-entry")
    .filter({ hasText: "ACTION COMPLETED" });
  await completedHistory.locator("summary").click();
  await expect(completedHistory).toContainText(
    "Synthetic lender confirms no early repayment fee",
  );
  await expect(
    completedHistory.getByText("Recorded choice:", { exact: false }),
  ).toBeVisible();
  await page.screenshot({
    path: testInfo.outputPath("financial-plan-history.png"),
    fullPage: true,
  });
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBeTruthy();
  // The normal, non-debt entry path works as well.
  await page
    .getByRole("button", { name: "New financial plan", exact: true })
    .click();
  await page
    .getByLabel("Financial plan title", { exact: true })
    .fill("Synthetic home insulation choice");
  await page
    .getByLabel("What are you trying to achieve?", { exact: true })
    .fill("Reduce heating costs with a checked quotation");
  await page
    .getByRole("button", { name: "Save proposal", exact: true })
    .click();
  await expect(
    page.getByText("Saved privately in this workspace."),
  ).toBeVisible();
  await expect(
    page.getByRole("heading", { name: "3. Record an explicit decision" }),
  ).toBeVisible();
});
