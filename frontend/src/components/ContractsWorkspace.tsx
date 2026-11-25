import { useEffect, useRef, useState, type FormEvent } from 'react';
import { useIdentity } from '../api/access';
import {
  ContractError, contractPath, contracts, termLink, type Assessment, type Decision, type Detail,
  type Inbox, type Kind, type Notification, type Outcome, type Summary, type TermView,
} from '../api/contracts';
import { dateFields, emptyTerms, label, priceFields, TermsEditor } from './ContractTerms';

const decisions: Decision[] = ['UNDECIDED', 'KEEP', 'RENEW', 'SWITCH', 'CANCEL'];
const outcomes: Outcome[] = ['RENEWED', 'SWITCHED', 'CANCELLED'];
function last<T>(items: T[]): T | undefined { return items[items.length - 1]; }

function newRequestId() {
  return Array.from(crypto.getRandomValues(new Uint8Array(16)), byte => byte.toString(16).padStart(2, '0')).join('');
}

const defaultAssessment: Assessment = { usage: 'UNKNOWN', closing: 'UNDECIDED', notes: null };

type Mutation = (path: string, body: unknown, method?: string) => Promise<void>;
type Mode = 'edit' | 'decision' | 'attempt' | 'confirmation' | 'reactivate';

function ContractForm({ detail, today, save, back }: { detail?: Detail; today: string; save: Mutation; back: () => void }) {
  const latest = detail ? last(detail.terms) : undefined;
  const [id] = useState(newRequestId);
  const [name, setName] = useState(detail?.summary.name ?? '');
  const [kind, setKind] = useState<Kind>(detail?.summary.kind ?? 'CONTRACT');
  const [terms, setTerms] = useState(latest?.term.terms ?? emptyTerms(today));
  const [assessment, setAssessment] = useState(detail?.assessment ?? defaultAssessment);

  function submit(event: FormEvent) {
    event.preventDefault();
    const common = { name, assessment: kind === 'SUBSCRIPTION' ? assessment : null };
    void (detail && latest
      ? save('/' + detail.summary.id + '/terms/' + latest.term.id, {
        ...common, revision: detail.revision, terms: latest.editable ? terms : null,
      }, 'PUT')
      : save('', { ...common, requestId: id, kind, terms }));
  }

  return <form className="contract-form" onSubmit={submit} onInvalid={revealInvalidField}>
    <div className="contract-fields">
      <label>Contract name<input value={name} required maxLength={120} onChange={event => setName(event.target.value)} /></label>
      {!detail && <label>Record type<select value={kind} onChange={event => setKind(event.target.value as Kind)}>
        <option value="CONTRACT">Contract</option><option value="SUBSCRIPTION">Subscription</option>
      </select></label>}
    </div>
    {(!latest || latest.editable) && <TermsEditor value={terms} onChange={setTerms} fixedStart={(detail?.terms.length ?? 0) > 1} />}
    {kind === 'SUBSCRIPTION' && <fieldset className="contract-evidence">
      <legend>Subscription assessment</legend>
      <div className="contract-fields">
        <label>Level of usage<select value={assessment.usage} onChange={event => setAssessment({ ...assessment, usage: event.target.value })}>
          {['UNKNOWN', 'HIGH', 'MEDIUM', 'LOW', 'UNUSED'].map(value => <option key={value} value={value}>{label(value)}</option>)}
        </select></label>
        <label>Cancellation assessment<select value={assessment.closing} onChange={event => setAssessment({ ...assessment, closing: event.target.value })}>
          {['UNDECIDED', 'KEEP', 'CONSIDER_CANCELLING', 'LIKELY_CANCEL', 'CANCELLED'].map(value =>
            <option key={value} value={value}>{label(value)}</option>)}
        </select></label>
      </div>
      <label>Assessment notes<textarea maxLength={1000} value={assessment.notes ?? ''}
        onChange={event => setAssessment({ ...assessment, notes: event.target.value || null })} /></label>
    </fieldset>}
    <div className="bill-actions"><button type="submit">Save contract</button><button type="button" onClick={back}>Back</button></div>
  </form>;
}

function revealInvalidField(event: FormEvent<HTMLFormElement>) {
  if (event.target instanceof HTMLElement) {
    const details = event.target.closest('details');
    if (details) details.open = true;
  }
}

function LifecycleForm({ mode, detail, view, today, save, back }: {
  mode: Exclude<Mode, 'edit'>; detail: Detail; view: TermView; today: string; save: Mutation; back: () => void;
}) {
  const term = view.term;
  const root = '/' + detail.summary.id;
  const path = root + '/terms/' + term.id;
  const recordedDecision = last(term.decisions)?.decision ?? 'UNDECIDED';
  const [decision, setDecision] = useState<Decision>(recordedDecision);
  const [outcome, setOutcome] = useState<Outcome>('RENEWED');
  const [date, setDate] = useState(today);
  const [evidence, setEvidence] = useState('');
  const [effective, setEffective] = useState(today > term.terms.start ? today : term.terms.start);
  const firstReactivationDate = term.confirmation?.effective ?? today;
  const [next, setNext] = useState(() => ({
    ...emptyTerms(today < firstReactivationDate ? firstReactivationDate : today, term.terms.provider), plan: term.terms.plan,
    type: term.terms.type, frequency: term.terms.frequency,
  }));
  const revision = detail.revision;
  const confirmation = mode === 'confirmation';
  const reactivate = mode === 'reactivate';
  const needsNext = reactivate || confirmation && outcome !== 'CANCELLED';
  const title = { decision: 'Record decision', attempt: 'Record attempted action',
    confirmation: 'Save provider confirmation', reactivate: 'Save reactivation' }[mode];

  function submit(event: FormEvent) {
    event.preventDefault();
    switch (mode) {
      case 'decision': void save(path + '/decision', { revision, decision, date, evidence }); break;
      case 'attempt': void save(path + '/attempt', { revision, action: recordedDecision, date, evidence }); break;
      case 'confirmation':
        void save(path + '/confirmation', {
          revision, outcome, date, effective, evidence, next: needsNext ? { ...next, start: effective } : null,
        });
        break;
      case 'reactivate': void save(root + '/reactivate', { revision, next }); break;
    }
  }

  return <form className="contract-form" onSubmit={submit} onInvalid={revealInvalidField}>
    <h4>{title}</h4>
    <div className="contract-fields">
      {mode === 'decision' && <label>Decision<select value={decision} onChange={event => setDecision(event.target.value as Decision)}>
        {decisions.map(value => <option key={value} value={value}>{label(value)}</option>)}
      </select></label>}
      {mode === 'attempt' && <p>Attempted action · {label(recordedDecision)}</p>}
      {confirmation && <>
        <label>Provider outcome<select value={outcome} onChange={event => setOutcome(event.target.value as Outcome)}>
          {outcomes.map(value => <option key={value} value={value}>{label(value)}</option>)}
        </select></label>
        <label>Effective date<input type="date" required min={term.terms.start} value={effective}
          onChange={event => setEffective(event.target.value)} /></label>
      </>}
      {!reactivate && <label>{confirmation ? 'Confirmation date' : mode === 'attempt' ? 'Attempt date' : 'Decision date'}
        <input type="date" max={today} required value={date} onChange={event => setDate(event.target.value)} /></label>}
    </div>
    {!reactivate && <label>{confirmation ? 'Provider evidence' : mode === 'attempt' ? 'Action record' : 'Decision notes or evidence'}
      <textarea required={mode !== 'decision'} maxLength={1000} value={evidence} onChange={event => setEvidence(event.target.value)} /></label>}
    {needsNext && <fieldset className="contract-evidence">
      <legend>{reactivate ? 'Reactivated term' : 'Replacement term'}</legend>
      <TermsEditor value={confirmation ? { ...next, start: effective } : next} onChange={setNext} fixedStart={confirmation} />
    </fieldset>}
    <div className="bill-actions"><button type="submit">{title}</button><button type="button" onClick={back}>Back</button></div>
  </form>;
}

function TermDetails({ view, today }: { view: TermView; today: string }) {
  const { term, deadline } = view;
  const { terms, confirmation } = term;
  return <>
    <dl className="contract-fields contract-facts">
      <div><dt>Provider</dt><dd>{terms.provider}</dd></div>
      {terms.plan && <div><dt>Plan</dt><dd>{terms.plan}</dd></div>}
      <div><dt>Term</dt><dd>{label(terms.type)} · {terms.start}</dd></div>
      {terms.frequency && <div><dt>Billing frequency</dt><dd>{label(terms.frequency)}</dd></div>}
      <div><dt>Review target</dt><dd>{deadline?.target ?? (deadline ? 'Needs a date' : 'No review scheduled')}</dd></div>
      {deadline && <div><dt>Suggested target</dt><dd>{deadline.suggested ?? 'No verified cutoff'}</dd></div>}
      {deadline && <div><dt>First verified cutoff</dt><dd>{deadline.cutoff ?? 'Unknown'}</dd></div>}
    </dl>
    {deadline && <p>Review {label(deadline.stage).toLowerCase()}{deadline.cutoff && deadline.cutoff < today ? ' · Verified cutoff passed' : ''}.</p>}
    {terms.reviewSource && <p>Review source · {terms.reviewSource}</p>}
    {terms.reviewNote && <p>{terms.reviewNote}</p>}
    <details open>
      <summary>Dates, prices and notice terms</summary>
      <dl className="contract-fields contract-facts">
        {Object.entries(dateFields).map(([key, title]) => {
          const evidence = terms[key as keyof typeof dateFields];
          return <div key={key}><dt>{title}</dt><dd>{label(evidence.certainty)}{evidence.date && ' · ' + evidence.date}
            {evidence.source && <><br />Source · {evidence.source}</>}{evidence.note && <><br />{evidence.note}</>}</dd></div>;
        })}
        {Object.entries(priceFields).map(([key, title]) => {
          const price = terms[key as keyof typeof priceFields];
          return price && <div key={key}><dt>{title}</dt><dd>{price.amount} {price.currency}{price.effectiveFrom && ' from ' + price.effectiveFrom}</dd></div>;
        })}
      </dl>
      {terms.noticeTerms && <p>Notice terms · {terms.noticeTerms}</p>}
    </details>
    {(terms.references?.billId || terms.references?.transactionId) && <p>
      {terms.references.billId && <a href={'/workspaces/planning/forecast?bill=' + encodeURIComponent(terms.references.billId)}>Review linked recurring bill in Plan ahead</a>}
      {terms.references.transactionId && <span> Transaction evidence linked.</span>}
    </p>}
    {confirmation && terms.references?.billId && <p>Use the confirmed effective date when reviewing any schedule change in Plan ahead.</p>}
    {term.revisions.length > 0 && <details>
      <summary>Earlier saved versions of this term</summary>
      <ul className="contract-history">{term.revisions.map(({ recordedOn, previous }, index) =>
        <li key={index}>{recordedOn} · {previous.provider}{previous.plan && ' · ' + previous.plan}
          <ul>{Object.entries(priceFields).map(([key, title]) => {
            const price = previous[key as keyof typeof priceFields];
            return price && <li key={key}>{title} · {price.amount} {price.currency}{price.effectiveFrom && ' from ' + price.effectiveFrom}</li>;
          })}
          {Object.entries(dateFields).map(([key, title]) => {
            const previousDate = previous[key as keyof typeof dateFields];
            return <li key={key}>{title} · {label(previousDate.certainty)}{previousDate.date && ' · ' + previousDate.date}</li>;
          })}</ul>
        </li>)}</ul>
    </details>}
    <h4>Decision and provider history</h4>
    <ul className="contract-history">
      {term.decisions.map((entry, i) => <li key={'decision' + i}>{entry.date} · Decision: {label(entry.decision)}{entry.evidence && ' · ' + entry.evidence}</li>)}
      {term.attempts.map((entry, i) => <li key={'attempt' + i}>{entry.date} · Attempted: {label(entry.action)} · {entry.evidence}</li>)}
      {confirmation
        ? <li>Provider confirmed {label(confirmation.outcome).toLowerCase()} on {confirmation.date}, effective {confirmation.effective} · {confirmation.evidence}</li>
        : <li>Awaiting provider confirmation if the service changes.</li>}
    </ul>
  </>;
}

function ReviewAlert({ notification, today, change }: {
  notification: Notification; today: string;
  change: (item: Notification, action: string, until?: string) => Promise<void>;
}) {
  const [until, setUntil] = useState('');
  const active = notification.stage === 'DUE' || notification.stage === 'OVERDUE';
  return <li className="bill-row contract-alert">
    <a href={termLink(notification.contractId, notification.termId)}>{notification.name}</a>
    <p>{label(notification.stage)}{notification.unread && ' · Unread'}{notification.dismissed && ' · Dismissed'}
      {notification.snoozedUntil && ' · Snoozed until ' + notification.snoozedUntil}
      {notification.target && ' · Review ' + notification.target}{notification.cutoff && ' · Cutoff ' + notification.cutoff}</p>
    {active && <div className="bill-actions">
      {notification.unread && <button type="button" onClick={() => void change(notification, 'MARK_READ')}>Mark read</button>}
      {notification.dismissed || notification.snoozedUntil
        ? <button type="button" onClick={() => void change(notification, 'REOPEN')}>Reopen alert</button>
        : <>
          <button type="button" onClick={() => void change(notification, 'DISMISS')}>Dismiss alert</button>
          <form className="contract-snooze" onSubmit={event => {
            event.preventDefault();
            void change(notification, 'SNOOZE', until);
          }}>
            <label>Snooze until<input type="date" required value={until} onChange={event => setUntil(event.target.value)} /></label>
            <button type="submit" disabled={until <= today}>Snooze</button>
          </form>
        </>}
    </div>}
  </li>;
}

export default function ContractsWorkspace({ inbox, refreshInbox }: { inbox: Inbox | null; refreshInbox: () => Promise<void> }) {
  const identity = useIdentity();
  const [query, setQuery] = useState(() => new URLSearchParams(location.search));
  const contractId = query.get('contract');
  const termId = query.get('term');
  const inInbox = query.get('view') === 'reviews';
  const [list, setList] = useState<Summary[] | null>(null);
  const [detail, setDetail] = useState<Detail | null>(null);
  const [creating, setCreating] = useState(false);
  const [mode, setMode] = useState<Mode | null>(null);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);
  const [retry, setRetry] = useState(0);
  const pending = useRef(false);
  const heading = useRef<HTMLHeadingElement>(null);
  const current = detail?.summary.id === contractId ? detail : null;
  const selected = current?.terms.find(v => v.term.id === (termId ?? current.summary.currentTermId));
  const today = inbox?.today ?? '';
  const canEdit = !!identity?.canEdit && !!current?.summary.owned;
  const unread = inbox?.notifications.filter(item => item.unread).length ?? 0;
  const chosenDecision = selected && last(selected.term.decisions)?.decision;
  const scope = current?.summary.shared ? 'Shared with household readers' : 'Private';

  useEffect(() => {
    const navigate = () => {
      setQuery(new URLSearchParams(location.search));
      setMode(null);
      setCreating(false);
      setError('');
      setMessage('');
    };
    window.addEventListener('popstate', navigate);
    return () => window.removeEventListener('popstate', navigate);
  }, []);

  useEffect(() => {
    let live = true;
    if (contractId) {
      void contracts<Detail>('/' + encodeURIComponent(contractId))
        .then(data => { if (live) setDetail(data); })
        .catch(err => {
          if (live) {
            setDetail(null);
            setError(err instanceof ContractError && err.status === 404 ? 'Contract unavailable.' : err.message);
          }
        });
    } else {
      void contracts<Summary[]>().then(data => { if (live) setList(data); })
        .catch(err => { if (live) { setList(null); setError(err.message); } });
    }
    return () => { live = false; };
  }, [contractId, retry]);

  function navigate(next: string) {
    history.pushState(history.state, '', next);
    setQuery(new URLSearchParams(location.search));
    setMode(null);
    setCreating(false);
    setError('');
    setMessage('');
    heading.current?.focus();
  }

  async function run(operation: () => Promise<void>) {
    if (pending.current) return;
    pending.current = true;
    setBusy(true);
    setError('');
    setMessage('');
    try { await operation(); }
    catch (err) { setError(err instanceof Error ? err.message : 'Unable to save the contract.'); }
    finally { pending.current = false; setBusy(false); }
  }

  async function save(path: string, body: unknown, method?: string) {
    await run(async () => {
      const result = await contracts<Detail>(path, body, method);
      navigate(termLink(result.summary.id, result.latestTermId));
      setDetail(result);
      setMessage('Contract saved.');
      await refreshInbox();
    });
  }

  async function change(item: Notification, action: string, until?: string) {
    await run(async () => {
      await contracts('/alerts/' + item.contractId + '/' + item.termId, { action, until: until ?? null, timing: item.timing });
      await refreshInbox();
    });
  }

  const links = <div className="bill-actions">
    <button type="button" onClick={() => navigate(contractPath)} aria-pressed={!inInbox && !contractId}>Contract register</button>
    <button type="button" onClick={() => navigate(contractPath + '?view=reviews')} aria-pressed={inInbox}>
      Review inbox{unread > 0 && ' (' + unread + ' unread)'}
    </button>
    <a href="/api/contracts/export" download>Export visible contracts</a>
  </div>;

  return <section className="bills-workspace contracts-workspace">
    <h2 tabIndex={-1} ref={heading}>Contracts &amp; subscriptions</h2>
    {links}
    {error && <p role="alert">{error}</p>}
    {message && <p role="status">{message}</p>}
    <fieldset className="contract-workspace-body" disabled={busy}>
      {inInbox && <>
        <h3>Review inbox</h3>
        {inbox ? <>
          <p>As of {inbox.today} · {inbox.zone}</p>
          {inbox.notifications.length === 0 && <p>No contract reviews to show.</p>}
          <ul className="contract-list">{inbox.notifications.map(item => <ReviewAlert key={item.contractId + item.termId}
            notification={item} today={today} change={change} />)}</ul>
        </> : <p>Review inbox unavailable. <button type="button" onClick={() => void refreshInbox()}>Retry inbox</button></p>}
      </>}
      {!inInbox && !contractId && (creating
        ? <><h3>Add a contract or subscription</h3><ContractForm today={today} save={save} back={() => setCreating(false)} /></>
        : <>
          <h3>Contract register</h3>
          {identity?.canEdit && <button type="button" disabled={!today} onClick={() => setCreating(true)}>Add contract or subscription</button>}
          {list?.length === 0 && <p>There are no visible contracts or subscriptions yet.</p>}
          {list === null && !error && <p role="status">Loading contracts…</p>}
          <ul className="contract-list">{list?.map(item => <li className="bill-row" key={item.id}>
            <a href={termLink(item.id)}>{item.name}</a>
            <p>{item.provider} · {label(item.kind)} · {label(item.status)} · {item.shared ? 'Shared' : 'Private'}</p>
          </li>)}</ul>
        </>)}
      {contractId && !current && !error && <p role="status">Loading contract…</p>}
      {current && <>
        <h3>{current.summary.name}</h3>
        <p>{label(current.summary.kind)} · {label(current.summary.status)} · {scope}</p>
        {current.assessment && <p>Usage · {label(current.assessment.usage)}. Cancellation assessment · {label(current.assessment.closing)}.
          {current.assessment.notes && ' ' + current.assessment.notes}</p>}
        {canEdit && !mode && <div className="bill-actions">
          <button type="button" onClick={() => setMode('edit')}>Edit contract</button>
          <button type="button" onClick={() => void save('/' + contractId + '/sharing', {
            revision: current.revision, shared: !current.summary.shared,
          })}>{current.summary.shared ? 'Make private' : 'Share with household'}</button>
          {current.summary.status === 'CLOSED' && current.latestTermId === current.summary.currentTermId && selected?.term.id === current.latestTermId && today &&
            <button type="button" onClick={() => setMode('reactivate')}>Reactivate contract</button>}
        </div>}
        {mode === 'edit' && <ContractForm detail={current} today={today} save={save} back={() => setMode(null)} />}
        {mode !== 'edit' && <>
          <nav aria-label="Contract terms"><ul className="contract-term-list">{current.terms.map(({ term }) =>
            <li key={term.id}><a href={termLink(current.summary.id, term.id)} aria-current={selected?.term.id === term.id ? 'page' : undefined}>
              Term {term.terms.start}{term.id === current.summary.currentTermId ? ' · Current' : ''}</a></li>)}</ul></nav>
          {selected ? <div className="bill-row">
            <TermDetails view={selected} today={today} />
            {canEdit && selected.editable && !mode && today && <div className="bill-actions">
              {selected.term.terms.reviewEnabled && <button type="button" onClick={() => setMode('decision')}>Review decision</button>}
              {chosenDecision && ['RENEW', 'SWITCH', 'CANCEL'].includes(chosenDecision) &&
                <button type="button" onClick={() => setMode('attempt')}>Log attempted action</button>}
              <button type="button" onClick={() => setMode('confirmation')}>Record provider confirmation</button>
            </div>}
            {mode && <LifecycleForm key={mode} mode={mode} detail={current} view={selected} today={today} save={save} back={() => setMode(null)} />}
          </div> : <p role="status">Contract term unavailable.</p>}
        </>}
      </>}
      {error && <button type="button" onClick={() => { setError(''); setMode(null); setRetry(value => value + 1); }}>Reload saved records</button>}
    </fieldset>
  </section>;
}
