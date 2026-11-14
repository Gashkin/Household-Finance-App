import { useEffect, useRef, useState } from 'react';
import { approveIntake, intakeRequest, type IntakeCapabilities, type IntakeDraft, type ReviewedFact } from '../api/evidenceIntake';
import type { CashPositionSnapshot } from '../api/cashPosition';
import { todayInDublin } from '../domain/dates';

export default function EvidenceIntakePanel({ onApproved }: { onApproved: (snapshot: CashPositionSnapshot) => void }) {
  const [capabilities, setCapabilities] = useState<IntakeCapabilities | null>(null);
  const [accessKey, setAccessKey] = useState('');
  const [unlocked, setUnlocked] = useState(false);
  const [source, setSource] = useState('');
  const [reference, setReference] = useState('');
  const [allowModel, setAllowModel] = useState(false);
  const [draft, setDraft] = useState<IntakeDraft | null>(null);
  const [rows, setRows] = useState<ReviewedFact[]>([]);
  const [date, setDate] = useState(todayInDublin);
  const [reserve, setReserve] = useState('');
  const [acknowledged, setAcknowledged] = useState(false);
  const [busy, setBusy] = useState('');
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const version = useRef(0);
  useEffect(() => {
    let active = true;
    intakeRequest<IntakeCapabilities>('/capabilities', '').then(value => { if (active) setCapabilities(value); })
      .catch(() => { if (active) setError('Evidence intake capabilities could not be loaded.'); });
    return () => { active = false; version.current++; };
  }, []);
  function discard(id: string, key = accessKey) { void intakeRequest<void>('/drafts/' + encodeURIComponent(id), key, 'DELETE').catch(() => {}); }
  function invalidate() {
    version.current++; if (draft) discard(draft.id);
    setDraft(null); setRows([]); setAcknowledged(false); setError(''); setNotice(''); setBusy('');
  }
  async function unlock() {
    setError(''); setBusy('unlock');
    try { await intakeRequest<void>('/session', accessKey); setUnlocked(true); }
    catch (e) { setError(e instanceof Error ? e.message : 'Unable to unlock intake'); }
    finally { setBusy(''); }
  }
  function lock() { invalidate(); setUnlocked(false); setAccessKey(''); setSource(''); setReference(''); setReserve(''); setAllowModel(false); }
  async function extract() {
    if (draft) discard(draft.id);
    const current = ++version.current; const key = accessKey;
    setDraft(null); setRows([]); setAcknowledged(false); setNotice(''); setError(''); setBusy('extract');
    try {
      const response = await intakeRequest<IntakeDraft>('/drafts', key, 'POST', { source, reference, allowModel });
      if (current !== version.current) { discard(response.id, key); return; }
      setDraft(response); setRows(response.proposals.map(({ lineNumber, kind, label, amountEuro }) => ({ lineNumber, kind, label, amountEuro })));
    } catch (e) { if (current === version.current) setError(e instanceof Error ? e.message : 'Unable to extract evidence'); }
    finally { if (current === version.current) setBusy(''); }
  }
  function update(index: number, values: Partial<ReviewedFact>) { setRows(current => current.map((row, i) => i === index ? { ...row, ...values } : row)); setAcknowledged(false); }
  async function approve() {
    if (!draft) return;
    const current = ++version.current; setBusy('approve'); setError('');
    try {
      const snapshot = await approveIntake(draft.id, accessKey, { asOfDate: date, minimumReserveEuro: reserve, acknowledged, rows });
      if (current !== version.current) return;
      onApproved(snapshot); setDraft(null); setRows([]); setSource(''); setReference(''); setAcknowledged(false);
      setNotice('Reviewed cash position saved and applied to the debt comparison. The source excerpt was discarded.');
    } catch (e) { if (current === version.current) setError(e instanceof Error ? e.message : 'Unable to approve intake'); }
    finally { if (current === version.current) setBusy(''); }
  }
  const lines = source.split(/\r\n|[\n\r\v\f\u0085\u2028\u2029]/);
  return <details className="workspace-card evidence-intake">
    <summary>Paste evidence and review suggested facts</summary>
    <h3>From source text to a reviewed cash position</h3>
    <p>Review every suggestion before saving. Excerpts stay in memory for at most 10 minutes and are discarded on approval. Only approved facts and your source reference are stored.</p>
    {!capabilities ? <p>Checking intake availability…</p> : !capabilities.enabled ? <p>Evidence intake is not enabled on this instance. Its owner must configure an intake access key.</p> : !unlocked ? <div className="intake-unlock">
      <label>Intake access key<input type="password" autoComplete="off" value={accessKey} onChange={e => setAccessKey(e.target.value)} /></label>
      <button type="button" className="debt-primary" disabled={!!busy || !accessKey} onClick={unlock}>Unlock evidence intake</button>
    </div> : <>
      <button type="button" className="cash-secondary" onClick={lock} disabled={busy === 'approve'}>Lock and clear excerpt</button>
      <div className="intake-source-fields">
        <label>Evidence reference<input maxLength={100} value={reference} disabled={busy === 'approve'} placeholder="e.g. Bank statement, 14 November" onChange={e => { invalidate(); setReference(e.target.value); }} /></label>
        <label>Source excerpt<textarea rows={6} maxLength={4000} value={source} disabled={busy === 'approve'} placeholder={'Current account: EUR 1,000.00\nElectricity bill due: EUR 100.00'} onChange={e => { invalidate(); setSource(e.target.value); }} /></label>
      </div>
      {capabilities.modelConfigured ? <label className="intake-check"><input type="checkbox" checked={allowModel} disabled={busy === 'approve'} onChange={e => { invalidate(); setAllowModel(e.target.checked); }} />Send this excerpt to the configured model for assistance. Common identifiers are redacted, but remove personal details yourself. Provider charges may apply; up to {capabilities.modelDailyLimit} requests per day on this running instance, at most {capabilities.maxOutputTokens} output tokens each.</label>
        : <p className="debt-caption">Local suggestions are available without a model or subscription. No source text is sent to a provider.</p>}
      <button type="button" className="debt-primary" disabled={!!busy || !source.trim() || !reference.trim()} onClick={extract}>{busy === 'extract' ? 'Preparing suggestions…' : 'Review suggestions'}</button>
      {draft && <section aria-label="Review evidence suggestions">
        <p>{draft.method === 'MODEL' ? 'Model-assisted' : 'Local'} suggestions · Draft expires {new Date(draft.expiresAt).toLocaleTimeString('en-IE')}</p>
        {draft.warnings.map(w => <p className="intake-warning" key={w}>{w}</p>)}
        <div className="intake-review-layout">
          <div><h4>Original passage</h4><pre className="intake-passage">{lines.map((line, index) => `${index + 1}: ${line}`).join('\n')}</pre></div>
          <fieldset disabled={busy === 'approve'} className="intake-facts"><legend>Correct and approve facts</legend>
            {rows.map((row, index) => <div className="intake-fact" key={index}>
              <blockquote>{lines[row.lineNumber - 1]}</blockquote>
              <p className="debt-caption">Suggested or manually corrected. Check against the quoted line; no automatic verification of meaning.</p>
              <label>Source line {index + 1}<select value={row.lineNumber} onChange={e => update(index, { lineNumber: Number(e.target.value) })}>{lines.map((_, i) => <option key={i} value={i + 1}>Line {i + 1}</option>)}</select></label>
              <label>Fact type {index + 1}<select value={row.kind} onChange={e => update(index, { kind: e.target.value as ReviewedFact['kind'] })}><option value="CASH">Liquid cash account</option><option value="COMMITMENT">Unpaid commitment</option></select></label>
              <label>Fact label {index + 1}<input maxLength={100} value={row.label} onChange={e => update(index, { label: e.target.value })} /></label>
              <label>Fact amount {index + 1} (€)<input inputMode="decimal" value={row.amountEuro} onChange={e => update(index, { amountEuro: e.target.value })} /></label>
              <button type="button" className="cash-secondary" onClick={() => { setRows(current => current.filter((_, i) => i !== index)); setAcknowledged(false); }}>Remove fact {index + 1}</button>
            </div>)}
            <button type="button" className="cash-secondary" disabled={rows.length >= 30} onClick={() => { setRows(current => [...current, { lineNumber: 1, kind: 'CASH', label: '', amountEuro: '' }]); setAcknowledged(false); }}>Add a source-linked fact</button>
          </fieldset>
        </div>
        <div className="intake-source-fields">
          <label>Evidence balance date<input type="date" max={todayInDublin()} value={date} disabled={busy === 'approve'} onChange={e => { setDate(e.target.value); setAcknowledged(false); }} /></label>
          <label>Intake protected reserve (€)<input inputMode="decimal" value={reserve} disabled={busy === 'approve'} onChange={e => { setReserve(e.target.value); setAcknowledged(false); }} /></label>
        </div>
        <label className="intake-check"><input type="checkbox" checked={acknowledged} disabled={busy === 'approve'} onChange={e => setAcknowledged(e.target.checked)} />I checked these facts against the source, excluded credit limits and already-paid bills, and confirm the balance date and reserve. Save the corrected cash position.</label>
        <button type="button" className="debt-primary" disabled={!!busy || !acknowledged || rows.length === 0} onClick={approve}>{busy === 'approve' ? 'Saving reviewed facts…' : 'Approve and use cash position'}</button>
      </section>}
    </>}
    {error && <p role="alert" className="debt-alert">{error}</p>}
    {notice && <p role="status" className="cash-notice">{notice}</p>}
  </details>;
}
