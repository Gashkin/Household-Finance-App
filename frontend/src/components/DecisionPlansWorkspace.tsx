import { useEffect, useState } from "react";
import {
  plans,
  type DecisionPlan,
  type PlanContent,
  type PlanHistory,
} from "../api/plans";
import DebtReportPreview from "./DebtReportPreview";
const today = () =>
  new Intl.DateTimeFormat("en-CA", {
    timeZone: "Europe/Dublin",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(new Date());
const fresh = (): PlanContent => ({
  title: "",
  goal: "",
  evidence: [],
  options: [],
});
const itemId = () => crypto.randomUUID();
function HistoryEntry({ entry }: { entry: PlanHistory }) {
  return (
    <details className="plan-history-entry">
      <summary>
        {entry.kind.replace(/_/g, " ")} · {new Date(entry.at).toLocaleString()}
      </summary>
      <p>{entry.note}</p>
      <h4>{entry.content.title}</h4>
      <p>{entry.content.goal}</p>
      {entry.content.evidence.map((e) => (
        <p key={e.id}>
          <b>{e.source}</b> · {e.observedOn}
          <br />
          {e.summary}
          <br />
          Assumptions / uncertainty: {e.uncertainty}
        </p>
      ))}
      {entry.content.options.map((o) => (
        <p key={o.id}>
          <b>{o.label}</b>: {o.impact}
          <br />
          Uncertainty: {o.uncertainty}
        </p>
      ))}
      {entry.decision && (
        <p>
          Recorded choice: <b>{entry.decision.optionLabel}</b> —{" "}
          {entry.decision.rationale}. Review {entry.decision.reviewOn}
        </p>
      )}
      {entry.actions.map((a) => (
        <p key={a.id}>
          {a.label} · due {a.dueOn} ·{" "}
          {a.completedAt ? a.outcome : "Pending at this point"}
        </p>
      ))}
      {entry.report && (
        <p>
          Frozen debt comparison from {entry.report.generatedAt};{" "}
          {entry.report.calculationVersion}
        </p>
      )}
    </details>
  );
}
export default function DecisionPlansWorkspace({
  initialId,
}: {
  initialId: string | null;
}) {
  const [saved, setSaved] = useState<DecisionPlan[]>([]);
  const [selected, setSelected] = useState<DecisionPlan | null>(null);
  const [draft, setDraft] = useState<PlanContent>(fresh);
  const [dirty, setDirty] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [optionId, setOptionId] = useState("");
  const [rationale, setRationale] = useState("");
  const [reviewOn, setReviewOn] = useState(today);
  const [confirmed, setConfirmed] = useState(false);
  const [actions, setActions] = useState([
    { id: itemId(), label: "", dueOn: today() },
  ]);
  const [outcomes, setOutcomes] = useState<Record<string, string>>({});
  function load(p: DecisionPlan | null) {
    setSelected(p);
    setDraft(p ? structuredClone(p.data.content) : fresh());
    setDirty(false);
    setOptionId("");
    setRationale("");
    setConfirmed(false);
    setOutcomes({});
    setActions([{ id: itemId(), label: "", dueOn: today() }]);
    setReviewOn(today());
    setError("");
    setNotice("");
  }
  useEffect(() => {
    let active = true;
    setBusy(true);
    plans<DecisionPlan[]>()
      .then((items) => {
        if (active) {
          setSaved(items);
          load(items.find((p) => p.id === initialId) ?? items[0] ?? null);
        }
      })
      .catch((e) => {
        if (active) setError(String(e.message));
      })
      .finally(() => {
        if (active) setBusy(false);
      });
    return () => {
      active = false;
    };
  }, [initialId]);
  function edit(next: PlanContent) {
    setDraft(next);
    setDirty(true);
    setError("");
    setNotice("");
  }
  async function mutate(path: string, body: unknown, method = "POST") {
    setBusy(true);
    setError("");
    setNotice("");
    try {
      const p = await plans<DecisionPlan>(path, body, method);
      setSaved((items) => [p, ...items.filter((item) => item.id !== p.id)]);
      load(p);
      setNotice("Saved privately in this workspace.");
    } catch (e) {
      setError(e instanceof Error ? e.message : "Unable to save plan");
    } finally {
      setBusy(false);
    }
  }
  async function reload() {
    setBusy(true);
    setError("");
    try {
      const items = await plans<DecisionPlan[]>();
      setSaved(items);
      load(items.find((p) => p.id === selected?.id) ?? null);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Unable to reload plans");
    } finally {
      setBusy(false);
    }
  }
  return (
    <section className="decision-plans">
      <p className="debt-eyebrow">Evidence → choice → follow-through</p>
      <h2>Financial plans</h2>
      <p>
        Keep the reasoning behind a financial choice, record what you decide,
        and revisit the result. These records never make payments or change
        bank, pension or tax settings.
      </p>
      {error && (
        <p role="alert" className="debt-alert">
          {error}
        </p>
      )}
      {notice && <p role="status">{notice}</p>}
      <fieldset className="plan-controls" disabled={busy}>
        <legend className="sr-only">Financial plan controls</legend>
        <div className="plan-toolbar">
          <label>
            Saved plans
            <select
              value={selected?.id ?? ""}
              onChange={(e) =>
                load(saved.find((p) => p.id === e.target.value) ?? null)
              }
            >
              <option value="">New plan</option>
              {saved.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.data.content.title} · {p.status.replace(/_/g, " ")}
                </option>
              ))}
            </select>
          </label>
          <button onClick={() => load(null)}>New financial plan</button>
          <button onClick={reload}>Reload saved plans</button>
        </div>
        {dirty && (
          <p className="plan-notice">
            Unsaved changes. Saving revisions returns this plan to a proposal;
            previous decisions and actions remain in history. Switching plans or
            reloading discards unsaved edits.
          </p>
        )}
        <form
          onSubmit={(e) => {
            e.preventDefault();
            void mutate(
              selected ? "/" + selected.id : "",
              selected
                ? { revision: selected.revision, content: draft }
                : draft,
              selected ? "PUT" : "POST",
            );
          }}
        >
          <article className="plan-card">
            <h3>1. Goal and evidence</h3>
            <label>
              Financial plan title
              <input
                maxLength={120}
                required
                value={draft.title}
                onChange={(e) => edit({ ...draft, title: e.target.value })}
              />
            </label>
            <label>
              What are you trying to achieve?
              <textarea
                maxLength={2000}
                required
                value={draft.goal}
                onChange={(e) => edit({ ...draft, goal: e.target.value })}
              />
            </label>
            {draft.evidence.map((e, i) => (
              <div className="plan-item" key={e.id}>
                <h4>Evidence {i + 1}</h4>
                <label>
                  Evidence {i + 1} source
                  <input
                    required
                    maxLength={500}
                    value={e.source}
                    onChange={(v) =>
                      edit({
                        ...draft,
                        evidence: draft.evidence.map((x) =>
                          x.id === e.id ? { ...x, source: v.target.value } : x,
                        ),
                      })
                    }
                  />
                </label>
                <label>
                  Evidence {i + 1} observed on
                  <input
                    required
                    type="date"
                    value={e.observedOn}
                    onChange={(v) =>
                      edit({
                        ...draft,
                        evidence: draft.evidence.map((x) =>
                          x.id === e.id
                            ? { ...x, observedOn: v.target.value }
                            : x,
                        ),
                      })
                    }
                  />
                </label>
                <label>
                  Evidence {i + 1} summary
                  <textarea
                    required
                    maxLength={2000}
                    value={e.summary}
                    onChange={(v) =>
                      edit({
                        ...draft,
                        evidence: draft.evidence.map((x) =>
                          x.id === e.id ? { ...x, summary: v.target.value } : x,
                        ),
                      })
                    }
                  />
                </label>
                <label>
                  Evidence {i + 1} assumptions and uncertainty
                  <textarea
                    required
                    maxLength={1000}
                    value={e.uncertainty}
                    onChange={(v) =>
                      edit({
                        ...draft,
                        evidence: draft.evidence.map((x) =>
                          x.id === e.id
                            ? { ...x, uncertainty: v.target.value }
                            : x,
                        ),
                      })
                    }
                  />
                </label>
                <button
                  type="button"
                  onClick={() =>
                    edit({
                      ...draft,
                      evidence: draft.evidence.filter((x) => x.id !== e.id),
                    })
                  }
                >
                  Remove evidence {i + 1}
                </button>
              </div>
            ))}
            <button
              type="button"
              disabled={draft.evidence.length >= 20}
              onClick={() =>
                edit({
                  ...draft,
                  evidence: [
                    ...draft.evidence,
                    {
                      id: itemId(),
                      source: "",
                      observedOn: today(),
                      summary: "",
                      uncertainty: "",
                    },
                  ],
                })
              }
            >
              Add evidence
            </button>
          </article>
          <article className="plan-card">
            <h3>2. Compare options</h3>
            <p>
              Describe impacts and uncertainty explicitly. Entered descriptions
              are your estimates; the app does not validate lender, tax or
              pension terms.
            </p>
            <div className="plan-option-grid">
              {draft.options.map((o, i) => (
                <div className="plan-item" key={o.id}>
                  <h4>Option {i + 1}</h4>
                  <label>
                    Option {i + 1} name
                    <input
                      required
                      maxLength={120}
                      value={o.label}
                      onChange={(e) =>
                        edit({
                          ...draft,
                          options: draft.options.map((x) =>
                            x.id === o.id ? { ...x, label: e.target.value } : x,
                          ),
                        })
                      }
                    />
                  </label>
                  <label>
                    Option {i + 1} impact
                    <textarea
                      required
                      maxLength={2000}
                      value={o.impact}
                      onChange={(e) =>
                        edit({
                          ...draft,
                          options: draft.options.map((x) =>
                            x.id === o.id
                              ? { ...x, impact: e.target.value }
                              : x,
                          ),
                        })
                      }
                    />
                  </label>
                  <label>
                    Option {i + 1} uncertainty
                    <textarea
                      required
                      maxLength={1000}
                      value={o.uncertainty}
                      onChange={(e) =>
                        edit({
                          ...draft,
                          options: draft.options.map((x) =>
                            x.id === o.id
                              ? { ...x, uncertainty: e.target.value }
                              : x,
                          ),
                        })
                      }
                    />
                  </label>
                  <button
                    type="button"
                    onClick={() =>
                      edit({
                        ...draft,
                        options: draft.options.filter((x) => x.id !== o.id),
                      })
                    }
                  >
                    Remove option {i + 1}
                  </button>
                </div>
              ))}
            </div>
            <button
              type="button"
              disabled={draft.options.length >= 10}
              onClick={() =>
                edit({
                  ...draft,
                  options: [
                    ...draft.options,
                    { id: itemId(), label: "", impact: "", uncertainty: "" },
                  ],
                })
              }
            >
              Add option
            </button>
          </article>
          <button
            className="debt-primary"
            type="submit"
            disabled={!!selected && !dirty}
          >
            {selected ? "Save revised evidence and options" : "Save proposal"}
          </button>
        </form>
        {selected?.data.report && (
          <details className="plan-card">
            <summary>Frozen debt calculation and source assumptions</summary>
            <p>
              Captured {selected.data.report.generatedAt}. Historical evidence;
              this report does not refresh when cash or assumptions change.
              Revised options are separate operator proposals.
            </p>
            <DebtReportPreview
              report={selected.data.report}
              onDownload={() => {
                const url = URL.createObjectURL(
                  new Blob([JSON.stringify(selected.data.report, null, 2)], {
                    type: "application/json",
                  }),
                );
                const a = document.createElement("a");
                a.href = url;
                a.download = "saved-debt-comparison.json";
                a.click();
                setTimeout(() => URL.revokeObjectURL(url), 1000);
              }}
            />
          </details>
        )}
        {selected && !dirty && !selected.data.decision && (
          <form
            className="plan-card"
            onSubmit={(e) => {
              e.preventDefault();
              void mutate("/" + selected.id + "/decision", {
                revision: selected.revision,
                optionId,
                rationale,
                reviewOn,
                actions,
                confirmed,
              });
            }}
          >
            <h3>3. Record an explicit decision</h3>
            <p>
              Proposal only until you record a choice. Add evidence and at least
              two options before deciding.
            </p>
            <label>
              Chosen option
              <select
                required
                value={optionId}
                onChange={(e) => setOptionId(e.target.value)}
              >
                <option value="">Choose an option</option>
                {selected.data.content.options.map((o) => (
                  <option key={o.id} value={o.id}>
                    {o.label}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Why this choice?
              <textarea
                required
                maxLength={2000}
                value={rationale}
                onChange={(e) => setRationale(e.target.value)}
              />
            </label>
            <label>
              Review decision on
              <input
                required
                type="date"
                value={reviewOn}
                onChange={(e) => setReviewOn(e.target.value)}
              />
            </label>
            {actions.map((a, i) => (
              <div key={a.id} className="plan-item">
                <label>
                  Follow-up {i + 1}
                  <input
                    required
                    maxLength={500}
                    value={a.label}
                    onChange={(e) =>
                      setActions((items) =>
                        items.map((x) =>
                          x.id === a.id ? { ...x, label: e.target.value } : x,
                        ),
                      )
                    }
                  />
                </label>
                <label>
                  Follow-up {i + 1} due
                  <input
                    required
                    type="date"
                    value={a.dueOn}
                    onChange={(e) =>
                      setActions((items) =>
                        items.map((x) =>
                          x.id === a.id ? { ...x, dueOn: e.target.value } : x,
                        ),
                      )
                    }
                  />
                </label>
                <button
                  type="button"
                  disabled={actions.length === 1}
                  onClick={() =>
                    setActions((items) => items.filter((x) => x.id !== a.id))
                  }
                >
                  Remove follow-up {i + 1}
                </button>
              </div>
            ))}
            <button
              type="button"
              disabled={actions.length >= 20}
              onClick={() =>
                setActions((items) => [
                  ...items,
                  { id: itemId(), label: "", dueOn: today() },
                ])
              }
            >
              Add follow-up
            </button>
            <label className="plan-check">
              <input
                required
                type="checkbox"
                checked={confirmed}
                onChange={(e) => setConfirmed(e.target.checked)}
              />
              I have reviewed the evidence, impacts and uncertainty and choose
              this option. No financial change is executed.
            </label>
            <button type="submit" className="debt-primary">
              Record decision
            </button>
          </form>
        )}
        {selected?.data.decision && !dirty && (
          <article className="plan-card">
            <h3>3. Decision and follow-through</h3>
            <p className="plan-status">
              {selected.status === "REVIEW_DUE"
                ? "Review due — revisit evidence before acting"
                : "Decision recorded"}
            </p>
            <p>
              Chosen: <b>{selected.data.decision.optionLabel}</b>
            </p>
            <p>{selected.data.decision.rationale}</p>
            <p>
              Review on {selected.data.decision.reviewOn} · recorded{" "}
              {new Date(selected.data.decision.decidedAt).toLocaleString()}
            </p>
            <p>
              To change your choice or record new evidence, edit and save the
              goal, evidence or options above. Earlier decisions remain in
              history.
            </p>
            {selected.data.actions.map((a) => (
              <div className="plan-item" key={a.id}>
                <h4>{a.label}</h4>
                <p>
                  Due {a.dueOn}
                  {!a.completedAt && a.dueOn < today() ? " · Overdue" : ""}
                </p>
                {a.completedAt ? (
                  <p>
                    Completed {new Date(a.completedAt).toLocaleString()}:{" "}
                    {a.outcome}
                  </p>
                ) : (
                  <form
                    onSubmit={(e) => {
                      e.preventDefault();
                      void mutate("/" + selected.id + "/complete", {
                        revision: selected.revision,
                        actionId: a.id,
                        outcome: outcomes[a.id] ?? "",
                      });
                    }}
                  >
                    <label>
                      Observed outcome for {a.label}
                      <textarea
                        required
                        maxLength={2000}
                        value={outcomes[a.id] ?? ""}
                        onChange={(e) =>
                          setOutcomes((items) => ({
                            ...items,
                            [a.id]: e.target.value,
                          }))
                        }
                      />
                    </label>
                    <button type="submit">Mark follow-up complete</button>
                  </form>
                )}
              </div>
            ))}
          </article>
        )}
        {selected && (
          <section className="plan-card">
            <h3>Decision history</h3>
            <p>
              Earlier evidence, proposals, decisions and outcomes are retained
              in this workspace and its encrypted backups. Revision{" "}
              {selected.revision}.
            </p>
            {[...selected.data.history].reverse().map((entry, i) => (
              <HistoryEntry key={i} entry={entry} />
            ))}
          </section>
        )}
      </fieldset>
      {busy && <p role="status">Loading financial plans…</p>}
    </section>
  );
}
