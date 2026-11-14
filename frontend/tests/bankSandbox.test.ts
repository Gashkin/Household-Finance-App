import {test} from 'node:test';
import assert from 'node:assert/strict';
import {captureSandboxCallback,completeSandboxCallback} from '../src/api/bankSandbox.ts';
test('callback is scrubbed, exchanged once while in flight and never reused as stale status',async()=>{
 const globals=globalThis as unknown as Record<string,unknown>;
 const previous={location:globals.location,history:globals.history,fetch:globalThis.fetch};
 let scrubbed='',calls=0,sent:unknown;
 globals.location={pathname:'/bank/callback',search:'?state=synthetic-state&code=synthetic-code'};
 globals.history={replaceState:(_a:unknown,_b:unknown,path:string)=>{scrubbed=path;}};
 let release:(r:Response)=>void=()=>{};
 globalThis.fetch=async(_url,init)=>{calls++;sent=JSON.parse(String(init?.body));return new Promise<Response>(r=>{release=r;});};
 try{
  captureSandboxCallback();assert.equal(scrubbed,'/bank/callback');
  const first=completeSandboxCallback();assert.ok(first);assert.equal(completeSandboxCallback(),first);assert.equal(calls,1);
  assert.deepEqual(sent,{state:'synthetic-state',code:'synthetic-code'});
  release(new Response(JSON.stringify({state:'CONNECTED'})));await first;
  assert.equal(completeSandboxCallback(),null);
 }finally{globals.location=previous.location;globals.history=previous.history;globalThis.fetch=previous.fetch;}
});
