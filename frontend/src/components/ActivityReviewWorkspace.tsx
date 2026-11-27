import { useEffect, useRef, useState } from 'react';
import { ledger, type Activity, type Position, type Audit } from '../api/ledger';
import LocalSuggestions from './LocalSuggestions';
import { todayInDublin } from '../domain/dates';
import { money } from '../domain/money';

type Category={id:string;revision:number;name:string;parentId:string|null;retired:boolean;budgetEuro:string};
type Rule={id:string;revision:number;description:string;categoryId:string;approved:boolean};
type Entry=Activity&{reviewedMerchant?:string;categoryId:string|null;reviewState:string;linkedEntryId:string|null;linkType:string|null};
type Item={accountId:string;accountName:string;revision:number;entry:Entry;issues:string[];risk:string;suggestion:{categoryId:string|null;confidence:string;explanation:string}};
type Guide={guide:{throughDate:string;unresolved:number;checks:string[];monthSpendingMinor:number;budgetMinor:number;completedReviews:{id:string;payload:string}[]};auditCount:number};

export default function ActivityReviewWorkspace(){
 const [date,setDate]=useState(todayInDublin()),[from,setFrom]=useState('2000-01-01'),[account,setAccount]=useState(''),[issue,setIssue]=useState(''),[risk,setRisk]=useState('');
 const [items,setItems]=useState<Item[]>([]),[all,setAll]=useState<Item[]>([]),[categories,setCategories]=useState<Category[]>([]),[rules,setRules]=useState<Rule[]>([]),[position,setPosition]=useState<Position|null>(null),[guide,setGuide]=useState<Guide|null>(null);
 const [selected,setSelected]=useState(''),[category,setCategory]=useState(''),[state,setState]=useState('CONFIRMED'),[reason,setReason]=useState(''),[offer,setOffer]=useState(false),[other,setOther]=useState(''),[linkType,setLinkType]=useState('TRANSFER');
 const [editCategory,setEditCategory]=useState(''),[name,setName]=useState(''),[parent,setParent]=useState(''),[budget,setBudget]=useState('0.00'),[retired,setRetired]=useState(false),[merge,setMerge]=useState('');
 const [history,setHistory]=useState<Audit[]>([]),[error,setError]=useState(''),[message,setMessage]=useState(''),[busy,setBusy]=useState(false),[ack,setAck]=useState(false);
 const current=all.find(i=>i.entry.id===selected);
 const generation=useRef(0);
 const categoryVersion=useRef(0);
 const selectedVersion=categoryVersion.current;
 const loadedVersion=generation.current;
 const [modelPrefill,setModelPrefill]=useState(false);
 async function load(){
   const version=++generation.current;
   const q=new URLSearchParams({from,to:date});if(account)q.set('account',account);if(issue)q.set('issue',issue);if(risk)q.set('risk',risk);
   const [i,a,c,r,p,g]=await Promise.all([ledger<Item[]>('/review/queue?'+q),ledger<Item[]>('/review/queue?from=1900-01-01&to='+date),ledger<Category[]>('/review/categories'),ledger<Rule[]>('/review/rules'),ledger<Position>('?asOf='+date),ledger<Guide>('/review/guide?through='+date)]);
   if(version!==generation.current)return;
   setItems(i);setAll(a);setCategories(c);setRules(r);setPosition(p);setGuide(g);setAck(false);
 }
 useEffect(()=>{let live=true;setItems([]);setAll([]);setGuide(null);setError('');void load().catch(e=>{if(live)setError(e.message);});return()=>{live=false;generation.current++;};},[date,from,account,issue,risk]);
 async function run(action:()=>Promise<unknown>,success:string){setBusy(true);setError('');setMessage('');setHistory([]);try{await action();await load();setMessage(success);}catch(e){setError(e instanceof Error?e.message:'Review request failed');}finally{setBusy(false);}}
 const label=(id:string|null)=>categories.find(c=>c.id===id)?.name||'Uncategorised';
 function evidence(value:unknown):string {
   if(value===null||value===undefined)return 'No record';
   if(typeof value==='string'){try{return evidence(JSON.parse(value));}catch{return value;}}
   if(Array.isArray(value))return value.map(evidence).join('; ');
   if(typeof value!=='object')return String(value);
   const v=value as Record<string,unknown>;
   if(v.row)return evidence(v.row)+(v.review?' · '+evidence(v.review):'');
   if(v.result)return 'Through '+v.throughDate+' · '+evidence(v.result);
   if(v.description&&v.amountMinor!==undefined)return `${v.date} · ${v.description} · ${money(Number(v.amountMinor))} · ${v.status} · ${v.reviewState} · ${label(v.categoryId as string|null)}${v.linkType?' · '+v.linkType:''}`;
   if(v.amountEuro!==undefined)return `${v.date} · ${v.description} · €${v.amountEuro} · ${v.status} · ${v.kind}`;
   if(v.statementMinor!==undefined)return `Statement ${money(Number(v.statementMinor))}; difference ${money(Number(v.differenceMinor))}`;
   if(v.name)return String(v.name)+(v.retired===undefined?'':v.retired?' · Retired':' · Active');
   if(v.description&&v.approved!==undefined)return `${v.description} → ${label(v.categoryId as string|null)} · ${v.approved?'Approved':'Inactive'}`;
   if(v.throughDate)return `Through ${v.throughDate}${v.balanceMinor===undefined?'':' · '+money(Number(v.balanceMinor))}${v.source?' · '+v.source:''}`;
   if(v.expectedMinor!==undefined)return 'Booked balance '+money(Number(v.expectedMinor));
   return 'Recorded source-backed decision; expand full evidence for its details';
 }
 function choose(i:Item){categoryVersion.current++;setModelPrefill(false);setSelected(i.entry.id);setCategory(i.entry.categoryId||i.suggestion.categoryId||'');setState(i.entry.reviewState==='CONFIRMED'?'CONFIRMED':'PROVISIONAL');setOther(i.entry.linkedEntryId||'');setReason('');setOffer(false);setHistory([]);}
 function categoryEdit(c?:Category){setEditCategory(c?.id||'');setName(c?.name||'');setParent(c?.parentId||'');setBudget(c?.budgetEuro||'0.00');setRetired(c?.retired||false);setMerge('');}
 return <section className="activity-review" aria-label="Activity review"><h2>Review &amp; reconcile</h2>
 <p>Resolve source-backed activity, then complete a dated review. Suggestions stay provisional until you confirm them. Transfers and card settlements require both booked sides.</p>
 {error&&<p role="alert">{error}</p>}{message&&<p role="status">{message}</p>}
 <fieldset disabled={busy}><legend>Review queue filters</legend>
 <label>From date<input type="date" value={from} onChange={e=>setFrom(e.target.value)}/></label>
 <label>Through date<input type="date" value={date} onChange={e=>setDate(e.target.value)}/></label>
 <label>Account filter<select value={account} onChange={e=>setAccount(e.target.value)}><option value="">All owned accounts</option>{position?.accounts.filter(a=>a.owned).map(a=><option key={a.id} value={a.id}>{a.name}</option>)}</select></label>
 <label>Issue filter<select value={issue} onChange={e=>setIssue(e.target.value)}><option value="">All activity</option>{['PROVISIONAL','CATEGORY','PENDING','COUNTERPART'].map(v=><option key={v}>{v}</option>)}</select></label>
 <label>Risk filter<select value={risk} onChange={e=>setRisk(e.target.value)}><option value="">All risk levels</option><option>HIGH</option><option>NORMAL</option></select></label>
 <button onClick={()=>run(load,'Review refreshed')}>Refresh review</button></fieldset>
 <p>{items.length} matching activity items. High risk includes unmatched transfers and amounts of €1,000 or more.</p>
 <div className="table-scroll"><table><thead><tr><th>Activity</th><th>Account / amount</th><th>Review</th><th>Action</th></tr></thead><tbody>{items.map(i=><tr key={i.entry.id}><td>{i.entry.date} · {i.entry.description}<br/>{i.entry.reference}</td><td>{i.accountName} · {money(i.entry.amountMinor)}</td><td>{label(i.entry.categoryId)} · {i.entry.reviewState}<br/>{i.risk}: {i.issues.join(', ')||'No open activity issue'}{i.entry.linkType&&<p>{i.entry.linkType} linked</p>}</td><td><button disabled={busy} onClick={()=>choose(i)}>Review {i.entry.description}</button></td></tr>)}</tbody></table></div>
 {current&&<fieldset disabled={busy}><legend>Review selected activity</legend><h3>{current.entry.description}</h3>
 {current.entry.reviewedMerchant&&<p>Reviewed merchant: {current.entry.reviewedMerchant}</p>}
 <LocalSuggestions key={current.entry.id+':'+current.revision} id={current.entry.id} revision={current.revision} applyCategory={id=>{
   if(categoryVersion.current===selectedVersion && generation.current===loadedVersion && !category){
     setCategory(id);setModelPrefill(true);
   }
 }} dismissCategory={()=>{if(modelPrefill){setCategory('');setModelPrefill(false);}}} refresh={load}/>
 <p>{modelPrefill ? "Local model suggested a category. Review it before confirming." : current.suggestion.explanation}</p>
 <label>Category<select value={category} onChange={e=>{categoryVersion.current++;setModelPrefill(false);setCategory(e.target.value);}}><option value="">Choose category</option>{categories.filter(c=>!c.retired).map(c=><option key={c.id} value={c.id}>{c.parentId?label(c.parentId)+' / ':''}{c.name}</option>)}</select></label>
 {(modelPrefill||current.suggestion.categoryId)&&current.entry.reviewState!=='CONFIRMED'&&<button disabled={!category}
   onClick={()=>run(()=>ledger('/review/activity/'+selected,{revision:current.revision,categoryId:category,state:'CONFIRMED',
     reason:'Owner confirmed the selected category during activity review',offerRule:false},'PUT'),'Category confirmed')}>Confirm category</button>}
 <label>Review state<select value={state} onChange={e=>setState(e.target.value)}><option>PROVISIONAL</option><option>CONFIRMED</option></select></label>
 <label>Review reason / source<input value={reason} maxLength={200} onChange={e=>setReason(e.target.value)}/></label>
 <label><input type="checkbox" checked={offer} onChange={e=>setOffer(e.target.checked)}/>Offer an exact-description local rule after this confirmed correction</label>
 <button disabled={!category||!reason} onClick={()=>run(()=>ledger('/review/activity/'+selected,{revision:current.revision,categoryId:category,state,reason,offerRule:offer},'PUT'),'Classification saved; offered rules still need explicit approval')}>Save classification</button>
 <h4>Link related activity</h4><label>Relationship<select value={linkType} onChange={e=>setLinkType(e.target.value)}>{['TRANSFER','CARD_SETTLEMENT','REFUND','REVERSAL'].map(v=><option key={v}>{v}</option>)}</select></label>
 <label>Counterpart / original debit<select value={other} onChange={e=>setOther(e.target.value)}><option value="">Choose booked activity</option>{all.filter(i=>i.entry.id!==selected&&i.entry.status==='BOOKED').map(i=><option key={i.entry.id} value={i.entry.id}>{i.accountName}: {i.entry.description} {money(i.entry.amountMinor)} ({i.entry.date})</option>)}</select></label>
 <button disabled={!other||!reason||!!current.entry.linkedEntryId} onClick={()=>run(()=>ledger('/review/activity/'+selected+'/link',{revision:current.revision,otherId:other,otherRevision:all.find(i=>i.entry.id===other)?.revision,type:linkType,reason}),'Relationship linked; totals updated')}>Link activity</button>
 <button disabled={!current.entry.linkedEntryId||!reason} onClick={()=>run(()=>ledger('/review/activity/'+selected+'/unlink',{revision:current.revision,otherId:current.entry.linkedEntryId,otherRevision:all.find(i=>i.entry.id===current.entry.linkedEntryId)?.revision,type:current.entry.linkType,reason}),'Relationship removed; activity requires review again')}>Unlink activity</button>
 <button onClick={()=>run(async()=>{setHistory(await ledger<Audit[]>('/accounts/'+current.accountId+'/history'));},'Audit history loaded')}>Load account audit</button>
 </fieldset>}
 <button disabled={busy} onClick={()=>run(async()=>{setHistory(await ledger<Audit[]>('/history'));},'Review decision audit loaded')}>Load category, rule and dated review audit</button>
 {history.length>0&&<details open><summary>Actor, reason and before/after history</summary>{history.map(h=><article key={h.id}><h4>{h.action.replace(/_/g,' ')} · {new Date(h.recordedAt).toLocaleString()}</h4><p>Actor: {h.actorId} · {h.source}</p><p>Before: {evidence(h.beforeJson)}</p><p>After: {evidence(h.afterJson)}</p><details><summary>Full audit evidence</summary><pre style={{whiteSpace:'pre-wrap',overflowWrap:'anywhere'}}>Before: {h.beforeJson}{'\n'}After: {h.afterJson}</pre></details></article>)}</details>}
 <details><summary>Categories, subcategories and monthly budgets</summary><fieldset disabled={busy}><legend>Maintain local categories</legend><label>Edit category<select value={editCategory} onChange={e=>categoryEdit(categories.find(c=>c.id===e.target.value))}><option value="">New category</option>{categories.map(c=><option key={c.id} value={c.id}>{c.name}{c.retired?' (retired)':''}</option>)}</select></label>
 <label>Category name<input value={name} maxLength={80} onChange={e=>setName(e.target.value)}/></label><p>Use “Interest &amp; Fees” for interest, account charges and card fees.</p>
 <label>Parent category<select value={parent} onChange={e=>setParent(e.target.value)}><option value="">Top level</option>{categories.filter(c=>!c.parentId&&!c.retired&&c.id!==editCategory).map(c=><option key={c.id} value={c.id}>{c.name}</option>)}</select></label>
 <label>Monthly budget (€)<input value={budget} onChange={e=>setBudget(e.target.value)}/></label>
 <label><input type="checkbox" checked={retired} onChange={e=>setRetired(e.target.checked)}/>Retire category; keep historical references</label>
 <label>Merge into<select value={merge} onChange={e=>setMerge(e.target.value)}><option value="">Do not merge</option>{categories.filter(c=>!c.retired&&c.id!==editCategory).map(c=><option key={c.id} value={c.id}>{c.name}</option>)}</select></label>
 <label>Category change reason<input value={reason} onChange={e=>setReason(e.target.value)}/></label>
 <button disabled={!name||!reason} onClick={()=>run(()=>ledger('/review/categories'+(editCategory?'/'+editCategory:''),{revision:categories.find(c=>c.id===editCategory)?.revision??null,name,parentId:parent||null,retired,budgetEuro:budget,mergeInto:merge||null,reason},editCategory?'PUT':'POST'),'Category saved; historical IDs retained')}>Save category</button></fieldset></details>
 <details><summary>Review reusable local rules</summary><p>Rules only suggest a category. Each correction and each rule approval are separate decisions.</p>{rules.map(r=><article key={r.id}><p>{r.description} → {label(r.categoryId)} · {r.approved?'Approved':'Offered, inactive'}</p><button disabled={busy||!reason} onClick={()=>run(()=>ledger('/review/rules/'+r.id,{revision:r.revision,approved:!r.approved,reason},'PUT'),'Rule choice saved')}>{r.approved?'Disable':'Approve'} rule for {r.description}</button></article>)}<label>Rule decision reason<input value={reason} onChange={e=>setReason(e.target.value)}/></label></details>
 <section aria-label="Guided dated review"><h3>Complete household review through {date}</h3>{guide&&<><ul>{guide.guide.checks.map((c,i)=><li key={i}>{c}</li>)}</ul><p>Month net spending: {money(guide.guide.monthSpendingMinor)} · Category budget: {money(guide.guide.budgetMinor)}</p>
 <p>Use Money position to compare and reconcile each statement. Protected corrections first require password confirmation in Household access and an explicit reopening reason below.</p>
 {position?.accounts.filter(a=>a.owned&&a.reconciledDate).map(a=><p key={a.id}>{a.name} protected through {a.reconciledDate} <button disabled={busy||!reason} onClick={()=>run(()=>ledger('/accounts/'+a.id+'/reopen',{revision:a.revision,reason}),'Reconciliation reopened. Correct the source facts, then reconcile again.')}>Reopen {a.name}</button></p>)}
 <label>Review completion / reopening reason<input value={reason} maxLength={200} onChange={e=>setReason(e.target.value)}/></label>
 <label><input type="checkbox" checked={ack} onChange={e=>setAck(e.target.checked)}/>I checked source freshness, unresolved activity, statement differences, budget variance and upcoming risk</label>
 <button disabled={busy||!ack||!reason||guide.guide.unresolved>0} onClick={()=>run(()=>ledger('/review/complete',{throughDate:date,reason,acknowledged:ack,expectedAuditCount:guide.auditCount}),'Dated review recorded. Later changes remain visible and require a new review.')}>Complete dated review</button>
 <details><summary>Completed review evidence</summary>{guide.guide.completedReviews.map(r=><pre style={{whiteSpace:'pre-wrap',overflowWrap:'anywhere'}} key={r.id}>{r.payload}</pre>)}</details></>}</section>
 </section>;
}
