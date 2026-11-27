import {useState} from 'react';
import {ledger} from '../api/ledger';
import {commitments} from '../api/commitments';

type Choice = {id:string; name:string};
type Result = {
  model:string; revision:number;
  advice:{categoryId:string|null; merchant:string|null; commitmentId:string|null; explanation:string};
  commitments:{revision:number; options:(Choice & {date:string})[]};
};

export default function LocalSuggestions({id, revision, applyCategory, dismissCategory, refresh}:{
  id:string; revision:number; applyCategory:(categoryId:string)=>void; dismissCategory:()=>void; refresh:()=>Promise<void>
}) {
  const [result,setResult]=useState<Result|null>(null);
  const [merchant,setMerchant]=useState(''), [match,setMatch]=useState('');
  const [busy,setBusy]=useState(false), [error,setError]=useState('');
  const path='/review/activity/'+id;
  async function request(action:()=>Promise<void>) {
    setBusy(true); setError('');
    try { await action(); }
    catch(e) {setError(e instanceof Error?e.message:'Local suggestion request failed');}
    finally {setBusy(false);}
  }
  async function suggest() {
    setResult(null);
    const next=await ledger<Result>(path+'/suggest',{revision});
    if(next.revision!==revision) throw new Error('Activity changed. Refresh review.');
    if(next.advice.categoryId) applyCategory(next.advice.categoryId);
    setMerchant(next.advice.merchant||'');
    setMatch(next.advice.commitmentId||''); setResult(next);
  }
  async function save(action:()=>Promise<unknown>) {
    await action(); setResult(null); await refresh();
  }
  return <fieldset disabled={busy}><legend>Local suggestions</legend>
    <button onClick={()=>request(suggest)}>Suggest locally</button>
    {busy&&<span role="status">Waiting for local review…</span>}
    {error&&<p role="alert">{error}</p>}
    {result&&<><p>{result.model} · {result.advice.explanation}</p>
      {result.advice.merchant ? <><label>Suggested merchant<input value={merchant} maxLength={80} onChange={e=>setMerchant(e.target.value)}/></label>
        <button disabled={!merchant.trim()} onClick={()=>request(()=>save(()=>ledger(path+'/merchant',{revision,label:merchant},'PUT')))}>Save merchant label</button></>
        : <p>No merchant recommendation.</p>}
      {result.advice.commitmentId ? <><label>Suggested commitment<select value={match} onChange={e=>setMatch(e.target.value)}>
        {result.commitments.options.map(o=><option key={o.id} value={o.id}>{o.name} · {o.date}</option>)}</select></label>
        <button onClick={()=>request(()=>save(()=>commitments('/occurrence',{revision:result.commitments.revision,
          operationId:crypto.randomUUID(),reason:'Owner reviewed '+result.model+' commitment suggestion',occurrenceId:match,
          entryId:id,action:'MATCH',accountRevision:revision})))}>Confirm commitment match</button></>
        : <p>No commitment recommendation.</p>}
      <button onClick={()=>{dismissCategory();setResult(null);}}>Dismiss suggestions</button>
    </>}
  </fieldset>;
}
