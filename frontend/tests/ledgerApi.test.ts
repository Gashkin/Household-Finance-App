import { test } from "node:test";
import assert from "node:assert/strict";
import { ledger, euroText } from "../src/api/ledger.ts";
test("ledger preserves exact signed text, source and server revision without client calculations", async () => {
  const previous = globalThis.fetch;
  let sent: unknown;
  globalThis.fetch = async (input, init) => {
    sent = {
      input,
      body: JSON.parse(String(init?.body)),
      method: init?.method,
    };
    return new Response(JSON.stringify({ revision: 4, bookedMinor: -123 }), {
      status: 200,
    });
  };
  try {
    assert.deepEqual(
      await ledger("/accounts/a/activity", {
        revision: 3,
        row: { amountEuro: "-1.23" },
        source: "Synthetic",
      }),
      { revision: 4, bookedMinor: -123 },
    );
    assert.deepEqual(sent, {
      input: "/api/ledger/accounts/a/activity",
      body: { revision: 3, row: { amountEuro: "-1.23" }, source: "Synthetic" },
      method: "POST",
    });
  } finally {
    globalThis.fetch = previous;
  }
});
test("ledger explains validation, uncertain network outcome and masked service failures", async () => {
  const previous = globalThis.fetch;
  try {
    globalThis.fetch = async () =>
      new Response(JSON.stringify({ error: "Account changed; reload" }), {
        status: 400,
      });
    await assert.rejects(ledger("/publish", {}), /Account changed/);
    globalThis.fetch = async () =>
      new Response("private SQL details", { status: 500 });
    await assert.rejects(ledger("/publish", {}), /service is unavailable/);
    globalThis.fetch = async () => {
      throw Error("network");
    };
    await assert.rejects(ledger("/publish", {}), /Cannot confirm this request/);
  } finally {
    globalThis.fetch = previous;
  }
});
test("signed EUR display remains exact at safe-integer boundary", () => {
  assert.equal(euroText(-9007199254740991), "-90071992547409.91");
  assert.equal(euroText(1), "0.01");
});
