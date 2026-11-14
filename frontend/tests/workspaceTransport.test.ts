import { test } from 'node:test';
import assert from 'node:assert/strict';
import { installWorkspaceTransport } from '../src/api/workspace.ts';

test('workspace expiry locks the screen; an intake-key rejection preserves its own error UI', async()=>{
  const events:string[]=[];const calls:{input:unknown;init:RequestInit|undefined}[]=[];
  let response=new Response('Evidence intake access key is required',{status:401});
  const fake={location:{href:'http://127.0.0.1:5173/',origin:'http://127.0.0.1:5173'},fetch:async(input:unknown,init?:RequestInit)=>{calls.push({input,init});return response;},dispatchEvent:(event:Event)=>{events.push(event.type);return true;}};
  const old=Object.getOwnPropertyDescriptor(globalThis,'window');Object.defineProperty(globalThis,'window',{value:fake,configurable:true});
  try{
    installWorkspaceTransport();
    await fake.fetch('/api/evidence-intake/session',{headers:{'X-Intake-Key':'wrong'}});
    assert.deepEqual(events,[]);
    assert.equal(new Headers(calls[0].init?.headers).get('X-Intake-Key'),'wrong');
    assert.equal(new Headers(calls[0].init?.headers).get('X-Workspace-Request'),'1');
    assert.equal(calls[0].init?.credentials,'same-origin');
    response=new Response('Unlock the workspace',{status:401,headers:{'X-Workspace-Locked':'1'}});
    await fake.fetch('/api/cash-position/latest');assert.deepEqual(events,['workspace-locked']);
    await fake.fetch('https://example.invalid/external',{headers:{'X-Example':'1'}});
    assert.equal(new Headers(calls[2].init?.headers).get('X-Workspace-Request'),null);
  }finally{if(old)Object.defineProperty(globalThis,'window',old);else Reflect.deleteProperty(globalThis,'window');}
});
