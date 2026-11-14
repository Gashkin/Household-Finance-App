import { useRef, useState } from 'react';

type Balance = { name: string; amount: string; asOf: string; source: string };
type Commitment = { name: string; amount: string; dueOn: string };
type Result = { liquidCashMinor: number; commitmentsMinor: number; availableCashMinor: number; warnings: string[]; calculationVersion: string };
const money = (n: number) => new Intl.NumberFormat('en-IE', { style: 'currency', currency: 'EUR' }).format(n / 100);
function cents(value: string) {
  if (!/^-?(?:0|[1-9]\d*)(?:\.\d{1,2})?$/.test(value)) throw new Error('Enter amounts in euro with at most two decimal places');
  const [whole, fraction = ''] = value.replace('-', '').split('.');
  const result = (Number(whole) * 100 + Number(fraction.padEnd(2, '0'))) * (value.startsWith('-') ? -1 : 1);
  if (!Number.isSafeInteger(result)) throw new Error('Amount is too large');
  return result;
}
export default function CashPositionWorkspace({ onUse, onInvalidate }: { onUse: (minor: number, description: string) => void; onInvalidate: () => void }) {
  const today = new Date().toLocaleDateString('en-CA');
  const [asOf, setAsOf] = useState(today);
  const [through, setThrough] = useState(today);
  const [balances, setBalances] = useState<Balance[]>([{ name: '', amount: '', asOf: today, source: '' }]);
  const [commitments, setCommitments] = useState<Commitment[]>([]);
  const [result, setResult] = useState<Result | null>(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const version = useRef(0);
  function invalidate() { version.current++; setResult(null); setError(''); setLoading(false); onInvalidate(); }
  async function calculate() {
    const current = ++version.current; setResult(null); setError(''); setLoading(true); onInvalidate();
    try {
      const response = await fetch('/api/cash-position/calculate', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ asOf, through,
        balances: balances.map(b => ({ name: b.name, balanceMinor: cents(b.amount), asOf: b.asOf, source: b.source })),
        commitments: commitments.map(c => ({ name: c.name, amountMinor: cents(c.amount), dueOn: c.dueOn })) }) });
      if (!response.ok) throw new Error(response.status === 400 ? await response.text() : 'Cash position service failed. Please retry.');
      const data: Result = await response.json(); if (current === version.current) setResult(data);
    } catch (e) { if (current === version.current) setError(e instanceof Error ? e.message : 'Unable to calculate cash position'); }
    finally { if (current === version.current) setLoading(false); }
  }
  return <section className="workspace-card cash-position">
    <h3>Build your household cash position</h3>
    <p>List liquid accounts, including negative overdraft balances, and unpaid costs due in your planning period. Exclude credit limits. Figures stay in this session.</p>
    <div className="debt-fields">
      <label className="debt-field">Position date<input type="date" value={asOf} onChange={e => { invalidate(); setAsOf(e.target.value); }} /></label>
      <label className="debt-field">Include costs through<input type="date" value={through} onChange={e => { invalidate(); setThrough(e.target.value); }} /></label>
    </div>
    <h4>Liquid balances and evidence</h4>
    {balances.map((b, i) => <fieldset key={i}><legend>Account {i + 1}</legend><div className="debt-fields">
      {(['name', 'amount', 'asOf', 'source'] as const).map(key => <label className="debt-field" key={key}>{({ name: 'Account name', amount: 'Balance (€)', asOf: 'Balance date', source: 'Source / reference' })[key]}<input type={key === 'asOf' ? 'date' : 'text'} value={b[key]} onChange={e => { invalidate(); setBalances(rows => rows.map((row, index) => index === i ? { ...row, [key]: e.target.value } : row)); }} /></label>)}
    </div><button type="button" onClick={() => { invalidate(); setBalances(rows => rows.filter((_, index) => index !== i)); }}>Remove account {i + 1}</button></fieldset>)}
    <button type="button" onClick={() => { invalidate(); setBalances(rows => [...rows, { name: '', amount: '', asOf, source: '' }]); }}>Add account</button>
    <h4>Unpaid commitments</h4>
    {commitments.map((c, i) => <fieldset key={i}><legend>Commitment {i + 1}</legend><div className="debt-fields">
      {(['name', 'amount', 'dueOn'] as const).map(key => <label className="debt-field" key={key}>{({ name: 'Description', amount: 'Amount (€)', dueOn: 'Due date' })[key]}<input type={key === 'dueOn' ? 'date' : 'text'} value={c[key]} onChange={e => { invalidate(); setCommitments(rows => rows.map((row, index) => index === i ? { ...row, [key]: e.target.value } : row)); }} /></label>)}
    </div><button type="button" onClick={() => { invalidate(); setCommitments(rows => rows.filter((_, index) => index !== i)); }}>Remove commitment {i + 1}</button></fieldset>)}
    <button type="button" onClick={() => { invalidate(); setCommitments(rows => [...rows, { name: '', amount: '', dueOn: through }]); }}>Add commitment</button>
    <p><button type="button" className="debt-primary" disabled={loading} onClick={calculate}>{loading ? 'Calculating…' : 'Calculate cash position'}</button></p>
    <div aria-live="polite">{error && <p role="alert">{error}</p>}{result && <>
      <p>Liquid cash: <strong>{money(result.liquidCashMinor)}</strong> − listed commitments: <strong>{money(result.commitmentsMinor)}</strong> = available for planning: <strong>{money(result.availableCashMinor)}</strong></p>
      {result.warnings.map(w => <p key={w}>{w}</p>)}
      <button type="button" disabled={result.availableCashMinor < 0} onClick={() => onUse(result.availableCashMinor, `Cash position ${asOf} through ${through} (${result.calculationVersion})`)}>Use available cash in debt comparison</button>
    </>}</div>
  </section>;
}
