import { useState, type FormEvent } from 'react';
import { compareDebtScenario, type DebtScenarioInput, type DebtScenarioResult } from '../api/debtScenarios';

const euro = new Intl.NumberFormat('en-IE', { style: 'currency', currency: 'EUR' });
const money = (minor: number) => euro.format(minor / 100);
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

function parseMoney(value: string, label: string): number {
  if (!/^(?:0|[1-9]\d*)(?:\.\d{1,2})?$/.test(value)) throw new RangeError(`${label} must be a non-negative euro amount with up to two decimals`);
  const [whole, fraction = ''] = value.split('.');
  const cents = Number(whole) * 100 + Number(fraction.padEnd(2, '0'));
  if (!Number.isSafeInteger(cents)) throw new RangeError(`${label} is too large`);
  return cents;
}

export default function DebtDecisionWorkspace() {
  const [values, setValues] = useState(defaults);
  const [result, setResult] = useState<DebtScenarioResult | null>(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [showAssumptions, setShowAssumptions] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError('');
    setResult(null);
    try {
      const rate = Number(values.rate);
      if (!/^\\d+(?:\\.\\d+)?$/.test(values.rate) || !Number.isFinite(rate))
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
      setResult(await compareDebtScenario(input));
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Unable to calculate scenario');
    } finally {
      setLoading(false);
    }
  }

  return (
    <section className="debt-decision">
      <div className="debt-intro">
        <p className="debt-eyebrow">Household decision workspace</p>
        <h2>Repay debt or keep the cash?</h2>
        <p>Compare the cost of keeping your repayment schedule with making an extra payment today. Figures are illustrative and stay in this browser session.</p>
      </div>
      <div className="debt-layout">
        <form className="workspace-card debt-form" onSubmit={submit}>
          <h3>Model your decision</h3>
          <div className="debt-fields">
            {fields.map(({ key, label, hint }) => (
              <label key={key} className="debt-field">
                <span>{label}</span>
                <small>{hint}</small>
                <input inputMode="decimal" value={values[key]} onChange={(event) => setValues(current => ({ ...current, [key]: event.target.value }))} aria-label={label} />
              </label>
            ))}
          </div>
          <button className="debt-primary" type="submit" disabled={loading}>{loading ? "Comparing…" : "Compare scenarios"}</button>
          <p className="debt-caption">No bank connection, saved records or payment instructions.</p>
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
