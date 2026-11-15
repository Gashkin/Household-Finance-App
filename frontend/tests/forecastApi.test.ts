import { test } from "node:test";
import assert from "node:assert/strict";
import { forecast } from "../src/api/forecast.ts";
test("forecast keeps exact EUR strings and optimistic revisions in authenticated workspace transport", async () => {
  const previous = globalThis.fetch;
  let sent: unknown;
  globalThis.fetch = async (url, init) => {
    sent = {
      url,
      method: init?.method,
      cache: init?.cache,
      body: JSON.parse(String(init?.body)),
    };
    return new Response(
      JSON.stringify({ revision: 3, scenario: { lowestMinor: -123 } }),
    );
  };
  try {
    assert.deepEqual(
      await forecast("/compare", {
        revision: 3,
        expenseEuro: "1.23",
        includeUncertain: false,
      }),
      { revision: 3, scenario: { lowestMinor: -123 } },
    );
    assert.deepEqual(sent, {
      url: "/api/forecast/compare",
      method: "POST",
      cache: "no-store",
      body: { revision: 3, expenseEuro: "1.23", includeUncertain: false },
    });
  } finally {
    globalThis.fetch = previous;
  }
});
test("forecast errors explain stale corrections and uncertain save outcomes without exposing server details", async () => {
  const previous = globalThis.fetch;
  try {
    globalThis.fetch = async () =>
      new Response(JSON.stringify({ error: "Plan changed; reload" }), {
        status: 400,
      });
    await assert.rejects(forecast("", {}), /Plan changed/);
    globalThis.fetch = async () =>
      new Response("private database details", { status: 500 });
    await assert.rejects(forecast(), /Local forecast service unavailable/);
    globalThis.fetch = async () => {
      throw Error("offline");
    };
    await assert.rejects(forecast("", {}), /a save may have completed/);
  } finally {
    globalThis.fetch = previous;
  }
});
