import { useEffect,useState } from 'react';
import { ledger,type Position,type Activity } from '../api/ledger';
import { todayInDublin } from '../domain/dates';
import { money } from '../domain/money';
export default function PermittedPosition(){
  const [position,setPosition]=useState<Position|null>(null),[error,setError]=useState(''),[rows,setRows]=useState<Activity[]>([]),[selected,setSelected]=useState('');
  async function refresh(){setError('');setRows([]);setSelected('');try{setPosition(await ledger<Position>('/shared?asOf='+todayInDublin()));}catch(e){setPosition(null);setError((e as Error).message);}}
  useEffect(()=>{void refresh();},[]);
  async function activity(id:string){setSelected(id);setRows([]);try{setRows(await ledger<Activity[]>('/accounts/'+id+'/activity'));}catch(e){setSelected('');setError((e as Error).message);}}
  return <section className="permitted-position"><h2>Permitted money position</h2><p>This shared-only view excludes all private accounts, including your own. It may be incomplete and is not permission to spend.</p><button onClick={()=>void refresh()}>Refresh permitted position</button>
    {position&&<><p>As of {position.asOf} · recorded cash {money(position.cashMinor)} · signed debt {money(position.debtSignedMinor)}</p>
      {position.accounts.length===0?<p>No financial accounts are currently shared with you.</p>:position.accounts.map(a=><article key={a.id}><h3>{a.name}</h3><p>{money(a.bookedMinor)} · {a.shared?'Shared':'Your private account'} · {a.reconciled?'Reconciled':'Needs review'}</p><button onClick={()=>void activity(a.id)}>View {a.name} activity</button></article>)}
      {position.warnings.map((w,i)=><p key={i}>{w}</p>)}
    </>}
    {selected&&<div><h3>Permitted account activity</h3><ul>{rows.map(r=><li key={r.id}>{r.date} · {r.description} · {money(r.amountMinor)} · {r.status}</li>)}</ul></div>}
    {error&&<p role="alert">{error}</p>}
  </section>;
}
