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
  const errors: string[] = [];
  page.on("pageerror", (e) => errors.push(e.message));
  const baselineDate = page.getByLabel(
    "Starting evidence date (closing balance)",
    { exact: true },
  );
  await baselineDate.fill("");
  await expect(page.getByLabel("Plan name", { exact: true })).toHaveValue(
    "Synthetic forecast " + testInfo.project.name,
  );
  await expect(
    page.getByRole("heading", { name: "Plan ahead", exact: true }),
  ).toBeVisible();
  expect(errors).toEqual([]);
  await baselineDate.fill(today);
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

test("a second-tab ledger change withdraws current evidence after comparing", async ({
  page,
  context,
}, testInfo) => {
  test.setTimeout(90000);
  const current = await (await page.request.get("/api/forecast")).json();
  const today = current.today;
  const response = await page.request.post("/api/ledger/accounts", {
    data: {
      name: "Synthetic forecast source " + testInfo.project.name,
      kind: "CASH",
      openingDate: today,
      openingEuro: "1000",
      source: "Synthetic source opening",
    },
  });
  expect(response.ok()).toBeTruthy();
  const account = await response.json();
  expect(
    (
      await page.request.post(
        `/api/ledger/accounts/${account.id}/reconcile?asOf=${today}`,
        {
          data: {
            revision: account.revision,
            statementEuro: "1000",
            source: "Synthetic source closing",
            commit: true,
            acceptAdjustment: false,
          },
        },
      )
    ).ok(),
  ).toBeTruthy();
  const saved = await page.request.post("/api/forecast", {
    data: {
      revision: current.plan?.revision || 0,
      name: "Synthetic live source guard",
      baseline: {
        mode: "LEDGER",
        date: today,
        cashEuro: null,
        pendingEuro: null,
        source: null,
      },
      endDate: after(today, 5),
      reserveEuro: "100",
      events: [],
      acknowledged: true,
    },
  });
  expect(saved.ok()).toBeTruthy();
  await page.goto("/");
  await page.getByRole("button", { name: "Plan ahead", exact: true }).click();
  const claim =
    "Starting evidence is current and reconciled. Future income is still only projected.";
  await expect(page.getByText(claim, { exact: true })).toBeVisible();
  const other = await context.newPage();
  await other.goto("/");
  await other
    .getByRole("button", { name: "Money position", exact: true })
    .click();
  await other
    .getByLabel("Review account", { exact: true })
    .selectOption(account.id);
  await other
    .getByLabel("Activity description", { exact: true })
    .fill("Late synthetic card debit");
  await other
    .getByLabel("Signed activity amount (€)", { exact: true })
    .fill("-10");
  await other
    .getByLabel("Activity source or correction reason", { exact: true })
    .fill("Second-tab synthetic receipt");
  await other
    .getByLabel("I checked the source and existing activity for duplication", {
      exact: true,
    })
    .check();
  await other
    .getByRole("button", { name: "Save reviewed activity", exact: true })
    .click();
  await expect(
    other.getByRole("region", { name: "Account balance evidence" }),
  ).toContainText("€990.00");
  await page.bringToFront();
  await page
    .getByLabel("Proposed purchase (€; 0 for none)", { exact: true })
    .fill("1");
  await page
    .getByRole("button", { name: "Compare what-if", exact: true })
    .click();
  await expect(
    page.getByRole("region", { name: "Forecast evidence status" }),
  ).toContainText("SOURCE CHANGED");
  await expect(page.getByText(claim, { exact: true })).toBeHidden();
  await expect(
    page.getByText(
      "Illustrative projection — refresh/review starting evidence before a current spending decision.",
      { exact: true },
    ),
  ).toBeVisible();
  const actual = await (
    await other.request.get("/api/ledger?asOf=" + today)
  ).json();
  const changed = actual.accounts.find(
    (a: { id: string }) => a.id === account.id,
  );
  expect(
    (
      await other.request.post(
        `/api/ledger/accounts/${account.id}/reconcile?asOf=${today}`,
        {
          data: {
            revision: changed.revision,
            statementEuro: "990",
            source: "Synthetic corrected statement",
            commit: true,
            acceptAdjustment: false,
          },
        },
      )
    ).ok(),
  ).toBeTruthy();
  await page.screenshot({
    path: testInfo.outputPath("changed-forecast-source.png"),
    fullPage: true,
  });
  const amounts = page.locator(".forecast-totals strong");
  await expect(amounts).toHaveCount(8);
  expect(
    await amounts.evaluateAll((elements) =>
      elements.every((element) => {
        const range = document.createRange();
        range.selectNodeContents(element);
        return (
          range.getClientRects().length === 1 &&
          element.getBoundingClientRect().width <=
            element.parentElement!.getBoundingClientRect().width
        );
      }),
    ),
  ).toBe(true);
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBe(true);
  await other.close();
});
