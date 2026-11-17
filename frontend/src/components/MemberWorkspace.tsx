import { useEffect,useRef,useState,type FormEvent } from 'react';
import { member,type MemberWorkspace as Workspace,type PurchaseView,type MemberEvent } from '../api/member';
import { useIdentity } from '../api/access';
import { forecast,type ForecastWorkspace } from '../api/forecast';
import { ledger,type Activity,type LedgerAccount } from '../api/ledger';
import { money } from '../domain/money';

function PurchaseCard({item,accounts,busy,onChange,onHistory}:{item:PurchaseView;accounts:LedgerAccount[];busy:boolean;onChange:(item:PurchaseView,action:string,extra?:object)=>void;onHistory:(id:string)=>void}){
  const identity=useIdentity();const p=item.purchase,own=item.ownerId===identity?.id;
  const [reason,setReason]=useState(''),[account,setAccount]=useState(''),[entry,setEntry]=useState(''),[rows,setRows]=useState<Activity[]>([]),[error,setError]=useState('');
  useEffect(()=>{let alive=true;setRows([]);setEntry('');setError('');if(account)void ledger<Activity[]>('/accounts/'+account+'/activity').then(v=>{if(alive)setRows(v);}).catch(e=>{if(alive)setError((e as Error).message);});return()=>{alive=false;};},[account]);
  return <article className="member-purchase" aria-label={'Purchase: '+p.title}>
    <h3>{p.title}</h3><p>{money(p.amountMinor)} · {p.date} · {p.category}</p>
    <p><strong>{p.state}</strong> · {p.sharedRequest?'Shared request: '+p.requestDecision.toLowerCase():'Personal plan'} · {item.assessment.split('_').join(' ').toLowerCase()}</p>
    {p.decisionReason&&<p>Decision: {p.decisionReason}</p>}
    {item.actualMinor!==null&&<p>Matched actual {money(item.actualMinor)} · variance {money(item.varianceMinor!)} (positive means over plan)</p>}
    {item.matchWarning&&<p role="status">{item.matchWarning}</p>}
    <div className="member-actions">
      {own&&['PLANNED','REQUESTED','APPROVED','DECLINED'].includes(p.state)&&<button disabled={busy} onClick={()=>onChange(item,'BOUGHT')}>Mark bought</button>}
      {own&&['PLANNED','REQUESTED','APPROVED','DECLINED'].includes(p.state)&&<button disabled={busy} onClick={()=>onChange(item,'CANCEL')}>Cancel purchase</button>}
      <button disabled={busy} onClick={()=>onHistory(item.id)}>Decision history</button>
    </div>
    {item.canDecide&&<fieldset disabled={busy}><legend>Review shared request</legend><label>Decision reason<input maxLength={200} value={reason} onChange={e=>setReason(e.target.value)}/></label><p>Approval adds a shared reservation. Confirm this is an additional cost, absent from published bills and other purchases. It changes no account or budget.</p><button disabled={!reason.trim()} onClick={()=>onChange(item,'APPROVE',{reason})}>Approve additional cost</button><button disabled={!reason.trim()} onClick={()=>onChange(item,'DECLINE',{reason})}>Decline request</button></fieldset>}
    {own&&p.state==='BOUGHT'&&<fieldset disabled={busy}><legend>Match actual activity</legend><p>Only booked outgoing activity in a shared cash account can be matched. Matching releases this purchase reservation because the ledger already includes the expense. Bought items remain reserved until matched.</p>
      <label>Actual account<select aria-label="Actual account" value={account} onChange={e=>setAccount(e.target.value)}><option value="">Choose account</option>{accounts.filter(a=>a.kind==='CASH').map(a=><option key={a.id} value={a.id}>{a.name}</option>)}</select></label>
      <label>Booked outgoing activity<select aria-label="Booked outgoing activity" value={entry} onChange={e=>setEntry(e.target.value)}><option value="">Choose activity</option>{rows.filter(r=>r.status==='BOOKED'&&r.kind==='ACTIVITY'&&r.amountMinor<0).map(r=><option key={r.id} value={r.id}>{r.date} · {r.description} · {money(r.amountMinor)}</option>)}</select></label>
      <button disabled={!entry||!account} onClick={()=>onChange(item,'MATCH',{accountId:account,entryId:entry})}>Confirm actual match</button>
      {p.entryId&&<button onClick={()=>onChange(item,'UNMATCH')}>Remove actual match</button>}{error&&<p role="alert">{error}</p>}
    </fieldset>}
  </article>;
}

export default function MemberWorkspace(){
  const [data,setData]=useState<Workspace|null>(null),[busy,setBusy]=useState(false),[error,setError]=useState(''),[notice,setNotice]=useState('');
  const [title,setTitle]=useState(''),[amount,setAmount]=useState(''),[date,setDate]=useState(''),[category,setCategory]=useState('Everyday'),[shared,setShared]=useState(false);
  const [history,setHistory]=useState<MemberEvent[]|null>(null),[historyTitle,setHistoryTitle]=useState('');
  const [categories,setCategories]=useState(''),[dashboard,setDashboard]=useState('DETAIL'),[notifications,setNotifications]=useState(true),[consent,setConsent]=useState(false);
  const [additional,setAdditional]=useState(false);const purchaseId=useRef(crypto.randomUUID());
  const sequence=useRef(0),identity=useIdentity();
  function accept(value:Workspace){setData(value);setCategories(value.preferences.values.categories.join(', '));setDashboard(value.preferences.values.dashboard);setNotifications(value.preferences.values.notifications);setCategory(c=>value.preferences.values.categories.includes(c)?c:value.preferences.values.categories[0]);setDate(d=>d||value.report.asOf);}
  async function run(task:()=>Promise<Workspace>,message=''){
    const n=++sequence.current;setBusy(true);setError('');setNotice('');setHistory(null);
    try{const v=await task();if(n===sequence.current){accept(v);setNotice(message);}}
    catch(e){if(n===sequence.current){setData(null);setError((e as Error).message);}}
    finally{if(n===sequence.current)setBusy(false);}
  }
  useEffect(()=>{void run(()=>member());return()=>{sequence.current++;};},[]);
  async function submit(e:FormEvent){e.preventDefault();if(!data)return;await run(async()=>{const v=await member('/purchases',{id:purchaseId.current,title,category,amountEuro:amount,date,sharedRequest:shared,reportToken:data.report.token,acknowledged:additional});purchaseId.current=crypto.randomUUID();setTitle('');setAmount('');setAdditional(false);return v;},'Purchase recorded. No money was moved.');}
  function change(item:PurchaseView,action:string,extra={}){void run(()=>member('/purchases/'+item.id,{revision:item.revision,action,reportToken:data?.report.token,...extra},'PUT'),'Purchase updated.');}
  async function showHistory(id:string){const n=++sequence.current;setBusy(true);setHistory(null);setError('');setHistoryTitle(data?.purchases.find(p=>p.id===id)?.purchase.title||'Purchase');try{const h=await member<MemberEvent[]>('/purchases/'+id+'/history');if(n===sequence.current)setHistory(h);}catch(e){if(n===sequence.current){setData(null);setError((e as Error).message);}}finally{if(n===sequence.current)setBusy(false);}}
  async function publish(){if(!data)return;await run(async()=>{const f=await forecast<ForecastWorkspace>();if(!f.plan)throw Error('Save a reconciled ledger forecast in Plan ahead first.');return member('/publication',{revision:data.report.boardRevision,forecastRevision:f.plan.revision,acknowledged:consent});},'Shared forecast published.');setConsent(false);}
  async function download(){setError('');try{const r=await fetch('/api/member/export',{cache:'no-store'});if(!r.ok)throw Error('Report export unavailable; refresh access.');const url=URL.createObjectURL(await r.blob());const a=document.createElement('a');a.href=url;a.download='permitted-spending-report.json';a.click();URL.revokeObjectURL(url);}catch(e){setData(null);setError((e as Error).message);}}
  const report=data?.report;
  return <section className="member-workspace"><header><p className="eyebrow">Your household view</p><h2>Spending &amp; purchases</h2><p>Understand shared money, plan a purchase and keep a record of decisions.</p><div className="member-actions"><button disabled={busy} onClick={()=>void run(()=>member())}>Refresh spending workspace</button>{data&&<button disabled={busy} onClick={()=>void download()}>Download permitted report</button>}</div></header>
    {busy&&<p role="status">Checking current evidence…</p>}{error&&<p role="alert">{error}</p>}{notice&&<p role="status">{notice}</p>}
    {data&&report&&<>
      <section aria-label="Permitted spending report"><h3>Shared spending report</h3><p>As of {report.asOf} · shared accounts only · {report.evidenceState.split('_').join(' ').toLowerCase()}</p>
        <div className="member-metrics"><div><span>Recorded cash</span><strong>{report.recordedCashMinor===null?'Unknown':money(report.recordedCashMinor)}</strong></div><div><span>Conservative spending room</span><strong>{report.spendingRoomMinor===null?'Unknown':money(report.spendingRoomMinor)}</strong></div><div><span>Published bills</span><strong>{report.commitmentsMinor===null?'Unknown':money(report.commitmentsMinor)}</strong></div><div><span>Purchase reservations</span><strong>{money(report.purchaseReservationsMinor)}</strong></div></div>
        {report.board&&<p>Published by {report.board.publisherName} at {new Date(report.board.publishedAt).toLocaleString()} · through {report.board.forecast.endDate} · reserve {money(report.reserveMinor!)}</p>}
        <ul className="member-warnings">{report.warnings.map(w=><li key={w}>{w}</li>)}</ul>
        {report.position.warnings.map(w=><p key={w}>{w}</p>)}
        {dashboard==='DETAIL'&&<><h4>Shared accounts and source freshness</h4>{report.position.accounts.length===0?<p>No accounts are currently shared.</p>:report.position.accounts.map(a=><p key={a.id}>{a.name} · {money(a.bookedMinor)} · {a.reconciled?'Reconciled today':'Needs review'} · statement {a.reconciledDate||'unknown'}</p>)}
          {report.board&&<><h4>Published expectations</h4><ul>{report.board.forecast.events.map(e=><li key={e.id}>{e.date} · {e.label} · {money(e.amountMinor)} · {e.direction} {e.certainty}</li>)}</ul></>}
          <p>Trends: {report.trendState}</p></>}
      </section>
      {report.canPublish&&<details><summary>Publish a reviewed shared forecast</summary>
        <p>In Plan ahead, choose Reconciled shared cash only and save today's reviewed forecast. Private accounts stay excluded. This publishes its name, dates, source descriptions, bills, income expectations and reserve to permitted members. Review labels for private information and exclude costs already represented by pending debits or purchase reservations.</p>
        <label className="member-check"><input type="checkbox" checked={consent} onChange={e=>setConsent(e.target.checked)}/>I reviewed the shared sources and consent to publish these forecast details</label>
        <button disabled={busy||!consent} onClick={()=>void publish()}>Publish shared forecast</button>
      </details>}
      <section aria-label="Purchase planning"><h3>Plan a purchase</h3>
        <form onSubmit={e=>void submit(e)}><fieldset disabled={busy}>
          <div className="member-form-grid">
            <label>Purchase title<input required maxLength={100} value={title} onChange={e=>setTitle(e.target.value)}/></label>
            <label>Planned amount EUR<input required inputMode="decimal" placeholder="0.00" value={amount} onChange={e=>setAmount(e.target.value)}/></label>
            <label>Purchase date<input required type="date" value={date} min={report.asOf} onChange={e=>setDate(e.target.value)}/></label>
            <label>Purchase category<select value={category} onChange={e=>setCategory(e.target.value)}>{data.preferences.values.categories.map(c=><option key={c}>{c}</option>)}</select></label>
          </div>
          <label className="member-check"><input type="checkbox" checked={shared} onChange={e=>setShared(e.target.checked)}/>Request an additional shared cost from the publishing editor</label>
          <p>Personal plans stay private. A request shares its title, amount, category, date and decision history with the publishing editor. Pending requests reserve your view; approved requests reserve every member's shared report. Approval changes no bill, budget or ledger entry.</p>
          <label className="member-check"><input required type="checkbox" checked={additional} onChange={e=>setAdditional(e.target.checked)}/>I confirm this cost is additional to published bills, pending debits and existing purchase plans</label>
          <button type="submit">{shared?'Submit shared request':'Save purchase plan'}</button>
        </fieldset></form>
      </section>
      <section aria-label="Purchase list"><h3>Your purchases &amp; requests to review</h3>{data.purchases.length===0?<p>No purchases or permitted requests yet.</p>:data.purchases.map(p=><PurchaseCard key={p.id+':'+p.revision} item={p} accounts={report.position.accounts} busy={busy} onChange={change} onHistory={id=>void showHistory(id)}/>)}</section>
      {history&&<section aria-label="Decision history"><h3>History: {historyTitle}</h3><ol>{history.map((h,i)=><li key={i}>{new Date(h.at).toLocaleString()} · {h.actor} · {h.action} · {h.snapshot.state}{h.snapshot.decisionReason?' · '+h.snapshot.decisionReason:''}</li>)}</ol></section>}
      <details><summary>Personal dashboard &amp; notifications</summary>
        <form onSubmit={e=>{e.preventDefault();void run(()=>member('/preferences',{revision:data.preferences.revision,categories:categories.split(',').map(c=>c.trim()),dashboard,notifications,markSeen:false},'PUT'),'Preferences saved.');}}>
          <fieldset disabled={busy}>
            <label>Purchase categories (comma separated)<input maxLength={800} value={categories} onChange={e=>setCategories(e.target.value)}/></label>
            <p>These are your purchase labels. They do not recategorise ledger activity or change budgets.</p>
            <label>Dashboard detail<select aria-label="Dashboard detail" value={dashboard} onChange={e=>setDashboard(e.target.value)}><option value="DETAIL">Accounts and expectations</option><option value="COMPACT">Summary</option></select></label>
            <label className="member-check"><input type="checkbox" checked={notifications} onChange={e=>setNotifications(e.target.checked)}/>Show local purchase updates</label>
            <button type="submit">Save personal preferences</button>
          </fieldset>
        </form>
      </details>
      {data.preferences.values.notifications&&<section aria-label="Purchase updates"><h3>Purchase updates</h3><p>Updates appear here on refresh. Nothing is emailed or sent externally.</p>{data.notifications.length===0?<p>No unread updates.</p>:<><ul>{data.notifications.map(n=><li key={n.id}>{n.action} · {new Date(n.at).toLocaleString()}</li>)}</ul><button disabled={busy} onClick={()=>void run(()=>member('/preferences',{revision:data.preferences.revision,categories:data.preferences.values.categories,dashboard:data.preferences.values.dashboard,notifications:true,markSeen:true},'PUT'))}>Mark updates read</button></>}</section>}
      <p>Signed in as {identity?.username}. Administrators see this report only through their own financial permissions.</p>
    </>}
  </section>;
}
