import { useRef, useState, type FormEvent } from 'react';
import { compareDebtScenario, type DebtScenarioInput, type DebtScenarioResult } from '../api/debtScenarios';
import type { CashPositionSnapshot } from '../api/cashPosition';
import CashPositionPanel from './CashPositionPanel';
import { minorToInput, money, parseMoney } from '../domain/money';

const defaults = { balance: '10000', rate: '8', payment: '300', extra: '2000', cash: '8000', reserve: '4000' };
type Field = keyof typeof defaults;
const fields: { key: Field; label: string; hint: string }[] = [
  { key: 'balance', label: 'Debt outstanding (€)', hint: 'Current principal' },
  { key: 'rate', label: 'Annual interest rate (%)', hint: 'Assumed fixed rate' },
  { key: 'payment', label: 'Monthly repayment (€)', hint: 'Regular payment' },
  { key: 'extra', label: 'Extra repayment now (€)', hint: 'One-off payment' },
  { key: 'cash', label: 'Available cash (€)', hint: 'Liquid cash before extra payment' },
  { key: 'reserve', label: 'Minimum cash reserve (€)', hint: 'Your protected cash floor' },
];

export default function DebtDecisionWorkspace() {
  const [values, setValues] = useState(defaults);
  const [sourceSnapshot, setSourceSnapshot] = useState<CashPositionSnapshot | null>(null);
  const requestVersion = useRef(0);
  const [result, setResult] = useState<DebtScenarioResult | null>(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [showAssumptions, setShowAssumptions] = useState(false);

  function applyCashPosition(snapshot: CashPositionSnapshot) {
    if (snapshot.availableAfterCommitmentsMinor < 0) return;
    requestVersion.current++;
    setResult(null);
    setLoading(false);
    setError('');
    setSourceSnapshot(snapshot);
    setValues(current => ({
      ...current,
      cash: minorToInput(snapshot.availableAfterCommitmentsMinor),
      reserve: minorToInput(snapshot.minimumReserveMinor),
    }));
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError('');
    setResult(null);
    const version = ++requestVersion.current;
    try {
      const rate = Number(values.rate);
      if (!/^\d+(?:\.\d+)?$/.test(values.rate) || !Number.isFinite(rate))
        throw new RangeError('Annual interest rate must be a non-negative number');
      const input: DebtScenarioInput = {
        balanceMinor: parseMoney(values.balance, 'Debt outstanding'),
        annualRatePercent: rate,
        monthlyPaymentMinor: parseMoney(values.payment, 'Monthly repayment'),
        extraPaymentMinor: parseMoney(values.extra, 'Extra repayment'),
        liquidCashMinor: parseMoney(values.cash, 'Available cash'),
        minimumReserveMinor: parseMoney(values.reserve, 'Minimum cash reserve'),
      };
      setLoading(true);
      const response = await compareDebtScenario(input);
      if (version === requestVersion.current) setResult(response);
    } catch (e) {
      if (version === requestVersion.current) setError(e instanceof Error ? e.message : 'Unable to calculate scenario');
    } finally {
      if (version === requestVersion.current) setLoading(false);
    }
  }

  return (
    <section className="debt-decision">
      <div className="debt-intro">
        <p className="debt-eyebrow">Household decision workspace</p>
        <h2>Repay debt or keep the cash?</h2>
        <p>Compare the cost of keeping your repayment schedule with making an extra payment today. Debt comparisons are illustrative and not saved. Your optional cash evidence is stored in the local backend.</p>
      </div>
      <CashPositionPanel onApply={applyCashPosition} appliedId={sourceSnapshot?.id ?? null} />
      <div className="debt-layout">
        <form className="workspace-card debt-form" onSubmit={submit}>
          <h3>Model your decision</h3>
          <div className="debt-fields">
            {fields.map(({ key, label, hint }) => (
              <label key={key} className="debt-field">
                <span>{label}</span>
                <small>{hint}</small>
                <input inputMode="decimal" value={values[key]} onChange={(event) => { requestVersion.current++; setResult(null); setError(''); setLoading(false); if (key === 'cash' || key === 'reserve') setSourceSnapshot(null); setValues(current => ({ ...current, [key]: event.target.value })); }} aria-label={label} />
              </label>
            ))}
          </div>
          <button className="debt-primary" type="submit" disabled={loading}>{loading ? "Comparing…" : "Compare scenarios"}</button>
          {sourceSnapshot ? <p className="cash-source-note">Using cash position dated {sourceSnapshot.asOfDate} ({sourceSnapshot.verification === 'REVIEWED' ? 'manually reviewed' : 'estimated'}). Cash is net of recorded commitments. {sourceSnapshot.stale ? 'Evidence is older than 30 days.' : ''}</p> : <p className="debt-caption">Cash and reserve values are manual or illustrative until you apply a recorded position.</p>}
          <p className="debt-caption">Comparisons are not saved and no payment is initiated.</p>
        </form>
        <div className="debt-results" aria-live="polite">
          {error ? <div role="alert" className="debt-alert">{error}</div> : null}
          {result ? (
            <>
              <div className="debt-metrics">
                <article className="workspace-card"><span>Interest avoided</span><strong>{money(result.interestSavedMinor)}</strong></article>
                <article className="workspace-card"><span>Repayment time saved</span><strong>{result.monthsSaved} months</strong></article>
                <article className="workspace-card"><span>Cash after extra repayment</span><strong>{money(result.remainingCashMinor)}</strong></article>
              </div>
              <div className="workspace-card">
                <h3>Side-by-side comparison</h3>
                <div className="debt-table-wrap">
                  <table className="debt-table"><thead><tr><th>Measure</th><th>Keep schedule</th><th>Extra repayment</th></tr></thead>
                    <tbody>
                      <tr><th>Months to repay</th><td>{result.baseline.months}</td><td>{result.withExtraPayment.months}</td></tr>
                      <tr><th>Future interest</th><td>{money(result.baseline.interestMinor)}</td><td>{money(result.withExtraPayment.interestMinor)}</td></tr>
                      <tr><th>Future regular payments</th><td>{money(result.baseline.totalPaidMinor)}</td><td>{money(result.withExtraPayment.totalPaidMinor)}</td></tr>
                    </tbody>
                  </table>
                </div>
                <p className="debt-caption">The extra repayment is paid immediately and is excluded from the “future regular payments” row.</p>
              </div>
              <div className="workspace-card">
                <h3>Cash safety check</h3>
                {result.reserveShortfallMinor > 0 ? (
                  <p role="status" className="debt-alert">Below your protected reserve by {money(result.reserveShortfallMinor)}. Consider reducing the extra repayment.</p>
                ) : <p className="debt-safe">Cash remains at or above your stated minimum reserve.</p>}
                {result.warnings.filter(w => !w.includes('reserve')).map(w => <p className="debt-alert" key={w}>{w}</p>)}
              </div>
              <div className="workspace-card">
                <button type="button" className="debt-disclosure" aria-expanded={showAssumptions} onClick={() => setShowAssumptions(x => !x)}>
                  {showAssumptions ? 'Hide' : 'Show'} calculation assumptions
                </button>
                {showAssumptions ? <ul>{result.assumptions.map(a => <li key={a}>{a}</li>)}</ul> : null}
              </div>
            </>
          ) : null}
        </div>
      </div>
    </section>
  );
}
