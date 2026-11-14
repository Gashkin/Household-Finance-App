import { useEffect, useState, type FormEvent } from 'react';
import { getLatestCashPosition, saveCashPosition, type CashLine, type CashPositionSnapshot } from '../api/cashPosition';
import { money, parseMoney } from '../domain/money';
import { todayInDublin } from '../domain/dates';

type DraftLine = { label: string; amount: string };
type LineListProps = {
  title: string;
  entries: DraftLine[];
  update: (next: DraftLine[]) => void;
  canBeEmpty?: boolean;
};

function LineEditor({ title, entries, update, canBeEmpty = false }: LineListProps) {
  return (
    <fieldset className="cash-lines">
      <legend>{title}</legend>
      {entries.map((entry, index) => (
        <div className="cash-line" key={index}>
          <input
            aria-label={title + ' entry ' + (index + 1) + ' name'}
            placeholder={canBeEmpty ? 'e.g. next rent payment' : 'e.g. current account'}
            maxLength={100}
            value={entry.label}
            onChange={event => update(entries.map((line, i) => i === index ? { ...line, label: event.target.value } : line))}
          />
          <input
            aria-label={title + ' entry ' + (index + 1) + ' euros'}
            inputMode="decimal"
            placeholder="€ amount"
            value={entry.amount}
            onChange={event => update(entries.map((line, i) => i === index ? { ...line, amount: event.target.value } : line))}
          />
          <button type="button" className="cash-secondary" aria-label={'Remove ' + title + ' entry ' + (index + 1)}
            disabled={!canBeEmpty && entries.length === 1}
            onClick={() => update(entries.filter((_, i) => i !== index))}>Remove</button>
        </div>
      ))}
      <button type="button" className="cash-secondary" disabled={entries.length >= 30}
        onClick={() => update([...entries, { label: '', amount: '' }])}>+ Add {canBeEmpty ? 'commitment' : 'cash account'}</button>
    </fieldset>
  );
}

function summaryLines(title: string, lines: CashLine[]) {
  return lines.length > 0 ? (
    <div className="cash-line-summary">
      <h4>{title}</h4>
      <ul>{lines.map((line, index) => <li key={index}>{line.label}: {money(line.amountMinor)}</li>)}</ul>
    </div>
  ) : null;
}

export default function CashPositionPanel({ onApply, appliedId }: {
  onApply: (snapshot: CashPositionSnapshot) => void;
  appliedId: string | null;
}) {
  const [latest, setLatest] = useState<CashPositionSnapshot | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [asOfDate, setAsOfDate] = useState(todayInDublin);
  const [sourceNote, setSourceNote] = useState('');
  const [verification, setVerification] = useState<'REVIEWED' | 'ESTIMATED'>('ESTIMATED');
  const [balances, setBalances] = useState<DraftLine[]>([{ label: '', amount: '' }]);
  const [commitments, setCommitments] = useState<DraftLine[]>([]);
  const [reserve, setReserve] = useState('');

  useEffect(() => {
    let active = true;
    getLatestCashPosition().then(snapshot => { if (active) setLatest(snapshot); })
      .catch(cause => { if (active) setError(cause instanceof Error ? cause.message : 'Unable to load cash position'); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, []);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError('');
    setNotice('');
    const convert = (lines: DraftLine[], name: string): CashLine[] => lines.map((line, index) => ({
      label: line.label.trim(),
      amountMinor: parseMoney(line.amount, name + ' ' + (index + 1)),
    }));
    try {
      const input = {
        asOfDate, sourceNote: sourceNote.trim(), verification,
        cashBalances: convert(balances, 'Cash account'),
        upcomingCommitments: convert(commitments, 'Commitment'),
        minimumReserveMinor: parseMoney(reserve, 'Protected reserve'),
      };
      setSaving(true);
      const saved = await saveCashPosition(input);
      setLatest(saved);
      setNotice('Cash evidence saved as a new dated snapshot. Apply it explicitly to update the debt comparison.');
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Unable to save cash evidence');
    } finally {
      setSaving(false);
    }
  }

  return (
    <section className="workspace-card cash-position">
      <div className="cash-heading">
        <div>
          <p className="debt-eyebrow">Reusable household evidence</p>
          <h3>Cash position</h3>
          <p className="debt-caption">Record actual cash accounts and bills due in the next 30 days once, then use the resulting liquidity in a debt decision. Nothing is imported from the older sample planner balances.</p>
        </div>
      </div>
      {loading ? <p role="status">Loading saved cash position…</p> : null}
      {latest ? (
        <div className="cash-snapshot">
          <div className="cash-overview">
            <div><span>Total recorded cash</span><strong>{money(latest.liquidCashMinor)}</strong></div>
            <div><span>Next 30 days' commitments</span><strong>{money(latest.committedMinor)}</strong></div>
            <div><span>Cash after commitments</span><strong>{money(latest.availableAfterCommitmentsMinor)}</strong></div>
            <div><span>Protected reserve</span><strong>{money(latest.minimumReserveMinor)}</strong></div>
            <div><span>Beyond reserve</span><strong>{money(latest.discretionaryAfterReserveMinor)}</strong></div>
          </div>
          <p className="debt-caption">As at {latest.asOfDate} · {latest.verification === 'REVIEWED' ? 'Manually reviewed' : 'Estimated'} · Source: {latest.sourceNote}. This is self-reported evidence, not bank-verified.</p>
          <div className="cash-breakdown">
            {summaryLines('Cash accounts', latest.cashBalances)}
            {summaryLines('Upcoming commitments', latest.upcomingCommitments)}
          </div>
          {latest.warnings.map(warning => <p className="cash-warning" key={warning}>{warning}</p>)}
          <button type="button" className="debt-primary cash-apply"
            disabled={latest.availableAfterCommitmentsMinor < 0 || appliedId === latest.id}
            onClick={() => onApply(latest)}>
            {appliedId === latest.id ? 'Cash position applied' : 'Use this cash position in debt comparison'}
          </button>
          {latest.availableAfterCommitmentsMinor < 0 ? <p className="debt-caption">Cannot apply a negative available cash position to a repayment scenario.</p> : null}
        </div>
      ) : !loading ? <p className="debt-caption">No saved cash position yet. Record your accounts and commitments below to create one.</p> : null}
      <details className="cash-editor" key={latest?.id ?? 'empty'} open={!latest}>
        <summary>{latest ? 'Record a new cash position' : 'Record cash evidence'}</summary>
        <form onSubmit={submit}>
          <div className="cash-meta">
            <label>Balance date
              <input type="date" value={asOfDate} max={todayInDublin()} required onChange={event => setAsOfDate(event.target.value)}/>
            </label>
            <label>Evidence status
              <select value={verification} onChange={event => setVerification(event.target.value as 'REVIEWED' | 'ESTIMATED')}>
                <option value="ESTIMATED">Estimate / not checked</option>
                <option value="REVIEWED">Manually reviewed against a source</option>
              </select>
            </label>
            <label>Evidence source or reference
              <input maxLength={200} required value={sourceNote} placeholder="e.g. bank app balances, 14 Nov"
                onChange={event => setSourceNote(event.target.value)}/>
            </label>
            <label>Protected reserve (€)
              <input inputMode="decimal" value={reserve} required placeholder="e.g. 4000"
                onChange={event => setReserve(event.target.value)}/>
            </label>
          </div>
          <div className="cash-line-editors">
            <LineEditor title="Cash accounts" entries={balances} update={setBalances}/>
            <LineEditor title="Upcoming commitments" entries={commitments} update={setCommitments} canBeEmpty/>
          </div>
          <p className="debt-caption">Include commitments due in the next 30 days after the balance date. Don't subtract bills already reflected in account balances. Snapshots are append-only; scenarios remain separate.</p>
          <button type="submit" disabled={saving} className="debt-primary cash-save">{saving ? 'Saving…' : 'Save cash position'}</button>
        </form>
      </details>
      {error ? <p role="alert" className="debt-alert">{error}</p> : null}
      {notice ? <p role="status" className="cash-notice">{notice}</p> : null}
    </section>
  );
}
