import { openWorkspace } from './workspace-navigation';
import { test, expect, type Page } from "@playwright/test";
import { unlockFixture } from "./workspace-helper";
test.use({ extraHTTPHeaders: { "X-Workspace-Request": "1" } });
test.beforeEach(unlockFixture);
test.afterEach(async ({ page }) => {
  await page.request.post("/api/workspace/lock", {
    headers: { "X-Workspace-Request": "1" },
  });
});
const today = () =>
  new Date().toLocaleDateString("en-CA", { timeZone: "Europe/Dublin" });
function offset(days: number) {
  const d = new Date(today() + "T12:00:00Z");
  d.setUTCDate(d.getUTCDate() + days);
  return d.toISOString().slice(0, 10);
}
async function account(
  page: Page,
  name: string,
  kind: string,
  openingEuro: string,
) {
  const r = await page.request.post("/api/ledger/accounts", {
    data: {
      name,
      kind,
      openingDate: offset(-60),
      openingEuro,
      source: "Synthetic lender test statement",
    },
  });
  expect(r.ok()).toBeTruthy();
  return r.json();
}
async function state(page: Page) {
  const r = await page.request.get("/api/ledger/debts");
  expect(r.ok()).toBeTruthy();
  return r.json();
}
async function settleAndReconcile(page: Page, cash: string, debt: string) {
  async function view(id: string) {
    const r = await page.request.get("/api/ledger?asOf=" + today());
    return (await r.json()).accounts.find((a: { id: string }) => a.id === id);
  }
  const amount = -(await view(debt)).bookedMinor;
  if (amount > 0) {
    for (const [id, euro] of [
      [cash, (-amount / 100).toFixed(2)],
      [debt, (amount / 100).toFixed(2)],
    ]) {
      expect(
        (
          await page.request.post("/api/ledger/accounts/" + id + "/activity", {
            data: {
              revision: (await view(id)).revision,
              row: {
                reference: crypto.randomUUID(),
                date: today(),
                description: "Synthetic debt settlement",
                amountEuro: euro,
                status: "BOOKED",
                kind: "ACTIVITY",
              },
              source: "Synthetic reviewed settlement",
              acknowledgedOverlap: true,
            },
          })
        ).ok(),
      ).toBeTruthy();
    }
    const a = (
        await (
          await page.request.get("/api/ledger/accounts/" + cash + "/activity")
        ).json()
      ).find(
        (e: { description: string }) =>
          e.description === "Synthetic debt settlement",
      ),
      b = (
        await (
          await page.request.get("/api/ledger/accounts/" + debt + "/activity")
        ).json()
      ).find(
        (e: { description: string }) =>
          e.description === "Synthetic debt settlement",
      );
    expect(
      (
        await page.request.post(
          "/api/ledger/review/activity/" + a.id + "/link",
          {
            data: {
              revision: (await view(cash)).revision,
              otherId: b.id,
              otherRevision: (await view(debt)).revision,
              type: "CARD_SETTLEMENT",
              reason: "Synthetic complete settlement",
            },
          },
        )
      ).ok(),
    ).toBeTruthy();
  }
  for (const id of [cash, debt]) {
    const a = await view(id);
    expect(
      (
        await page.request.post(
          "/api/ledger/accounts/" + id + "/reconcile?asOf=" + today(),
          {
            data: {
              revision: a.revision,
              statementEuro: (a.bookedMinor / 100).toFixed(2),
              source: "Synthetic current closing statement",
              commit: true,
              acceptAdjustment: false,
            },
          },
        )
      ).ok(),
    ).toBeTruthy();
  }
}
test("statement corrections preserve drafts, recover unknown saves and export lender evidence", async ({
  page,
}, info) => {
  const suffix = crypto.randomUUID().slice(0, 8),
    name = "Synthetic card " + suffix,
    cash = await account(
      page,
      "Synthetic repayment " + suffix,
      "CASH",
      "2000.00",
    ),
    debt = await account(
      page,
      "Synthetic card ledger " + suffix,
      "DEBT",
      "-1000.00",
    );
  await page.goto("/");
  await openWorkspace(page, "Cards, loans & mortgages");
  await expect(
    page.getByRole("heading", { name: "Cards, loans & mortgages" }),
  ).toBeVisible();
  await page
    .getByRole("button", { name: "Add debt statement", exact: true })
    .click();
  await page.getByLabel("Debt name", { exact: true }).fill(name);
  await page
    .getByRole("combobox", { name: "Debt account", exact: true })
    .selectOption(debt.id);
  await page
    .getByRole("combobox", { name: "Repayment cash account", exact: true })
    .selectOption(cash.id);
  await page.getByLabel("Credit limit EUR", { exact: true }).fill("5000");
  await page
    .getByLabel("Contractual maturity / card planning end", { exact: true })
    .fill(offset(365));
  await page
    .getByLabel("Lender rules and assumptions", { exact: true })
    .fill(
      "Synthetic statement balance paid monthly; lender daily accrual is not modelled",
    );
  await page
    .getByLabel("Rate 1 effective date", { exact: true })
    .fill(offset(-60));
  await page.getByLabel("Rate 1 annual percent", { exact: true }).fill("18.5");
  await page.getByLabel("Cycle start", { exact: true }).fill(offset(-30));
  await page.getByLabel("Cycle end", { exact: true }).fill(offset(-1));
  await page.getByLabel("Payment due", { exact: true }).fill(offset(10));
  await page
    .getByLabel("Closing amount owed EUR", { exact: true })
    .fill("1000.00");
  await page
    .getByLabel("Statement source", { exact: true })
    .fill("Synthetic incomplete statement");
  await page
    .getByLabel("Reason for debt change", { exact: true })
    .fill("Synthetic evidence intake");
  await page.route("**/api/ledger/debts", async (route) => {
    if (route.request().method() === "POST") {
      await route.fetch();
      await route.abort("failed");
    } else await route.continue();
  });
  await page
    .getByRole("button", { name: "Save debt evidence", exact: true })
    .click();
  await expect(page.getByRole("alert")).toContainText(
    "Request status is unknown",
  );
  await page.unroute("**/api/ledger/debts");
  await page
    .getByRole("button", { name: "Check debt save status", exact: true })
    .click();
  await expect(page.getByRole("status")).toContainText(
    "Saved debt operation confirmed",
  );
  const first = await state(page),
    saved = first.state.debts.find(
      (d: { terms: { name: string } }) => d.terms.name === name,
    );
  expect(saved.statements[0].complete).toBe(false);
  await page
    .getByRole("button", { name: "Correct statement for " + name, exact: true })
    .click();
  for (const [label, value] of [
    ["Opening amount owed EUR", "1000.00"],
    ["Purchases / drawdown EUR", "0.00"],
    ["Statement interest EUR", "0.00"],
    ["Statement fees EUR", "0.00"],
    ["Statement repayments EUR", "0.00"],
    ["Minimum payment EUR", "25.00"],
  ])
    await page.getByLabel(label, { exact: true }).fill(value);
  await page
    .getByLabel("All statement components are complete", { exact: true })
    .check();
  await page
    .getByLabel("Reason for debt change", { exact: true })
    .fill("Synthetic complete statement correction");
  await page
    .getByRole("button", { name: "Save debt evidence", exact: true })
    .click();
  await expect(page.getByRole("status")).toContainText("Debt evidence saved");
  expect(
    (await state(page)).state.debts.find(
      (d: { id: string }) => d.id === saved.id,
    ).statements,
  ).toHaveLength(2);
  await page
    .getByRole("button", { name: "Edit terms for " + name, exact: true })
    .click();
  await page.getByLabel("Credit limit EUR", { exact: true }).fill("6000");
  const concurrent = await state(page);
  expect(
    (
      await page.request.post("/api/ledger/debts", {
        data: {
          revision: concurrent.state.revision,
          operationId: crypto.randomUUID(),
          reason: "Synthetic concurrent terms",
          id: saved.id,
          terms: saved.terms,
          statement: null,
        },
      })
    ).ok(),
  ).toBeTruthy();
  await page
    .getByLabel("Reason for debt change", { exact: true })
    .fill("Synthetic stale update");
  await page
    .getByRole("button", { name: "Save debt evidence", exact: true })
    .click();
  await expect(page.getByRole("alert")).toContainText("Debt evidence changed");
  await expect(
    page.getByLabel("Credit limit EUR", { exact: true }),
  ).toHaveValue("6000");
  await page
    .getByRole("button", { name: "Refresh debt evidence", exact: true })
    .click();
  await expect(
    page.getByRole("button", { name: "Save debt evidence", exact: true }),
  ).toBeDisabled();
  await page
    .getByRole("button", { name: "Discard debt draft", exact: true })
    .click();
  const download = page.waitForEvent("download");
  await page
    .getByRole("button", { name: "Download debt report", exact: true })
    .click();
  expect((await download).suggestedFilename()).toBe(
    "debt-statement-report.json",
  );
  await expect(
    page.getByRole("button", { name: "Open repayment outlook", exact: true }),
  ).toBeVisible();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBeTruthy();
  await page.screenshot({
    path: info.outputPath("debt-statements.png"),
    fullPage: true,
  });
  await page.route("**/api/ledger/debts", (route) =>
    route.fulfill({
      status: 403,
      contentType: "application/json",
      body: '{"error":"Access changed"}',
    }),
  );
  await page
    .getByRole("button", { name: "Refresh debt evidence", exact: true })
    .click();
  await expect(page.getByRole("alert")).toContainText(
    "Private debt information has been cleared",
  );
  await expect(page.getByRole("heading", { name, exact: true })).toHaveCount(0);
  await page.unroute("**/api/ledger/debts");
  await settleAndReconcile(page, cash.id, debt.id);
});
test("dated mortgage rates show principal interest fees payoff and constrained extra comparisons", async ({
  page,
}) => {
  const suffix = crypto.randomUUID().slice(0, 8),
    name = "Synthetic mortgage " + suffix,
    cash = await account(
      page,
      "Synthetic mortgage cash " + suffix,
      "CASH",
      "2000",
    ),
    debt = await account(
      page,
      "Synthetic mortgage ledger " + suffix,
      "DEBT",
      "-1000",
    ),
    before = await state(page),
    id = crypto.randomUUID();
  expect(
    (
      await page.request.post("/api/ledger/debts", {
        data: {
          revision: before.state.revision,
          operationId: crypto.randomUUID(),
          reason: "Synthetic mortgage statement",
          id,
          terms: {
            accountId: debt.id,
            name,
            kind: "MORTGAGE",
            paymentAccountId: cash.id,
            creditLimitEuro: "0",
            monthlyPaymentEuro: "100",
            monthlyFeeEuro: "1",
            maturity: offset(3650),
            extraLimitEuro: "150",
            extraFeeEuro: "5",
            lenderAssumptions: "Synthetic capped overpayment and lender fee",
            rates: [
              { from: offset(-60), annualPercent: "6" },
              { from: offset(30), annualPercent: "7" },
            ],
          },
          statement: {
            id: crypto.randomUUID(),
            start: offset(-30),
            end: offset(-1),
            due: offset(10),
            source: "Synthetic full statement",
            complete: true,
            openingEuro: "1000",
            borrowingEuro: "0",
            interestEuro: "0",
            feesEuro: "0",
            paidEuro: "0",
            balanceEuro: "1000",
            minimumEuro: "10",
          },
        },
      })
    ).ok(),
  ).toBeTruthy();
  await page.goto("/");
  await openWorkspace(page, "Cards, loans & mortgages");
  await expect(
    page.getByText("Statement net principal reduction", { exact: true }).last(),
  ).toBeVisible();
  await page
    .getByRole("combobox", { name: "Debt to compare", exact: true })
    .selectOption(id);
  await page.getByLabel("Extra principal EUR", { exact: true }).fill("151");
  await page
    .getByRole("button", { name: "Compare repayment options", exact: true })
    .click();
  await expect(page.getByRole("alert")).toContainText("Extra payment exceeds");
  await page.getByLabel("Extra principal EUR", { exact: true }).fill("100");
  await page
    .getByRole("button", { name: "Compare repayment options", exact: true })
    .click();
  await expect(
    page.getByRole("heading", {
      name: name + " repayment comparison",
      exact: true,
    }),
  ).toBeVisible();
  await page.getByText("Contractual baseline ·", { exact: false }).click();
  await expect(
    page.getByRole("columnheader", { name: "Principal", exact: true }),
  ).toBeVisible();
  await expect(page.getByText("6%", { exact: true }).first()).toBeVisible();
  await expect(page.getByText("7%", { exact: true }).first()).toBeVisible();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBeTruthy();
  await settleAndReconcile(page, cash.id, debt.id);
});
