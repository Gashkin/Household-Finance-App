import { test } from 'node:test';
import assert from 'node:assert/strict';
import { intakeRequest, approveIntake } from '../src/api/evidenceIntake.ts';

test('intake transport uses a transient key, no-store and explicit approval payload', async () => {
  const original = globalThis.fetch;
  try {
    globalThis.fetch = async (url, options) => {
      assert.match(String(url), /drafts\/draft-id\/approve$/);
      assert.equal((options?.headers as Record<string,string>)['X-Intake-Key'], 'transient-key');
      assert.equal(options?.cache, 'no-store');
      assert.equal(JSON.parse(String(options?.body)).acknowledged, true);
      return new Response('{"id":"snapshot"}');
    };
    assert.equal((await approveIntake('draft-id','transient-key',{asOfDate:'2026-11-14',minimumReserveEuro:'400',acknowledged:true,rows:[]})).id, 'snapshot');
  } finally { globalThis.fetch = original; }
});
test('intake errors stay actionable and lock/discard handles empty responses', async () => {
  const original = globalThis.fetch;
  try {
    globalThis.fetch = async () => new Response(null, {status:204});
    assert.equal(await intakeRequest('/drafts/id','key','DELETE'), undefined);
    globalThis.fetch = async () => new Response('Access key required', {status:401});
    await assert.rejects(intakeRequest('/session','bad'), /Access key required/);
    globalThis.fetch = async () => { throw new TypeError('offline'); };
    await assert.rejects(intakeRequest('/session','key'), /Cannot reach the evidence intake service/);
  } finally { globalThis.fetch = original; }
});
