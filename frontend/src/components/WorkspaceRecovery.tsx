import { useEffect,useState } from 'react';
import { workspacePost } from '../api/workspace';

export default function WorkspaceRecovery(){
  const [counts,setCounts]=useState<Record<string,number>|null>(null);
  const [password,setPassword]=useState('');const [repeat,setRepeat]=useState('');
  const [file,setFile]=useState<File|null>(null);const [confirmed,setConfirmed]=useState(false);
  const [busy,setBusy]=useState(false);const [error,setError]=useState('');const [message,setMessage]=useState('');
  useEffect(()=>{fetch('/api/workspace/records').then(async r=>{if(!r.ok)throw Error('Cannot read workspace records');setCounts(await r.json());}).catch(e=>setError(e.message));},[]);
  const empty=counts!==null&&Object.values(counts).every(n=>n===0);
  async function download(){setBusy(true);setError('');setMessage('');
    try{
      if(password.length<16||password!==repeat)throw Error('Use matching backup passphrases of at least 16 characters');
      const response=await workspacePost('backup',{password});const url=URL.createObjectURL(await response.blob());
      const a=document.createElement('a');a.href=url;a.download='household-workspace.hfb';a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);
      setMessage('Encrypted backup downloaded. Keep the file and passphrase separately, and test recovery on a fresh workspace.');
    }catch(e){setError((e as Error).message);}finally{setPassword('');setRepeat('');setBusy(false);}
  }
  async function restore(){setBusy(true);setError('');setMessage('');
    try{
      if(!file||file.size>10*1024*1024)throw Error('Choose a backup file no larger than 10 MiB');
      const bytes=new Uint8Array(await file.arrayBuffer());let binary='';for(const b of bytes)binary+=String.fromCharCode(b);
      const response=await workspacePost('restore',{password,backup:btoa(binary),confirmation:confirmed?'RESTORE INTO EMPTY WORKSPACE':''});
      setCounts(await response.json());setFile(null);setConfirmed(false);
      window.dispatchEvent(new Event('workspace-restored'));
    }catch(e){setError((e as Error).message);}finally{setPassword('');setRepeat('');setBusy(false);}
  }
  return <section className="recovery-panel"><h2>Backup & recovery</h2><p>Return to your saved records after restarting the app, or recover them on a fresh local workspace.</p>
    <p>Custodian-only recovery: backups contain all people's saved financial records, identities, password verifiers, permissions, reviewed ledger activity and audit history. Keep the archive and passphrase private. Recovery retains the fresh installation's owner password, revokes current sessions and disables all other users pending an administrator's review. Old invitations and recovery codes are invalidated. Unsaved comparisons and temporary intake drafts are excluded.</p>
    {counts&&<p>Saved records: {Object.values(counts).reduce((a,b)=>a+b,0)}. {empty?'This workspace is empty and can receive a backup.':'Recovery is blocked here to protect existing records.'}</p>}
    <label>Backup passphrase<input type="password" autoComplete="new-password" minLength={16} maxLength={256} value={password} onChange={e=>setPassword(e.target.value)} disabled={busy}/></label>
    <label>Repeat backup passphrase<input type="password" autoComplete="new-password" maxLength={256} value={repeat} onChange={e=>setRepeat(e.target.value)} disabled={busy}/></label>
    <button disabled={busy||password.length<16||password!==repeat} onClick={download}>Download encrypted backup</button>
    <h3>Recover into an empty workspace</h3><p>Start the backend with a new empty data directory. Use the backup’s original passphrase above; the new workspace password can be different. A wrong passphrase or damaged file changes nothing.</p>
    <label>Encrypted backup file<input type="file" accept=".hfb,application/octet-stream" disabled={busy||!empty} onChange={e=>setFile(e.target.files?.[0]??null)}/></label>
    <label className="restore-confirm"><input type="checkbox" checked={confirmed} disabled={busy||!empty} onChange={e=>setConfirmed(e.target.checked)}/>I want to restore all saved records into this empty workspace</label>
    <button disabled={busy||!empty||!file||!confirmed||password.length<16} onClick={restore}>Restore backup</button>
    {busy&&<p role="status">Protecting your records…</p>}{message&&<p role="status">{message}</p>}{error&&<p role="alert">{error}</p>}
    <p>There is no passphrase reset for backups. Account recovery codes cannot decrypt an archive. Keep a copy away from this device. Local database files rely on your operating system account and full-disk encryption; network access remains disabled.</p>
  </section>;
}
