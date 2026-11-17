import { test } from 'node:test';
import assert from 'node:assert/strict';
import { member } from '../src/api/member.ts';
test('member saves explain uncertain outcomes without retrying or exposing private server errors',async()=>{
  const original=globalThis.fetch;let attempts=0;
  try{
    globalThis.fetch=async()=>{attempts++;throw Error('synthetic network failure');};
    await assert.rejects(member('/purchases',{id:'stable-synthetic-id'}),/save may have completed/);assert.equal(attempts,1);
    await assert.rejects(member(),/Could not load the member workspace. Check the connection/);
    globalThis.fetch=async()=>new Response('synthetic private database detail',{status:500});
    await assert.rejects(member(),/Could not load the member workspace. Refresh to retry/);
    await assert.rejects(member('/purchases/id',{},'PUT'),/Could not save. Refresh/);
    globalThis.fetch=async()=>new Response(JSON.stringify({error:'Evidence changed; refresh first'}),{status:409});
    await assert.rejects(member('/purchases/id',{},'PUT'),/Evidence changed; refresh first/);
  }finally{globalThis.fetch=original;}
});
