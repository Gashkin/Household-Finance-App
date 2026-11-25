import { openWorkspace } from './workspace-navigation';
import { test, expect } from "@playwright/test";
import { unlockFixture } from "./workspace-helper";
test.beforeEach(unlockFixture);
test.afterEach(async ({ page }) => {
  await page.request.post("/api/workspace/lock", {
    headers: { "X-Workspace-Request": "1" },
  });
});
test("owner sees disabled connection and cannot enable missing OAuth configuration", async ({
  page,
}) => {
  await page.goto("/");
  await openWorkspace(page, "ChatGPT connection");
  await expect(
    page.getByRole("heading", { name: "ChatGPT connection", exact: true }),
  ).toBeVisible();
  await expect(
    page.getByText("Secure sign-in setup is required", { exact: false }),
  ).toBeVisible();
  await expect(
    page.getByRole("button", { name: "Enable connection", exact: true }),
  ).toBeDisabled();
  await expect(
    page.getByText("No requests awaiting a decision.", { exact: false }),
  ).toBeVisible();
});
test("one-use approval denial disable and failure recovery controls are accessible", async ({
  page,
}, testInfo) => {
  // Explicit UI transport fixture; real protocol enforcement is exercised by McpServerTest.
  const id = "12345678-1234-4234-8234-123456789abc";
  let enabled = true,
    decision = "PENDING",
    fail = false;
  const actions: string[] = [];
  await page.route("**/api/mcp**", async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (route.request().method() === "POST") {
      actions.push(path);
      if (fail) {
        await route.fulfill({ status: 409, body: "Changed" });
        return;
      }
      if (path.endsWith("/approve")) decision = "APPROVED";
      if (path.endsWith("/deny")) decision = "DENIED";
      if (path.endsWith("/disable")) {
        enabled = false;
        decision = "";
      }
      if (path.endsWith("/enable")) enabled = true;
      await route.fulfill({ json: { decided: true } });
      return;
    }
    await route.fulfill({
      json: {
        configured: true,
        enabled,
        approvals: decision
          ? [
              {
                id,
                caller: "fixture123",
                expiresAt: new Date(Date.now() + 120000).toISOString(),
                state: decision,
              },
            ]
          : [],
        audit: [],
      },
    });
  });
  await page.goto("/");
  await openWorkspace(page, "ChatGPT connection");
  await expect(page.getByText(id, { exact: true })).toBeVisible();
  fail = true;
  await page
    .getByRole("button", { name: "Approve this one read", exact: true })
    .click();
  await expect(page.getByRole("alert")).toContainText("refused");
  fail = false;
  await page.getByRole("button", { name: "Deny", exact: true }).click();
  await expect(page.getByText("DENIED", { exact: false })).toBeVisible();
  decision = "PENDING";
  await page
    .getByRole("button", { name: "Refresh requests", exact: true })
    .click();
  await page
    .getByRole("button", { name: "Approve this one read", exact: true })
    .click();
  await expect(page.getByText("APPROVED", { exact: false })).toBeVisible();
  await page
    .getByRole("button", { name: "Disable and revoke approvals", exact: true })
    .click();
  await expect(page.getByText("Disabled", { exact: true })).toBeVisible();
  await expect(
    page.getByText("No requests awaiting a decision.", { exact: false }),
  ).toBeVisible();
  expect(actions).toContain("/api/mcp/approvals/" + id + "/deny");
  expect(actions).toContain("/api/mcp/approvals/" + id + "/approve");
  await page.screenshot({
    path: testInfo.outputPath("mcp-controls.png"),
    fullPage: true,
  });
});
