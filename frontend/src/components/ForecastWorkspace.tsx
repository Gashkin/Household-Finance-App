import { useEffect, useRef, useState, type FormEvent } from "react";
import {
  forecast,
  type ForecastWorkspace as Workspace,
  type Comparison,
  type Projection,
  type FutureEvent,
} from "../api/forecast";
import { money } from "../domain/money";
import { euroText } from "../api/ledger";
import { todayInDublin, dateInputOffset as dateAfter } from "../domain/dates";
type EventDraft = {
  id: string;
  date: string;
  label: string;
  direction: string;
  certainty: string;
  amountEuro: string;
  source: string;
};
function draft(event: FutureEvent): EventDraft {
  return {
    id: event.id,
    date: event.date,
    label: event.label,
    direction: event.direction,
    certainty: event.certainty,
    amountEuro: euroText(event.amountMinor),
    source: event.source,
  };
}
function Summary({ value, label }: { value: Projection; label: string }) {
  return (
    <section className="forecast-result" aria-label={label}>
      <h3>{label}</h3>
      <div className="forecast-totals">
        <div>
          Planning headroom<strong>{money(value.planningHeadroomMinor)}</strong>
        </div>
        <div>
          Cash-only headroom
          <strong>{money(value.cashOnlyHeadroomMinor)}</strong>
        </div>
        <div>
          Lowest projected cash<strong>{money(value.lowestMinor)}</strong>
          <span>{value.lowestDate}</span>
        </div>
        <div>
          Projected closing cash<strong>{money(value.closingMinor)}</strong>
        </div>
      </div>
      <p>
        First cash shortfall:{" "}
        <strong>{value.shortfallDate || "none in this horizon"}</strong>. First
        reserve breach:{" "}
        <strong>{value.reserveBreachDate || "none in this horizon"}</strong>.
      </p>
      <p>
        Cash-only headroom excludes all future income. Planning headroom uses
        dated expected income but is capped by starting cash after pending
        debits and reserve. Neither figure credits uncertain money as current
        cash.
      </p>
    </section>
  );
}
export default function ForecastWorkspace() {
  const [workspace, setWorkspace] = useState<Workspace | null>(null),
    [error, setError] = useState(""),
    [message, setMessage] = useState(""),
    [busy, setBusy] = useState(false),
    [dirty, setDirty] = useState(false);
  const generation = useRef(0);
  const [name, setName] = useState("Household plan"),
    [mode, setMode] = useState("MANUAL"),
    [date, setDate] = useState(todayInDublin()),
    [cash, setCash] = useState(""),
    [pending, setPending] = useState("0"),
    [source, setSource] = useState(""),
    [replace, setReplace] = useState(true),
    [end, setEnd] = useState(dateAfter(todayInDublin(), 28)),
    [reserve, setReserve] = useState("0"),
    [events, setEvents] = useState<EventDraft[]>([]),
    [ack, setAck] = useState(false);
  const [expense, setExpense] = useState("0"),
    [expenseDate, setExpenseDate] = useState(dateAfter(todayInDublin(), 1)),
    [income, setIncome] = useState(""),
    [until, setUntil] = useState(""),
    [upside, setUpside] = useState(false),
    [comparison, setComparison] = useState<Comparison | null>(null);
  const plan = workspace?.plan;
  function hydrate(value: Workspace) {
    setWorkspace(value);
    if (value.plan) {
      const p = value.plan.inputs;
      setName(p.name);
      setDate(p.baseline.date);
      setMode(p.baseline.mode);
      setCash(euroText(p.baseline.cashMinor));
      setPending(euroText(p.baseline.pendingMinor));
      setSource(p.baseline.source);
      setEnd(p.endDate);
      setReserve(euroText(p.reserveMinor));
      setEvents(p.events.map(draft));
      setReplace(false);
      setExpenseDate(dateAfter(p.baseline.date, 1));
    } else {
      setMode(value.suggestedLedgerDate ? "LEDGER" : "MANUAL");
      setDate(value.suggestedLedgerDate || value.today);
      setEnd(dateAfter(value.suggestedLedgerDate || value.today, 28));
      setReplace(true);
    }
    setDirty(false);
    setAck(false);
    setComparison(null);
    setIncome("");
    setUntil("");
    setUpside(false);
  }
  async function reload() {
    const current = ++generation.current;
    setBusy(true);
    setError("");
    try {
      const value = await forecast<Workspace>();
      if (current === generation.current) hydrate(value);
    } catch (e) {
      if (current === generation.current) setError((e as Error).message);
    } finally {
      if (current === generation.current) setBusy(false);
    }
  }
  useEffect(() => {
    void reload();
    return () => {
      generation.current++;
    };
  }, []);
  function changed(action: () => void) {
    action();
    setDirty(true);
    setAck(false);
    setComparison(null);
    setMessage("");
  }
  function row(index: number, key: keyof EventDraft, value: string) {
    changed(() =>
      setEvents((old) =>
        old.map((e, i) =>
          i === index
            ? {
                ...e,
                [key]: value,
                ...(key === "direction" && value === "EXPENSE"
                  ? { certainty: "EXPECTED" }
                  : {}),
              }
            : e,
        ),
      ),
    );
  }
  async function save(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError("");
    setComparison(null);
    try {
      const value = await forecast<Workspace>("", {
        revision: plan?.revision || 0,
        name,
        baseline: replace
          ? {
              mode,
              date,
              cashEuro: mode === "MANUAL" ? cash : null,
              pendingEuro: mode === "MANUAL" ? pending : null,
              source: mode === "MANUAL" ? source : null,
            }
          : null,
        endDate: end,
        reserveEuro: reserve,
        events,
        acknowledged: ack,
      });
      hydrate(value);
      setMessage(
        "Plan saved. The ledger and current bank cash were not changed.",
      );
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  async function compare() {
    setBusy(true);
    setError("");
    setComparison(null);
    try {
      const value = await forecast<Comparison>("/compare", {
        revision: plan!.revision,
        expenseEuro: expense,
        expenseDate: expenseDate || null,
        delayedIncomeId: income || null,
        delayedUntil: income ? until || null : null,
        includeUncertain: upside,
      });
      setWorkspace((current) =>
        current ? { ...current, plan: value.checkedPlan } : current,
      );
      setComparison(value);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  if (!workspace)
    return (
      <section className="forecast-workspace">
        <h2>Plan ahead</h2>
        {error ? (
          <>
            <p role="alert">{error}</p>
            <button onClick={reload}>Retry forecast</button>
          </>
        ) : (
          <p>Loading dated plan…</p>
        )}
      </section>
    );
  return (
    <section className="forecast-workspace">
      <h2>Plan ahead</h2>
      <p>
        See when bills could outrun cash, then test a purchase or a delayed
        payday. Expectations remain separate from received money and reviewed
        ledger facts.
      </p>
      {error && <p role="alert">{error}</p>}
      {message && <p role="status">{message}</p>}
      <button disabled={busy} onClick={reload}>
        Reload saved plan and evidence status
      </button>
      {dirty && (
        <p role="status">
          Inputs changed. Save corrections before relying on a forecast or
          comparing a what-if.
        </p>
      )}
      <fieldset disabled={busy} className="affordability-controls">
        <form onSubmit={save}>
          <h3>Starting evidence and horizon</h3>
          <label>
            Plan name
            <input
              value={name}
              maxLength={100}
              onChange={(e) => changed(() => setName(e.target.value))}
              required
            />
          </label>
          {plan && (
            <>
              <p>
                Saved starting evidence:{" "}
                {plan.inputs.baseline.mode === "LEDGER"
                  ? "Reconciled ledger"
                  : "Manual estimate"}{" "}
                · {plan.inputs.baseline.date} · cash{" "}
                {money(plan.inputs.baseline.cashMinor)} · pending debits{" "}
                {money(plan.inputs.baseline.pendingMinor)} ·{" "}
                {plan.inputs.baseline.source}. Captured{" "}
                {new Date(plan.inputs.baseline.capturedAt).toLocaleString()}.
              </p>
              <label className="forecast-check">
                <input
                  type="checkbox"
                  checked={replace}
                  onChange={(e) => changed(() => setReplace(e.target.checked))}
                />
                Replace starting evidence after reviewing actual receipts,
                payments and pending debits
              </label>
            </>
          )}
          {replace && (
            <div className="forecast-grid">
              <label>
                Starting evidence type
                <select
                  aria-label="Starting evidence type"
                  value={mode}
                  onChange={(e) => changed(() => setMode(e.target.value))}
                >
                  <option value="MANUAL">Explicit manual estimate</option>
                  <option value="LEDGER">Reconciled cash ledger</option>
                </select>
              </label>
              <label>
                Starting evidence date (closing balance)
                <input
                  type="date"
                  value={date}
                  max={workspace.today}
                  onChange={(e) => changed(() => setDate(e.target.value))}
                  required
                />
              </label>
              {mode === "MANUAL" ? (
                <>
                  <label>
                    Starting cash (€; overdrafts negative)
                    <input
                      inputMode="decimal"
                      value={cash}
                      onChange={(e) => changed(() => setCash(e.target.value))}
                      required
                    />
                  </label>
                  <label>
                    Pending cash debits already reserved (€)
                    <input
                      inputMode="decimal"
                      value={pending}
                      onChange={(e) =>
                        changed(() => setPending(e.target.value))
                      }
                      required
                    />
                  </label>
                  <label>
                    Starting evidence source
                    <input
                      value={source}
                      maxLength={200}
                      onChange={(e) => changed(() => setSource(e.target.value))}
                      required
                    />
                  </label>
                  <p>
                    A manual figure is an estimate, even when a plan looks
                    affordable. No account or bank evidence is created.
                  </p>
                </>
              ) : (
                <p>
                  Amounts and revisions come from reconciled CASH accounts only.
                  Reconcile every cash account in Money position for this date
                  first. Latest common reconciled date:{" "}
                  {workspace.suggestedLedgerDate || "none"}. Debt accounts and
                  borrowing limits are excluded.
                </p>
              )}
            </div>
          )}
          <div className="forecast-grid">
            <label>
              Plan through
              <input
                type="date"
                value={end}
                min={dateAfter(date, 1)}
                max={dateAfter(date, 90)}
                onChange={(e) => changed(() => setEnd(e.target.value))}
                required
              />
            </label>
            <label>
              Protected reserve (€)
              <input
                inputMode="decimal"
                value={reserve}
                onChange={(e) => changed(() => setReserve(e.target.value))}
                required
              />
            </label>
          </div>
          <h3>Dated expectations</h3>
          <p>
            Enter each future occurrence once, after the starting closing
            balance and within the horizon. Do not repeat pending debits, bills
            already deducted, or transfers between your own cash accounts. To
            reflect a received salary or paid bill, review actuals,
            remove/retime the old expectation and explicitly replace starting
            evidence.
          </p>
          {events.map((e, i) => (
            <fieldset className="forecast-event" key={e.id}>
              <legend>Expectation {i + 1}</legend>
              <div className="forecast-grid">
                <label>
                  Label
                  <input
                    aria-label={`Expectation ${i + 1} label`}
                    value={e.label}
                    maxLength={100}
                    onChange={(v) => row(i, "label", v.target.value)}
                    required
                  />
                </label>
                <label>
                  Date
                  <input
                    aria-label={`Expectation ${i + 1} date`}
                    type="date"
                    value={e.date}
                    min={dateAfter(date, 1)}
                    max={end}
                    onChange={(v) => row(i, "date", v.target.value)}
                    required
                  />
                </label>
                <label>
                  Direction
                  <select
                    aria-label={`Expectation ${i + 1} direction`}
                    value={e.direction}
                    onChange={(v) => row(i, "direction", v.target.value)}
                  >
                    <option value="EXPENSE">Expense / bill</option>
                    <option value="INCOME">Expected income</option>
                  </select>
                </label>
                <label>
                  Income certainty
                  <select
                    aria-label={`Expectation ${i + 1} certainty`}
                    value={e.certainty}
                    disabled={e.direction === "EXPENSE"}
                    onChange={(v) => row(i, "certainty", v.target.value)}
                  >
                    <option value="EXPECTED">Expected (not received)</option>
                    <option value="UNCERTAIN">
                      Uncertain — exclude from base
                    </option>
                  </select>
                </label>
                <label>
                  Amount (€)
                  <input
                    aria-label={`Expectation ${i + 1} amount`}
                    inputMode="decimal"
                    value={e.amountEuro}
                    onChange={(v) => row(i, "amountEuro", v.target.value)}
                    required
                  />
                </label>
                <label>
                  Source / assumption
                  <input
                    aria-label={`Expectation ${i + 1} source`}
                    value={e.source}
                    maxLength={200}
                    onChange={(v) => row(i, "source", v.target.value)}
                    required
                  />
                </label>
              </div>
              <button
                type="button"
                onClick={() =>
                  changed(() =>
                    setEvents((old) => old.filter((_, n) => n !== i)),
                  )
                }
              >
                Remove expectation {i + 1}
              </button>
            </fieldset>
          ))}
          <button
            type="button"
            disabled={events.length >= 200}
            onClick={() =>
              changed(() =>
                setEvents((old) => [
                  ...old,
                  {
                    id: crypto.randomUUID(),
                    date: dateAfter(date, 1),
                    label: "",
                    direction: "EXPENSE",
                    certainty: "EXPECTED",
                    amountEuro: "",
                    source: "",
                  },
                ]),
              )
            }
          >
            Add dated expectation
          </button>
          <label className="forecast-check">
            <input
              type="checkbox"
              checked={ack}
              onChange={(e) => setAck(e.target.checked)}
            />
            I reviewed starting evidence and excluded bills already paid,
            deducted or represented by pending debits
          </label>
          <button disabled={!ack}>Save dated plan</button>
        </form>
        {plan && !dirty && (
          <>
            <section
              aria-label="Forecast evidence status"
              className="forecast-notice"
            >
              <h3>Evidence: {plan.evidenceState.replace(/_/g, " ")}</h3>
              <p>
                {plan.currentDecisionReady
                  ? "Starting evidence is current and reconciled. Future income is still only projected."
                  : "Illustrative projection — refresh/review starting evidence before a current spending decision."}
              </p>
              <ul>
                {plan.warnings.map((w, i) => (
                  <li key={i}>{w}</li>
                ))}
              </ul>
            </section>
            <Summary
              label="Saved expected projection"
              value={plan.projection}
            />
            <details>
              <summary>Daily projection and source expectations</summary>
              <p>
                Each day's low assumes expenses happen before any same-day
                income. These are assumptions, not bank transactions.
              </p>
              <div className="forecast-table">
                <table>
                  <thead>
                    <tr>
                      <th>Date</th>
                      <th>Opening</th>
                      <th>Expenses</th>
                      <th>Applied projected income</th>
                      <th>Lowest cash</th>
                      <th>Closing</th>
                      <th>Source expectations</th>
                    </tr>
                  </thead>
                  <tbody>
                    {plan.projection.days.map((d) => (
                      <tr key={d.date}>
                        <td>{d.date}</td>
                        <td>{money(d.openingMinor)}</td>
                        <td>{money(d.expensesMinor)}</td>
                        <td>{money(d.appliedIncomeMinor)}</td>
                        <td>{money(d.lowMinor)}</td>
                        <td>{money(d.closingMinor)}</td>
                        <td>
                          {d.events.map((e) => (
                            <p key={e.id}>
                              {e.label} · {e.direction} · {e.certainty} ·{" "}
                              {money(e.amountMinor)} · {e.source}
                            </p>
                          ))}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </details>
            <section
              className="forecast-scenario"
              aria-label="Temporary what-if"
            >
              <h3>What if?</h3>
              <p>
                Compare a proposed purchase, delayed expected income or
                explicitly speculative upside. This does not save changes or
                move money.
              </p>
              <div className="forecast-grid">
                <label>
                  Proposed purchase (€; 0 for none)
                  <input
                    inputMode="decimal"
                    value={expense}
                    onChange={(e) => {
                      setExpense(e.target.value);
                      setComparison(null);
                    }}
                  />
                </label>
                <label>
                  Purchase date
                  <input
                    type="date"
                    value={expenseDate}
                    min={dateAfter(plan.inputs.baseline.date, 1)}
                    max={plan.inputs.endDate}
                    onChange={(e) => {
                      setExpenseDate(e.target.value);
                      setComparison(null);
                    }}
                  />
                </label>
                <label>
                  Delay expected income
                  <select
                    aria-label="Delay expected income"
                    value={income}
                    onChange={(e) => {
                      setIncome(e.target.value);
                      setUntil("");
                      setComparison(null);
                    }}
                  >
                    <option value="">No delay</option>
                    {plan.inputs.events
                      .filter(
                        (e) =>
                          e.direction === "INCOME" &&
                          e.certainty === "EXPECTED",
                      )
                      .map((e) => (
                        <option value={e.id} key={e.id}>
                          {e.label} · {e.date}
                        </option>
                      ))}
                  </select>
                </label>
                {income && (
                  <label>
                    Delayed receipt date
                    <input
                      type="date"
                      value={until}
                      max={plan.inputs.endDate}
                      onChange={(e) => {
                        setUntil(e.target.value);
                        setComparison(null);
                      }}
                    />
                  </label>
                )}
              </div>
              <label className="forecast-check">
                <input
                  type="checkbox"
                  checked={upside}
                  onChange={(e) => {
                    setUpside(e.target.checked);
                    setComparison(null);
                  }}
                />
                Include uncertain income in this upside scenario only
              </label>
              <button type="button" onClick={compare}>
                Compare what-if
              </button>
              {comparison && (
                <section aria-label="What-if comparison">
                  <h4>Unsaved comparison</h4>
                  <ul>
                    {comparison.warnings.map((w, i) => (
                      <li key={i}>{w}</li>
                    ))}
                  </ul>
                  <Summary
                    label="What-if projection"
                    value={comparison.scenario}
                  />
                  <p>
                    Saved-plan planning headroom:{" "}
                    {money(comparison.base.planningHeadroomMinor)}. What-if
                    headroom: {money(comparison.scenario.planningHeadroomMinor)}
                    .
                  </p>
                </section>
              )}
            </section>
          </>
        )}
      </fieldset>
    </section>
  );
}
