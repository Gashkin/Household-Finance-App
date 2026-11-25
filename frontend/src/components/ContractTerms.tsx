import { useEffect, useState } from 'react';
import { contracts, type Evidence, type LinkOptions, type Option, type Price, type References, type Terms } from '../api/contracts';

export const dateFields = {
  termEnd: 'Term end',
  notice: 'Notice deadline',
  promotion: 'Promotional expiry',
  priceChange: 'Price-change date',
  nextCharge: 'Next charge',
} as const;

export const priceFields = {
  promotionalPrice: 'Promotional price',
  standardPrice: 'Standard price',
  exitFee: 'Early-exit fee',
} as const;

export function label(value: string) {
  const words = value.toLowerCase().replace(/_/g, ' ');
  return words.charAt(0).toUpperCase() + words.slice(1);
}

export function emptyTerms(today: string, provider = ''): Terms {
  const unknown: Evidence = { certainty: 'UNKNOWN', date: null, source: null, note: null };
  return {
    provider, plan: null, type: 'ROLLING', start: today, reviewEnabled: true,
    termEnd: unknown, notice: unknown, promotion: unknown, priceChange: unknown, nextCharge: unknown,
    reviewTarget: null, reviewSource: null, reviewNote: null, frequency: null,
    promotionalPrice: null, standardPrice: null, exitFee: null, noticeTerms: null, references: null,
  };
}

function DateEditor({ title, value, onChange }: { title: string; value: Evidence; onChange: (next: Evidence) => void }) {
  return <fieldset className="contract-evidence">
    <legend>{title}</legend>
    <label>Certainty<select value={value.certainty} onChange={event => {
      const certainty = event.target.value as Evidence['certainty'];
      onChange({ ...value, certainty, date: certainty === 'UNKNOWN' ? null : value.date });
    }}>
      <option value="UNKNOWN">Unknown</option><option value="ESTIMATED">Estimated</option><option value="VERIFIED">Verified</option>
    </select></label>
    {value.certainty !== 'UNKNOWN' && <label>Date<input type="date" required value={value.date ?? ''}
      onChange={event => onChange({ ...value, date: event.target.value || null })} /></label>}
    <label>Source<input maxLength={180} required={value.certainty === 'VERIFIED'} value={value.source ?? ''}
      onChange={event => onChange({ ...value, source: event.target.value || null })} /></label>
    <label>Verification note<input maxLength={500} value={value.note ?? ''}
      onChange={event => onChange({ ...value, note: event.target.value || null })} /></label>
  </fieldset>;
}

function PriceEditor({ title, value, onChange }: { title: string; value: Price | null; onChange: (next: Price | null) => void }) {
  return <fieldset className="contract-evidence"><legend>{title}</legend>
    <label className="contract-check"><input type="checkbox" checked={value !== null}
      onChange={event => onChange(event.target.checked ? { amount: '', currency: 'EUR', effectiveFrom: null } : null)} /> Record {title.toLowerCase()}</label>
    {value && <>
      <label>Amount<input inputMode="decimal" required pattern="[0-9]{1,9}([.][0-9]{1,4})?" value={value.amount}
        onChange={event => onChange({ ...value, amount: event.target.value })} /></label>
      <label>Currency<input required minLength={3} maxLength={3} pattern="[A-Z]{3}" value={value.currency}
        onChange={event => onChange({ ...value, currency: event.target.value.toUpperCase() })} /></label>
      <label>Effective from<input type="date" value={value.effectiveFrom ?? ''}
        onChange={event => onChange({ ...value, effectiveFrom: event.target.value || null })} /></label>
    </>}
  </fieldset>;
}

function OptionList({ options, selected }: { options: Option[]; selected: string | null }) {
  return <>
    {selected && !options.some(option => option.id === selected) && <option value={selected}>Previously linked reference</option>}
    {options.map(option => <option key={option.id} value={option.id}>{option.label}</option>)}
  </>;
}

function ReferenceEditor({ value, onChange }: { value: References | null; onChange: (next: References) => void }) {
  const references = value ?? { billId: null, accountId: null, transactionId: null };
  const accountId = references.accountId;
  const [options, setOptions] = useState<LinkOptions>({ bills: [], accounts: [] });
  const [transactions, setTransactions] = useState<Option[]>([]);
  const [optionsError, setOptionsError] = useState('');
  const [transactionError, setTransactionError] = useState('');
  useEffect(() => {
    let current = true;
    void contracts<LinkOptions>('/links').then(data => { if (current) setOptions(data); })
      .catch(() => { if (current) setOptionsError('Bill and account options are unavailable.'); });
    return () => { current = false; };
  }, []);
  useEffect(() => {
    setTransactions([]);
    setTransactionError('');
    if (!accountId) return;
    let current = true;
    void contracts<Option[]>('/links/' + encodeURIComponent(accountId) + '/transactions')
      .then(data => { if (current) setTransactions(data); })
      .catch(() => { if (current) setTransactionError('Transaction options are unavailable for this account.'); });
    return () => { current = false; };
  }, [accountId]);
  return <>
    <p>References stay private to the contract owner. Use Plan ahead to preview and apply a bill change.</p>
    {optionsError && <p role="status">{optionsError}</p>}
    {transactionError && <p role="status">{transactionError}</p>}
    <div className="contract-fields">
      <label>Recurring bill<select value={references.billId ?? ''} onChange={event => onChange({ ...references, billId: event.target.value || null })}>
        <option value="">No linked bill</option><OptionList options={options.bills} selected={references.billId} />
      </select></label>
      <label>Transaction account<select value={accountId ?? ''} onChange={event => onChange({
        ...references, accountId: event.target.value || null, transactionId: null,
      })}><option value="">No linked transaction</option><OptionList options={options.accounts} selected={accountId} /></select></label>
      {accountId && <label>Transaction<select required value={references.transactionId ?? ''} onChange={event =>
        onChange({ ...references, transactionId: event.target.value || null })}>
        <option value="">Choose a transaction</option><OptionList options={transactions} selected={references.transactionId} />
      </select></label>}
    </div>
  </>;
}

export function TermsEditor({ value, onChange, fixedStart = false }: {
  value: Terms; onChange: (value: Terms) => void; fixedStart?: boolean;
}) {
  const [showReferences, setShowReferences] = useState(false);
  return <>
    <div className="contract-fields">
      <label>Provider<input required maxLength={120} value={value.provider}
        onChange={event => onChange({ ...value, provider: event.target.value })} /></label>
      <label>Plan<input maxLength={120} value={value.plan ?? ''}
        onChange={event => onChange({ ...value, plan: event.target.value || null })} /></label>
      <label>Term type<select value={value.type} onChange={event => onChange({ ...value, type: event.target.value as Terms['type'] })}>
        <option value="ROLLING">Rolling</option><option value="FIXED">Fixed</option>
      </select></label>
      <label>Term start<input type="date" required readOnly={fixedStart} value={value.start}
        onChange={event => onChange({ ...value, start: event.target.value })} /></label>
      <label>Billing frequency<select value={value.frequency ?? ''} onChange={event => onChange({ ...value, frequency: event.target.value || null })}>
        <option value="">Unknown</option>
        {['WEEKLY', 'MONTHLY', 'QUARTERLY', 'YEARLY', 'ONE_OFF'].map(frequency => <option key={frequency} value={frequency}>{label(frequency)}</option>)}
      </select></label>
    </div>
    <label className="contract-check"><input type="checkbox" checked={value.reviewEnabled}
      onChange={event => onChange({ ...value, reviewEnabled: event.target.checked })} /> Review this term</label>
    {value.reviewEnabled && <div className="contract-fields">
      <label>Review target<input type="date" value={value.reviewTarget ?? ''}
        onChange={event => onChange({ ...value, reviewTarget: event.target.value || null })} /></label>
      <label>Review source<input maxLength={180} value={value.reviewSource ?? ''}
        onChange={event => onChange({ ...value, reviewSource: event.target.value || null })} /></label>
      <label>Review note<input maxLength={500} value={value.reviewNote ?? ''}
        onChange={event => onChange({ ...value, reviewNote: event.target.value || null })} /></label>
    </div>}
    {value.reviewEnabled && <p>Leave the review target empty to use the date suggested from verified deadlines.</p>}
    <details>
      <summary>Dates and evidence</summary>
      <div className="contract-fields">{Object.entries(dateFields).map(([key, title]) => {
        const field = key as keyof typeof dateFields;
        return <DateEditor key={field} title={title} value={value[field]} onChange={next => onChange({ ...value, [field]: next })} />;
      })}</div>
      <label>Notice terms<textarea maxLength={1000} value={value.noticeTerms ?? ''}
        onChange={event => onChange({ ...value, noticeTerms: event.target.value || null })} /></label>
    </details>
    <details>
      <summary>Prices and early-exit fee</summary>
      <div className="contract-fields">{Object.entries(priceFields).map(([key, title]) => {
        const field = key as keyof typeof priceFields;
        return <PriceEditor key={field} title={title} value={value[field]} onChange={next => onChange({ ...value, [field]: next })} />;
      })}</div>
    </details>
    <details onToggle={event => { if (event.currentTarget.open) setShowReferences(true); }}>
      <summary>Linked bill or transaction</summary>
      {showReferences && <ReferenceEditor value={value.references} onChange={next => onChange({ ...value, references: next })} />}
    </details>
  </>;
}
