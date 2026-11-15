import { test } from "node:test";
import assert from "node:assert/strict";
import { plans } from "../src/api/plans.ts";
test("plan transport exposes stale writes and unknown save outcomes without retry or private server errors", async () => {
  const original = globalThis.fetch;
  let calls = 0;
  try {
    globalThis.fetch = async () => {
      calls++;
      throw Error("offline");
    };
    await assert.rejects(
      plans("/id/decision", { revision: 3 }),
      /save may have completed/,
    );
    assert.equal(calls, 1);
    globalThis.fetch = async () =>
      new Response(
        JSON.stringify({ error: "Plan changed; reload before saving" }),
        { status: 409 },
      );
    await assert.rejects(plans("/id", {}, "PUT"), /Plan changed; reload/);
    globalThis.fetch = async () =>
      new Response("private database details", { status: 500 });
    await assert.rejects(plans(), /Could not load financial plans. Retry loading/);
    await assert.rejects(plans("/id", {}, "PUT"), /Could not save. Reload/);
    globalThis.fetch = async () => { throw Error("offline"); };
    await assert.rejects(plans(), /Could not load financial plans. Check the connection/);
  } finally {
    globalThis.fetch = original;
  }
});
