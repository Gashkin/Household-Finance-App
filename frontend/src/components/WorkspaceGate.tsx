import { useEffect, useRef, useState, type ReactNode } from 'react';
import { workspacePost } from '../api/workspace';

export default function WorkspaceGate({children}:{children:ReactNode}) {
  const channel=useRef<BroadcastChannel|null>(null);
  const [status,setStatus]=useState<{configured:boolean;unlocked:boolean}|null>(null);
  const [password,setPassword]=useState('');
  const [error,setError]=useState('');
  const [busy,setBusy]=useState(false);
  const [generation,setGeneration]=useState(0);
  useEffect(()=>{
    const locked=()=>{ setStatus(old=>old?{...old,unlocked:false}:old); setGeneration(n=>n+1); setPassword(''); };
    window.addEventListener('workspace-locked',locked);
    if (typeof BroadcastChannel !== 'undefined') { channel.current=new BroadcastChannel('household-workspace'); channel.current.onmessage=event=>{if(event.data==='locked')locked();}; }
    fetch('/api/workspace/status').then(async r=>{if(!r.ok)throw Error('Cannot reach the local workspace');setStatus(await r.json());}).catch(e=>setError(e.message));
    // Recheck when returning to a tab; server sessions also expire after inactivity.
    const check=()=>{if(document.visibilityState==='visible')void fetch('/api/workspace/status').then(r=>r.ok?r.json():null).then(s=>{if(s&&!s.unlocked)locked();}).catch(()=>{});};
    document.addEventListener('visibilitychange',check);
    return ()=>{channel.current?.close();channel.current=null;window.removeEventListener('workspace-locked',locked);document.removeEventListener('visibilitychange',check);};
  },[]);
  async function unlock(e:React.FormEvent) {
    e.preventDefault();setBusy(true);setError('');
    try {await workspacePost('unlock',{password});setPassword('');setStatus({configured:true,unlocked:true});setGeneration(n=>n+1);}
    catch(e){setError((e as Error).message);setPassword('');} finally{setBusy(false);}
  }
  async function lock() {
    setBusy(true);setError('');
    try{await workspacePost('lock',{});channel.current?.postMessage('locked');setStatus({configured:true,unlocked:false});setGeneration(n=>n+1);}
    catch(e){setError((e as Error).message);}finally{setBusy(false);}
  }
  if(!status || !status.unlocked) return <main className="workspace-gate"><h1>Household Finance</h1><h2>Private local workspace</h2>
    {!status?<p>Connecting to your workspace…</p>:!status.configured?<><p>This workspace is locked until its owner configures a password.</p><p>Set <code>WORKSPACE_PASSWORD</code> to 16–256 characters in the backend environment, restart it, then reload this page. Keep it in your password manager.</p></>:<form onSubmit={unlock}><p>Unlock to view your household records. Sessions expire after 30 minutes without requests.</p><label>Workspace password<input type="password" autoComplete="current-password" value={password} onChange={e=>setPassword(e.target.value)} maxLength={256} required/></label><button disabled={busy}>Unlock workspace</button></form>}
    {error&&<p role="alert">{error}</p>}<p>Your database stays on this device. Recovery backups use a separate passphrase.</p></main>;
  return <><div className="workspace-toolbar"><span>Private local workspace</span><button onClick={lock} disabled={busy}>Lock workspace</button></div>{error&&<p role="alert">{error}</p>}<div key={generation}>{children}</div></>;
}
