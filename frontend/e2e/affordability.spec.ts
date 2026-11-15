import { test, expect } from "@playwright/test";
import { unlockFixture } from "./workspace-helper";
function after(date: string, days: number) {
  const d = new Date(date + "T12:00:00Z");
  d.setUTCDate(d.getUTCDate() + days);
  return d.toISOString().slice(0, 10);
}
test.use({ extraHTTPHeaders: { "X-Workspace-Request": "1" } });
test.beforeEach(unlockFixture);
test("dated affordability, delayed payday, corrected inputs and stale evidence stay explainable", async ({
  page,
}, testInfo) => {
  test.setTimeout(120000);
  await page.goto("/");
  await page.getByRole("button", { name: "Plan ahead", exact: true }).click();
  await expect(page.getByText("Loading dated plan…")).toBeHidden();
  const original = await (await page.request.get("/api/forecast")).json();
  const today = original.today;
  if (original.plan)
    await page
      .getByLabel(
        "Replace starting evidence after reviewing actual receipts, payments and pending debits",
      )
      .check();
  await page
    .getByLabel("Starting evidence type", { exact: true })
    .selectOption("MANUAL");
  await page
    .getByLabel("Starting evidence date (closing balance)", { exact: true })
    .fill(today);
  await page
    .getByLabel("Starting cash (€; overdrafts negative)", { exact: true })
    .fill("2000");
  await page
    .getByLabel("Pending cash debits already reserved (€)", { exact: true })
    .fill("100");
  await page
    .getByLabel("Starting evidence source", { exact: true })
    .fill("Synthetic statement estimate");
  await page
    .getByLabel("Plan name", { exact: true })
    .fill("Synthetic forecast " + testInfo.project.name);
  await page.getByLabel("Plan through", { exact: true }).fill(after(today, 10));
  await page.getByLabel("Protected reserve (€)", { exact: true }).fill("200");
  while (
    await page
      .getByRole("button", { name: "Remove expectation 1", exact: true })
      .count()
  )
    await page
      .getByRole("button", { name: "Remove expectation 1", exact: true })
      .click();
  const events = [
    ["Rent", 1, "EXPENSE", "EXPECTED", "1500"],
    ["Salary", 3, "INCOME", "EXPECTED", "3000"],
    ["Groceries", 5, "EXPENSE", "EXPECTED", "200"],
    ["Utility", 6, "EXPENSE", "EXPECTED", "1000"],
    ["Possible refund", 2, "INCOME", "UNCERTAIN", "1000"],
  ] as const;
  for (let n = 0; n < events.length; n++) {
    const [label, days, direction, certainty, amount] = events[n];
    const i = n + 1;
    await page
      .getByRole("button", { name: "Add dated expectation", exact: true })
      .click();
    await page
      .getByLabel(`Expectation ${i} label`, { exact: true })
      .fill(label);
    await page
      .getByLabel(`Expectation ${i} date`, { exact: true })
      .fill(after(today, days));
    await page
      .getByLabel(`Expectation ${i} direction`, { exact: true })
      .selectOption(direction);
    if (direction === "INCOME")
      await page
        .getByLabel(`Expectation ${i} certainty`, { exact: true })
        .selectOption(certainty);
    await page
      .getByLabel(`Expectation ${i} amount`, { exact: true })
      .fill(amount);
    await page
      .getByLabel(`Expectation ${i} source`, { exact: true })
      .fill("Synthetic " + label + " evidence");
  }
  const reviewed = page.getByLabel(
    "I reviewed starting evidence and excluded bills already paid, deducted or represented by pending debits",
  );
  await reviewed.check();
  await page
    .getByRole("button", { name: "Save dated plan", exact: true })
    .click();
  const saved = page.getByRole("region", {
    name: "Saved expected projection",
    exact: true,
  });
  await expect(saved.getByText("€200.00", { exact: true })).toBeVisible();
  await expect(
    page.getByRole("region", { name: "Forecast evidence status" }),
  ).toContainText("ESTIMATED");
  await page
    .getByLabel("Proposed purchase (€; 0 for none)", { exact: true })
    .fill("500");
  await page.getByLabel("Purchase date", { exact: true }).fill(after(today, 2));
  await page
    .getByLabel("Delay expected income", { exact: true })
    .selectOption({ label: "Salary · " + after(today, 3) });
  await page
    .getByLabel("Delayed receipt date", { exact: true })
    .fill(after(today, 8));
  await page
    .getByRole("button", { name: "Compare what-if", exact: true })
    .click();
  const scenario = page.getByRole("region", {
    name: "What-if projection",
    exact: true,
  });
  await expect(scenario).toContainText("-€1,300.00");
  await expect(scenario).toContainText(after(today, 2));
  await page.getByLabel("Expectation 1 amount", { exact: true }).fill("1000");
  await page
    .getByLabel("Expectation 1 source", { exact: true })
    .fill("Corrected synthetic rent invoice");
  await expect(scenario).toBeHidden();
  await expect(saved).toBeHidden();
  await reviewed.check();
  await page
    .getByRole("button", { name: "Save dated plan", exact: true })
    .click();
  await expect(saved.getByText("€700.00", { exact: true })).toBeVisible();
  await page.reload();
  await page.getByRole("button", { name: "Plan ahead", exact: true }).click();
  await expect(
    page.getByLabel("Expectation 1 amount", { exact: true }),
  ).toHaveValue("1000.00");
  await page
    .getByText("Daily projection and source expectations", { exact: true })
    .click();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBeTruthy();
  await page.screenshot({
    path: testInfo.outputPath("dated-affordability.png"),
    fullPage: true,
  });
  await page
    .getByLabel(
      "Replace starting evidence after reviewing actual receipts, payments and pending debits",
    )
    .check();
  await page
    .getByLabel("Starting evidence date (closing balance)", { exact: true })
    .fill(after(today, -8));
  await reviewed.check();
  await page
    .getByRole("button", { name: "Save dated plan", exact: true })
    .click();
  await expect(
    page.getByRole("region", { name: "Forecast evidence status" }),
  ).toContainText("STALE");
  await expect(
    page.getByText(
      "Illustrative projection — refresh/review starting evidence before a current spending decision.",
      { exact: true },
    ),
  ).toBeVisible();
});
