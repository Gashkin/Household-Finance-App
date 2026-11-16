import { useEffect, useRef, useState, type ReactNode } from 'react';
import { workspacePost } from '../api/workspace';
import { accessRequest,IdentityContext,type Identity } from '../api/access';

export default function WorkspaceGate({children}:{children:ReactNode}) {
  const channel=useRef<BroadcastChannel|null>(null);
  const [status,setStatus]=useState<{configured:boolean;unlocked:boolean}|null>(null);
  const [password,setPassword]=useState('');
  const [error,setError]=useState('');
  const [busy,setBusy]=useState(false);
  const [generation,setGeneration]=useState(0);
  const [username,setUsername]=useState('owner');
  const [identity,setIdentity]=useState<Identity|null>(null);
  const [mode,setMode]=useState<'login'|'join'|'recover'>('login');
  const [code,setCode]=useState('');
  const [accepted,setAccepted]=useState(false);
  const [message,setMessage]=useState('');
  async function loadIdentity(){setIdentity(await accessRequest<Identity>('me'));}
  useEffect(()=>{
    const locked=()=>{ setStatus(old=>old?{...old,unlocked:false}:old);setIdentity(null); setGeneration(n=>n+1); setPassword('');setCode(''); };
    window.addEventListener('workspace-locked',locked);
    const restored=()=>{locked();setMessage('Recovery complete. Sign in with this installation’s owner password. Review restored people before enabling their accounts.');channel.current?.postMessage('locked');};
    window.addEventListener('workspace-restored',restored);
    if (typeof BroadcastChannel !== 'undefined') { channel.current=new BroadcastChannel('household-workspace'); channel.current.onmessage=event=>{if(event.data==='locked')locked();}; }
    fetch('/api/workspace/status').then(async r=>{if(!r.ok)throw Error('Cannot reach the local workspace');const s=await r.json();setStatus(s);if(s.unlocked)await loadIdentity();}).catch(e=>setError(e.message));
    // Recheck when returning to a tab; server sessions also expire after inactivity.
    const check=()=>{if(document.visibilityState==='visible')void fetch('/api/workspace/status').then(r=>r.ok?r.json():null).then(s=>{if(s&&!s.unlocked){channel.current?.postMessage('locked');locked();}}).catch(()=>{});};
    const interval=window.setInterval(check,15000);
    document.addEventListener('visibilitychange',check);
    return ()=>{clearInterval(interval);channel.current?.close();channel.current=null;window.removeEventListener('workspace-locked',locked);window.removeEventListener('workspace-restored',restored);document.removeEventListener('visibilitychange',check);};
  },[]);
  async function unlock(e:React.FormEvent) {
    e.preventDefault();setBusy(true);setError('');
    try {
      if(mode==='join'){await accessRequest('accept',{code,password,accepted});setMessage('Account accepted. Sign in with your invited username; an administrator can grant roles.');setMode('login');setCode('');return;}
      if(mode==='recover'){await accessRequest('recover',{username,code,password});setMessage('Password changed. Sign in again. Your recovery code was consumed.');setMode('login');setCode('');return;}
      await workspacePost('unlock',{username,password});setPassword('');await loadIdentity();setStatus({configured:true,unlocked:true});setGeneration(n=>n+1);
    }
    catch(e){setError((e as Error).message);} finally{setPassword('');setBusy(false);}
  }
  async function lock() {
    setBusy(true);setError('');
    try{await workspacePost('lock',{});channel.current?.postMessage('locked');setStatus({configured:true,unlocked:false});setIdentity(null);setGeneration(n=>n+1);}
    catch(e){setError((e as Error).message);}finally{setBusy(false);}
  }
  if(!status || !status.unlocked) return <main className="workspace-gate"><h1>Household Finance</h1><h2>Private local workspace</h2>
    {!status?<p>Connecting to your workspace…</p>:!status.configured?<><p>This workspace is locked until its owner configures a password.</p><p>Set <code>WORKSPACE_PASSWORD</code> to 16–256 characters in the backend environment, restart it, then reload this page. Keep it in your password manager.</p></>:<>
      <nav aria-label="Account access"><button onClick={()=>{setMode('login');setCode('');setPassword('');setError('');}}>Sign in</button><button onClick={()=>{setMode('join');setError('');}}>Accept invitation</button><button onClick={()=>{setMode('recover');setError('');}}>Recover access</button></nav>
      <form onSubmit={unlock}><p>{mode==='login'?'Unlock to view your permitted household records. Sessions expire after 30 minutes without activity.':mode==='join'?'Use the private code supplied by your household administrator. Accept only an invitation you expected.':'Use your separately stored recovery code. This resets account access, not backup encryption.'}</p>
      {mode!=='join'&&<label>Username<input autoComplete="username" value={username} onChange={e=>setUsername(e.target.value)} maxLength={40} required/></label>}
      {mode!=='login'&&<label>{mode==='join'?'Invitation code':'Recovery code'}<input type="password" autoComplete="off" value={code} onChange={e=>setCode(e.target.value)} maxLength={100} required/></label>}
      <label>{mode==='login'?'Workspace password':'New account password'}<input type="password" autoComplete={mode==='login'?'current-password':'new-password'} value={password} onChange={e=>setPassword(e.target.value)} minLength={16} maxLength={256} required/></label>
      {mode==='join'&&<label><input type="checkbox" checked={accepted} onChange={e=>setAccepted(e.target.checked)}/>I accept this household invitation</label>}
      <button disabled={busy||(mode==='join'&&!accepted)}>{mode==='login'?'Unlock workspace':mode==='join'?'Create my account':'Reset account password'}</button></form></>}
    {message&&<p role="status">{message}</p>}
    {error&&<p role="alert">{error}</p>}<p>Your database stays on this device. Recovery backups use a separate passphrase.</p></main>;
  if(!identity)return <p role="status">Loading your permissions…</p>;
  return <IdentityContext.Provider value={identity}><div className="workspace-toolbar"><span>Signed in as {identity.username} · {identity.roles.join(', ')||'No financial access'}</span><button onClick={lock} disabled={busy}>Lock workspace</button></div>{error&&<p role="alert">{error}</p>}<div key={generation}>{children}</div></IdentityContext.Provider>;
}
