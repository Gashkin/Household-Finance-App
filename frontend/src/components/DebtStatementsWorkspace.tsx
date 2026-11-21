import { useEffect, useRef, useState } from "react";
import { type LedgerAccount, type Audit } from "../api/ledger";
import { money } from "../domain/money";
type Rate = { from: string; annualPercent: string };
type Terms = {
  accountId: string;
  name: string;
  kind: string;
  paymentAccountId: string;
  creditLimitEuro: string;
  monthlyPaymentEuro: string;
  monthlyFeeEuro: string;
  maturity: string;
  extraLimitEuro: string;
  extraFeeEuro: string;
  lenderAssumptions: string;
  rates: Rate[];
};
type Statement = {
  id: string;
  start: string;
  end: string;
  due: string;
  source: string;
  complete: boolean;
  openingEuro: string;
  borrowingEuro: string;
  interestEuro: string;
  feesEuro: string;
  paidEuro: string;
  balanceEuro: string;
  minimumEuro: string;
  supersedes?: string | null;
};
type Debt = { id: string; terms: Terms; statements: Statement[] };
type Workspace = {
  today: string;
  state: { revision: number; debts: Debt[] };
  rows: {
    debt: Debt;
    outstandingMinor: number;
    statementDifferenceMinor: number;
    reconciled: boolean;
    remainingPaymentMinor: number;
    paymentCashMinor: number;
    warnings: string[];
  }[];
  accounts: LedgerAccount[];
  warnings: string[];
};
type Payment = {
  date: string;
  openingMinor: number;
  annualPercent: string;
  principalMinor: number;
  interestMinor: number;
  feesMinor: number;
  paymentMinor: number;
  closingMinor: number;
};
type Schedule = {
  payments: Payment[];
  interestMinor: number;
  feesMinor: number;
  totalPaidMinor: number;
  remainingMinor: number;
  paidOff: boolean;
};
type Comparison = {
  name: string;
  evidenceDate: string;
  reconciled: boolean;
  baseline: Schedule;
  extra: Schedule;
  netSavingMinor: number;
  remainingCashMinor: number;
  reserveShortfallMinor: number;
  warnings: string[];
  assumptions: string[];
};
class DebtError extends Error {
  constructor(
    message: string,
    public status = 0,
  ) {
    super(message);
  }
}
async function request<T>(path = "", body?: unknown): Promise<T> {
  let response: Response;
  try {
    response = await fetch("/api/ledger/debts" + path, {
      method: body === undefined ? "GET" : "POST",
      headers:
        body === undefined ? undefined : { "Content-Type": "application/json" },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new DebtError(
      "Request status is unknown. Check save status before retrying.",
    );
  }
  if (!response.ok) {
    let message = "Debt service unavailable";
    try {
      message = (await response.json()).error || message;
    } catch {
      /* access response */
    }
    throw new DebtError(message, response.status);
  }
  return response.json();
}
export default function DebtStatementsWorkspace({
  openMoney,
  openReview,
  openForecast,
}: {
  openMoney: () => void;
  openReview: () => void;
  openForecast: () => void;
}) {
  const [w, setW] = useState<Workspace | null>(null),
    [draft, setDraft] = useState<{
      id: string;
      terms: Terms;
      statement: Statement | null;
      revision: number;
    } | null>(null),
    [reason, setReason] = useState(""),
    [busy, setBusy] = useState(false),
    [error, setError] = useState(""),
    [message, setMessage] = useState(""),
    [denied, setDenied] = useState(false),
    [pending, setPending] = useState<string | null>(null),
    [history, setHistory] = useState<Audit[]>([]),
    [selected, setSelected] = useState(""),
    [extra, setExtra] = useState("0.00"),
    [reserve, setReserve] = useState("0.00"),
    [comparison, setComparison] = useState<Comparison | null>(null);
  const alive = useRef(true),
    alert = useRef<HTMLDivElement>(null),
    form = useRef<HTMLFormElement>(null);
  function failure(e: unknown) {
    if (!alive.current) return;
    const x = e as DebtError;
    setError(x.message);
    if (x.status === 401 || x.status === 403) {
      setDenied(true);
      setW(null);
      setDraft(null);
      setHistory([]);
      setComparison(null);
      setPending(null);
      setReason("");
    }
    queueMicrotask(() => alert.current?.focus());
  }
  async function load() {
    const result = await request<Workspace>();
    if (alive.current) {
      setW(result);
      setComparison(null);
    }
    return result;
  }
  useEffect(() => {
    alive.current = true;
    void load().catch(failure);
    return () => {
      alive.current = false;
    };
  }, []);
  useEffect(() => {
    if (draft) form.current?.querySelector("input")?.focus();
  }, [draft?.id]);
  async function run(action: () => Promise<void>) {
    setBusy(true);
    setError("");
    setMessage("");
    try {
      await action();
    } catch (e) {
      failure(e);
    } finally {
      if (alive.current) setBusy(false);
    }
  }
  function blankStatement(): Statement {
    return {
      id: crypto.randomUUID(),
      start: w!.today,
      end: w!.today,
      due: w!.today,
      source: "",
      complete: false,
      openingEuro: "",
      borrowingEuro: "",
      interestEuro: "",
      feesEuro: "",
      paidEuro: "",
      balanceEuro: "",
      minimumEuro: "",
    };
  }
  function edit(d?: Debt, add = false, correct = false) {
    if (!w) return;
    setReason("");
    setDraft({
      id: d?.id ?? crypto.randomUUID(),
      revision: w.state.revision,
      terms: d
        ? structuredClone(d.terms)
        : {
            accountId: w.accounts.find((a) => a.kind === "DEBT")?.id ?? "",
            name: "",
            kind: "CARD",
            paymentAccountId:
              w.accounts.find((a) => a.kind === "CASH")?.id ?? "",
            creditLimitEuro: "0.00",
            monthlyPaymentEuro: "0.00",
            monthlyFeeEuro: "0.00",
            maturity: w.today,
            extraLimitEuro: "0.00",
            extraFeeEuro: "0.00",
            lenderAssumptions: "",
            rates: [{ from: w.today, annualPercent: "0" }],
          },
      statement:
        correct && d
          ? {
              ...d.statements[d.statements.length - 1],
              id: crypto.randomUUID(),
              supersedes: d.statements[d.statements.length - 1].id,
            }
          : !d || add
            ? blankStatement()
            : null,
    });
  }
  function term<K extends keyof Terms>(key: K, value: Terms[K]) {
    setDraft((d) => (d ? { ...d, terms: { ...d.terms, [key]: value } } : d));
    setComparison(null);
  }
  function statement<K extends keyof Statement>(key: K, value: Statement[K]) {
    setDraft((d) =>
      d && d.statement
        ? { ...d, statement: { ...d.statement, [key]: value } }
        : d,
    );
  }
  async function save() {
    if (!draft) return;
    const op = crypto.randomUUID();
    setPending(op);
    try {
      await request("", { ...draft, operationId: op, reason });
      if (!alive.current) return;
      setPending(null);
      setDraft(null);
      await load();
      setMessage("Debt evidence saved. Ledger balances are unchanged.");
    } catch (e) {
      const x = e as DebtError;
      if (x.status >= 400 && x.status < 500) setPending(null);
      throw e;
    }
  }
  async function download() {
    const report = await request<unknown>(
      "/report",
      comparison
        ? {
            revision: w!.state.revision,
            debtId: selected,
            extraEuro: extra,
            reserveEuro: reserve,
          }
        : undefined,
    );
    const blob = new Blob([JSON.stringify(report, null, 2)], {
        type: "application/json",
      }),
      url = URL.createObjectURL(blob),
      a = document.createElement("a");
    a.href = url;
    a.download = "debt-statement-report.json";
    a.click();
    URL.revokeObjectURL(url);
  }
  if (denied)
    return (
      <section>
        <h2>Cards, loans &amp; mortgages</h2>
        <p role="alert">
          Access changed. Private debt information has been cleared.
        </p>
      </section>
    );
  return (
    <section className="debt-statements goals-workspace">
      <h2>Cards, loans &amp; mortgages</h2>
      <p>
        Track what the lender says, compare it with reviewed activity and plan
        repayments. Saving a statement never moves money.
      </p>
      {error && (
        <div ref={alert} tabIndex={-1} role="alert">
          {error}
          {draft && (
            <p>
              Your draft is retained. Refresh saved evidence and compare before
              editing again.
            </p>
          )}
        </div>
      )}
      {message && <p role="status">{message}</p>}
      <div className="goal-actions">
        <button
          disabled={busy}
          onClick={() =>
            void run(async () => {
              await load();
              setMessage("Saved evidence refreshed; draft retained.");
            })
          }
        >
          Refresh debt evidence
        </button>
        <button
          disabled={busy || !w || !!draft || !!pending}
          onClick={() => edit()}
        >
          Add debt statement
        </button>
        <button onClick={openMoney}>Open account reconciliation</button>
        <button onClick={openReview}>Open activity review</button>
        <button onClick={openForecast}>Open repayment outlook</button>
      </div>
      {!w && !error && <p role="status">Loading statement evidence…</p>}
      {w && (
        <>
          {w.warnings.map((n) => (
            <p key={n}>{n}</p>
          ))}
          {!w.rows.length && (
            <p>
              No debt statements yet. Create a debt account and cash payment
              account in Money position, then add your first statement here.
            </p>
          )}
          <div className="goal-list">
            {w.rows.map((r) => {
              const d = r.debt,
                s = d.statements[d.statements.length - 1];
              return (
                <article key={d.id}>
                  <h3>{d.terms.name}</h3>
                  <p>
                    {d.terms.kind} ·{" "}
                    {r.reconciled
                      ? "Statement reconciled"
                      : "Evidence needs review"}
                  </p>
                  <dl>
                    <dt>Cycle</dt>
                    <dd>
                      {s.start} to {s.end}
                    </dd>
                    <dt>
                      Statement amount owed (negative means lender credit)
                    </dt>
                    <dd>{s.balanceEuro} EUR</dd>
                    <dt>Current booked outstanding</dt>
                    <dd>{money(r.outstandingMinor)}</dd>
                    <dt>Statement / ledger difference</dt>
                    <dd>{money(r.statementDifferenceMinor)}</dd>
                    <dt>Due date</dt>
                    <dd>{s.due}</dd>
                    <dt>Minimum payment</dt>
                    <dd>{s.minimumEuro || "Unknown"} EUR</dd>
                    <dt>Remaining expected repayment</dt>
                    <dd>{money(r.remainingPaymentMinor)}</dd>
                    <dt>Payment account cash after pending debits</dt>
                    <dd>{money(r.paymentCashMinor)}</dd>
                    <dt>Credit limit (never cash)</dt>
                    <dd>{d.terms.creditLimitEuro} EUR</dd>
                  </dl>
                  {r.warnings.map((n) => (
                    <p key={n}>{n}</p>
                  ))}
                  <p>Lender assumptions: {d.terms.lenderAssumptions}</p>
                  <button
                    disabled={busy || !!draft || !!pending}
                    onClick={() => edit(d)}
                  >
                    Edit terms for {d.terms.name}
                  </button>
                  <button
                    disabled={busy || !!draft || !!pending}
                    onClick={() => edit(d, true)}
                  >
                    Next statement for {d.terms.name}
                  </button>
                  <button
                    disabled={busy || !!draft || !!pending}
                    onClick={() => edit(d, false, true)}
                  >
                    Correct statement for {d.terms.name}
                  </button>
                  <details>
                    <summary>Statement and dated rate history</summary>
                    {d.terms.rates.map((x) => (
                      <p key={x.from}>
                        {x.from}: {x.annualPercent}%
                      </p>
                    ))}
                    {d.statements.map((x) => (
                      <p key={x.id}>
                        {x.start} – {x.end}: {x.balanceEuro} EUR ·{" "}
                        {x.complete ? "Complete" : "Incomplete"} · {x.source}
                      </p>
                    ))}
                  </details>
                </article>
              );
            })}
          </div>
          {draft && (
            <form
              ref={form}
              onSubmit={(e) => {
                e.preventDefault();
                void run(save);
              }}
            >
              <fieldset disabled={busy || !!pending}>
                <legend>Debt terms and evidence</legend>
                <label>
                  Debt name
                  <input
                    required
                    maxLength={100}
                    value={draft.terms.name}
                    onChange={(e) => term("name", e.target.value)}
                  />
                </label>
                <label>
                  Debt type
                  <select
                    value={draft.terms.kind}
                    disabled={w.state.debts.some((d) => d.id === draft.id)}
                    onChange={(e) => term("kind", e.target.value)}
                  >
                    <option value="CARD">Credit card</option>
                    <option value="LOAN">Loan</option>
                    <option value="MORTGAGE">Mortgage</option>
                  </select>
                </label>
                <label>
                  Debt account
                  <select
                    required
                    value={draft.terms.accountId}
                    disabled={w.state.debts.some((d) => d.id === draft.id)}
                    onChange={(e) => term("accountId", e.target.value)}
                  >
                    <option value="">Choose a debt account</option>
                    {w.accounts
                      .filter((a) => a.kind === "DEBT")
                      .map((a) => (
                        <option key={a.id} value={a.id}>
                          {a.name}
                        </option>
                      ))}
                  </select>
                </label>
                <label>
                  Repayment cash account
                  <select
                    required
                    value={draft.terms.paymentAccountId}
                    onChange={(e) => term("paymentAccountId", e.target.value)}
                  >
                    <option value="">Choose a cash account</option>
                    {w.accounts
                      .filter((a) => a.kind === "CASH")
                      .map((a) => (
                        <option key={a.id} value={a.id}>
                          {a.name}
                        </option>
                      ))}
                  </select>
                </label>
                {(
                  [
                    "creditLimitEuro",
                    "monthlyPaymentEuro",
                    "monthlyFeeEuro",
                    "extraLimitEuro",
                    "extraFeeEuro",
                  ] as const
                ).map((key, i) => (
                  <label key={key}>
                    {
                      [
                        "Credit limit EUR",
                        "Contractual monthly payment EUR",
                        "Monthly lender fee EUR",
                        "Allowed immediate extra principal EUR",
                        "Lender fee for extra payment EUR",
                      ][i]
                    }
                    <input
                      required
                      inputMode="decimal"
                      value={draft.terms[key]}
                      onChange={(e) => term(key, e.target.value)}
                    />
                  </label>
                ))}
                <label>
                  Contractual maturity / card planning end
                  <input
                    required
                    type="date"
                    value={draft.terms.maturity}
                    onChange={(e) => term("maturity", e.target.value)}
                  />
                </label>
                <label>
                  Lender rules and assumptions
                  <textarea
                    required
                    maxLength={1000}
                    value={draft.terms.lenderAssumptions}
                    onChange={(e) => term("lenderAssumptions", e.target.value)}
                  />
                </label>
                <fieldset>
                  <legend>Dated annual rates</legend>
                  <p>
                    Keep existing rates. Append known changes; unentered future
                    rates remain assumptions.
                  </p>
                  {draft.terms.rates.map((rate, i) => (
                    <div key={i}>
                      <label>
                        Rate {i + 1} effective date
                        <input
                          required
                          type="date"
                          value={rate.from}
                          disabled={
                            i <
                            (w.state.debts.find((d) => d.id === draft.id)?.terms
                              .rates.length ?? 0)
                          }
                          onChange={(e) =>
                            term(
                              "rates",
                              draft.terms.rates.map((x, j) =>
                                j === i ? { ...x, from: e.target.value } : x,
                              ),
                            )
                          }
                        />
                      </label>
                      <label>
                        Rate {i + 1} annual percent
                        <input
                          required
                          inputMode="decimal"
                          value={rate.annualPercent}
                          disabled={
                            i <
                            (w.state.debts.find((d) => d.id === draft.id)?.terms
                              .rates.length ?? 0)
                          }
                          onChange={(e) =>
                            term(
                              "rates",
                              draft.terms.rates.map((x, j) =>
                                j === i
                                  ? { ...x, annualPercent: e.target.value }
                                  : x,
                              ),
                            )
                          }
                        />
                      </label>
                    </div>
                  ))}
                  <button
                    type="button"
                    onClick={() =>
                      term("rates", [
                        ...draft.terms.rates,
                        { from: w.today, annualPercent: "0" },
                      ])
                    }
                  >
                    Append dated rate
                  </button>
                </fieldset>
                {draft.statement && (
                  <fieldset>
                    <legend>
                      New statement (saved cycles remain unchanged)
                    </legend>
                    <p>
                      Amounts owed are positive; a lender credit is negative.
                      Borrowing includes purchases or drawdowns. Leave unknown
                      components blank and keep the statement incomplete.
                    </p>
                    {(["start", "end", "due"] as const).map((key, i) => (
                      <label key={key}>
                        {["Cycle start", "Cycle end", "Payment due"][i]}
                        <input
                          required
                          type="date"
                          value={draft.statement![key]}
                          onChange={(e) => statement(key, e.target.value)}
                        />
                      </label>
                    ))}
                    {(
                      [
                        "openingEuro",
                        "borrowingEuro",
                        "interestEuro",
                        "feesEuro",
                        "paidEuro",
                        "balanceEuro",
                        "minimumEuro",
                      ] as const
                    ).map((key, i) => (
                      <label key={key}>
                        {
                          [
                            "Opening amount owed EUR",
                            "Purchases / drawdown EUR",
                            "Statement interest EUR",
                            "Statement fees EUR",
                            "Statement repayments EUR",
                            "Closing amount owed EUR",
                            "Minimum payment EUR",
                          ][i]
                        }
                        <input
                          required={
                            key === "balanceEuro" || draft.statement!.complete
                          }
                          inputMode="decimal"
                          value={draft.statement![key]}
                          onChange={(e) => statement(key, e.target.value)}
                        />
                      </label>
                    ))}
                    <label>
                      Statement source
                      <input
                        required
                        maxLength={200}
                        value={draft.statement.source}
                        onChange={(e) => statement("source", e.target.value)}
                      />
                    </label>
                    <label>
                      <input
                        type="checkbox"
                        checked={draft.statement.complete}
                        onChange={(e) =>
                          statement("complete", e.target.checked)
                        }
                      />
                      All statement components are complete
                    </label>
                  </fieldset>
                )}
                <label>
                  Reason for debt change
                  <input
                    required
                    maxLength={200}
                    value={reason}
                    onChange={(e) => setReason(e.target.value)}
                  />
                </label>
                {draft.revision !== w.state.revision && (
                  <p role="alert">
                    Saved evidence changed. Compare this draft with the saved
                    cards, then reopen the editor.
                  </p>
                )}
                <button
                  type="submit"
                  disabled={draft.revision !== w.state.revision}
                >
                  Save debt evidence
                </button>
                <button type="button" onClick={() => setDraft(null)}>
                  Discard debt draft
                </button>
              </fieldset>
            </form>
          )}
          {pending && (
            <div role="status">
              <p>
                Check whether the save completed before editing or retrying.
              </p>
              <button
                disabled={busy}
                onClick={() =>
                  void run(async () => {
                    const result = await request<{ completed: boolean }>(
                      "/operations/" + pending,
                    );
                    setPending(null);
                    if (result.completed) setDraft(null);
                    await load();
                    setMessage(
                      result.completed
                        ? "Saved debt operation confirmed."
                        : "Operation not saved. Compare your retained draft with current evidence before retrying.",
                    );
                  })
                }
              >
                Check debt save status
              </button>
            </div>
          )}
          <form
            onSubmit={(e) => {
              e.preventDefault();
              void run(async () => {
                setComparison(
                  await request<Comparison>("/compare", {
                    revision: w.state.revision,
                    debtId: selected,
                    extraEuro: extra,
                    reserveEuro: reserve,
                  }),
                );
              });
            }}
          >
            <fieldset disabled={busy || !!draft || !!pending}>
              <legend>Compare loan or mortgage repayment</legend>
              <label>
                Debt to compare
                <select
                  required
                  value={selected}
                  onChange={(e) => {
                    setSelected(e.target.value);
                    setComparison(null);
                  }}
                >
                  <option value="">Choose a loan or mortgage</option>
                  {w.state.debts
                    .filter((d) => d.terms.kind !== "CARD")
                    .map((d) => (
                      <option key={d.id} value={d.id}>
                        {d.terms.name}
                      </option>
                    ))}
                </select>
              </label>
              <label>
                Extra principal EUR
                <input
                  required
                  inputMode="decimal"
                  value={extra}
                  onChange={(e) => {
                    setExtra(e.target.value);
                    setComparison(null);
                  }}
                />
              </label>
              <label>
                Minimum payment-account reserve EUR
                <input
                  required
                  inputMode="decimal"
                  value={reserve}
                  onChange={(e) => {
                    setReserve(e.target.value);
                    setComparison(null);
                  }}
                />
              </label>
              <button>Compare repayment options</button>
            </fieldset>
          </form>
          {comparison && (
            <article>
              <h3>{comparison.name} repayment comparison</h3>
              <p>
                {comparison.reconciled
                  ? "Reconciled statement"
                  : "Illustrative evidence"}{" "}
                · Statement {comparison.evidenceDate}
              </p>
              <p>
                Net interest and fee saving: {money(comparison.netSavingMinor)}{" "}
                · Cash after extra and fee:{" "}
                {money(comparison.remainingCashMinor)} · Reserve shortfall:{" "}
                {money(comparison.reserveShortfallMinor)}
              </p>
              {comparison.warnings.map((n) => (
                <p key={n}>{n}</p>
              ))}
              {comparison.assumptions.map((n) => (
                <p key={n}>{n}</p>
              ))}
              {(["baseline", "extra"] as const).map((key) => (
                <details key={key}>
                  <summary>
                    {key === "baseline"
                      ? "Contractual baseline"
                      : "With immediate extra principal"}{" "}
                    · {comparison[key].payments.length} payments ·{" "}
                    {comparison[key].paidOff ? "Paid off" : "Balloon remains"}
                  </summary>
                  <p>
                    Interest {money(comparison[key].interestMinor)} · Fees{" "}
                    {money(comparison[key].feesMinor)} · Remaining{" "}
                    {money(comparison[key].remainingMinor)}
                  </p>
                  <div className="debt-table">
                    <table>
                      <thead>
                        <tr>
                          {[
                            "Date",
                            "Opening",
                            "Rate",
                            "Principal",
                            "Interest",
                            "Fees",
                            "Payment",
                            "Closing",
                          ].map((h) => (
                            <th key={h}>{h}</th>
                          ))}
                        </tr>
                      </thead>
                      <tbody>
                        {comparison[key].payments.map((p) => (
                          <tr key={p.date}>
                            <td>{p.date}</td>
                            <td>{money(p.openingMinor)}</td>
                            <td>{p.annualPercent}%</td>
                            <td>{money(p.principalMinor)}</td>
                            <td>{money(p.interestMinor)}</td>
                            <td>{money(p.feesMinor)}</td>
                            <td>{money(p.paymentMinor)}</td>
                            <td>{money(p.closingMinor)}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </details>
              ))}
            </article>
          )}
          <button
            disabled={busy || !!draft || !!pending}
            onClick={() => void run(download)}
          >
            Download debt report
          </button>
          <button
            disabled={busy}
            onClick={() =>
              void run(async () =>
                setHistory(await request<Audit[]>("/history")),
              )
            }
          >
            Load debt history
          </button>
          {history.map((h) => (
            <details key={h.id}>
              <summary>
                {h.recordedAt} · {h.source}
              </summary>
              <pre>{h.beforeJson}</pre>
              <pre>{h.afterJson}</pre>
            </details>
          ))}
        </>
      )}
    </section>
  );
}
