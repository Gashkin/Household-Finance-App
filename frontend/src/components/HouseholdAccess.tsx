import { useEffect,useState,type FormEvent } from 'react';
import { accessRequest,useIdentity } from '../api/access';
import { ledger,type LedgerAccount,type Position } from '../api/ledger';
import { todayInDublin } from '../domain/dates';
type User={id:string;username:string;roles:string[];state:string;revision:number;custodian:boolean;accepted:boolean};
type Audit={ACTOR_ID:string;TARGET_ID:string;ACTION:string;OUTCOME:string;RECORDED_AT:string};
export default function HouseholdAccess(){
  const me=useIdentity();const [users,setUsers]=useState<User[]>([]),[accounts,setAccounts]=useState<LedgerAccount[]>([]);
  const [name,setName]=useState(''),[password,setPassword]=useState(''),[secret,setSecret]=useState(''),[message,setMessage]=useState(''),[error,setError]=useState(''),[busy,setBusy]=useState(false),[audit,setAudit]=useState<Audit[]>([]);
  const admin=me?.roles.includes('ADMIN');
  async function refresh(){if(admin){setUsers(await accessRequest<User[]>('users'));setAudit(await accessRequest<Audit[]>('audit'));}if(me?.canRead)setAccounts((await ledger<Position>('?asOf='+todayInDublin())).accounts);}
  useEffect(()=>{void refresh().catch(e=>setError(e.message));return()=>setSecret('');},[]);
  async function run(fn:()=>Promise<void>){setBusy(true);setMessage('');setError('');setSecret('');try{await fn();await refresh();}catch(e){setError((e as Error).message);}finally{setBusy(false);setPassword('');}}
  async function reauth(e:FormEvent){e.preventDefault();await run(async()=>{await accessRequest('reauth',{password});setMessage('Sensitive actions confirmed for five minutes.');});}
  return <section className="access-panel"><h2>Household access</h2><p>Use your own username and password. Roles combine capabilities; administration alone does not grant access to private financial records.</p>
    <p>Your financial view is limited to records you own and accounts explicitly shared with you. It may be incomplete; hidden accounts are never counted or identified.</p>
    <form onSubmit={reauth}><label>Confirm current password<input type="password" autoComplete="current-password" value={password} onChange={e=>setPassword(e.target.value)} maxLength={256} required/></label><button disabled={busy}>Confirm sensitive actions</button></form>
    <button disabled={busy} onClick={()=>void run(async()=>{const result=await accessRequest<{code:string}>('recovery-code',{});setSecret(result.code);setMessage('Store this one-use recovery code offline, separately from this device. This replaces the previous code.');})}>Create my recovery code</button>
    {secret&&<div role="status"><p>{message}</p><label>Private code — shown once<textarea readOnly value={secret}/></label><button onClick={()=>setSecret('')}>Hide code</button><p>Give an invitation code privately to its intended person. Codes never grant roles by themselves.</p></div>}
    {admin&&<><h3>Invite a person</h3><form onSubmit={e=>{e.preventDefault();void run(async()=>{const result=await accessRequest<{username:string;code:string}>('invitations',{username:name});setSecret(result.code);setMessage('Invitation for '+result.username+' is ready. Expires after seven days.');setName('');});}}><label>New username<input value={name} onChange={e=>setName(e.target.value)} minLength={3} maxLength={40} required/></label><button disabled={busy}>Create private invitation</button></form>
    <h3>People and combined roles</h3><p>Admin manages people. Editor can create and change their permitted records. Reader can inspect permitted financial information. Account ownership limits editors too. Another administrator must change your own roles.</p>
    {users.map(u=><div key={u.id+':'+u.revision}><UserEditor user={u} self={u.id===me?.id} disabled={busy} save={async(roles,state)=>run(async()=>{await accessRequest('users/'+u.id,{revision:u.revision,roles,state},'PUT');setMessage('Account updated. Previous sessions must sign in again.');})}/>{!u.accepted&&<><button disabled={busy} onClick={()=>void run(async()=>{const result=await accessRequest<{code:string}>('invitations/'+u.id+'/reissue',{});setSecret(result.code);setMessage('Replacement invitation for '+u.username+'. The old code no longer works.');})}>Replace {u.username} invitation</button><button disabled={busy} onClick={()=>void run(async()=>{await accessRequest('invitations/'+u.id+'/revoke',{});setMessage('Invitation revoked.');})}>Revoke {u.username} invitation</button></>}</div>)}
    <h3>Recent access events</h3><ul>{audit.slice(0,20).map((a,i)=><li key={i}>{a.RECORDED_AT} · {a.ACTION} · {a.OUTCOME} · actor {a.ACTOR_ID}</li>)}</ul></>}
    {me?.canRead&&<><h3>Account privacy</h3><p>New accounts are private. Sharing exposes the account, its activity and audit evidence to financial Readers and Editors. Sharing does not give another person ownership or a right to change it.</p>
    {accounts.map(a=><div key={a.id}><strong>{a.name}</strong> · {a.shared?'Shared':'Private'} {me.canEdit&&a.owned&&<button disabled={busy} onClick={()=>void run(async()=>{await ledger('/accounts/'+a.id+'/sharing',{revision:a.revision,shared:!a.shared},'PUT');setMessage('Account privacy updated. Review any forecasts based on this account.');})}>{a.shared?'Make private':'Share with household'}</button>}</div>)}</>}
    {me?.custodian&&<p>Your original-owner account also has the separate recovery-custodian capability. Whole-workspace encrypted backups contain every person's records and credentials. Keep archives private; ordinary Admins cannot download or restore them.</p>}
    {!secret&&message&&<p role="status">{message}</p>}{error&&<p role="alert">{error}</p>}
  </section>;
}
function UserEditor({user,self,disabled,save}:{user:User;self:boolean;disabled:boolean;save:(roles:string[],state:string)=>Promise<void>}){
  const [roles,setRoles]=useState(user.roles),[state,setState]=useState(user.state);
  return <fieldset disabled={disabled||self||!user.accepted}><legend>{user.username}{self?' (you)':''} · {user.state}</legend>
    {['ADMIN','EDITOR','READER'].map(role=><label key={role}><input type="checkbox" checked={roles.includes(role)} onChange={e=>setRoles(e.target.checked?[...roles,role]:roles.filter(x=>x!==role))}/>{role}</label>)}
    <label>Account status<select value={state} onChange={e=>setState(e.target.value)}><option value="ACTIVE">Active</option><option value="DISABLED">Disabled</option>{user.state==='INVITED'&&<option value="INVITED">Awaiting acceptance</option>}</select></label>
    <button onClick={()=>void save(roles,state)}>Save permissions</button>
  </fieldset>;
}
