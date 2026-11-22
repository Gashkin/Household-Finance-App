import BankSandboxPanel from "./BankSandboxPanel";
import { useIdentity } from '../api/access';
import { useEffect, useRef, useState, type FormEvent } from "react";
import {
  ledger,
  euroText,
  type ActivityRow,
  type Position,
  type Activity,
  type Preview,
  type Audit,
  type Reconciliation,
} from "../api/ledger";
import { money } from "../domain/money";
import { todayInDublin } from "../domain/dates";

const header = "reference,date,description,amount,status,kind\n";
function newRow(date: string): ActivityRow {
  return {
    reference: "manual-" + crypto.randomUUID(),
    date,
    description: "",
    amountEuro: "",
    status: "BOOKED",
    kind: "ACTIVITY",
  };
}
function explain(value: string): string {
  try {
    const v = JSON.parse(value);
    if (v === null) return "—";
    if (v.row) return explain(JSON.stringify(v.row)) + (v.review?.reviewState ? ` · ${v.review.reviewState}` : "");
    if (v.result) return `${v.throughDate} · ${explain(JSON.stringify(v.result))}`;
    if (v.expectedMinor !== undefined) return `Booked balance before reconciliation: ${money(v.expectedMinor)}`;
    if (typeof v === "number") return money(v);
    if (Array.isArray(v))
      return v
        .map((r) =>
          r.description
            ? `${r.description}: €${r.amountEuro} (${r.status})`
            : r.statement
              ? `${r.name}: ${money(r.bookedMinor)}, statement: ${r.statement}`
              : "Reviewed facts",
        )
        .join("; ");
    if (v.differenceMinor !== undefined)
      return `Statement ${money(v.statementMinor)}; difference ${money(v.differenceMinor)}`;
    if (v.amountEuro !== undefined)
      return `${v.date} · ${v.description} · €${v.amountEuro} · ${v.status} · ${v.kind}`;
    if (v.openingEuro !== undefined)
      return `${v.name} · opening €${v.openingEuro} on ${v.openingDate}`;
    return "Reviewed evidence";
  } catch {
    return value;
  }
}
export default function MoneyPositionWorkspace({
  openDecision,
}: {
  openDecision: () => void;
}) {
  const identity=useIdentity();
  const [asOf, setAsOf] = useState(todayInDublin());
  const [position, setPosition] = useState<Position | null>(null);
  const [selected, setSelected] = useState("");
  const [busy, setBusy] = useState(false),
    [error, setError] = useState(""),
    [message, setMessage] = useState("");
  const generation = useRef(0);
  const [activity, setActivity] = useState<Activity[]>([]),
    [history, setHistory] = useState<Audit[]>([]),
    [publicationHistory, setPublicationHistory] = useState<Audit[]>([]);
  const [name, setName] = useState(""),
    [kind, setKind] = useState("CASH"),
    [openingDate, setOpeningDate] = useState(todayInDublin()),
    [opening, setOpening] = useState("0.00"),
    [openingSource, setOpeningSource] = useState("");
  const [csv, setCsv] = useState(header),
    [source, setSource] = useState(""),
    [preview, setPreview] = useState<Preview | null>(null);
  const [proposals, setProposals] = useState<
      {
        include: boolean;
        row: ActivityRow;
        disposition: string;
        possibleOverlap: boolean;
      }[]
    >([]),
    [reviewed, setReviewed] = useState(false);
  const [row, setRow] = useState<ActivityRow>(() => newRow(todayInDublin())),
    [editId, setEditId] = useState(""),
    [rowSource, setRowSource] = useState(""),
    [rowReviewed, setRowReviewed] = useState(false);
  const [statement, setStatement] = useState(""),
    [statementSource, setStatementSource] = useState(""),
    [reconciliation, setReconciliation] = useState<Reconciliation | null>(null),
    [adjust, setAdjust] = useState(false);
  const [reserve, setReserve] = useState("0.00"),
    [commitments, setCommitments] = useState<
      { label: string; amountEuro: string }[]
    >([]),
    [publishReviewed, setPublishReviewed] = useState(false),
    [requestId, setRequestId] = useState(() => crypto.randomUUID());
  const account = position?.accounts.find((a) => a.id === selected);
  async function refresh() {
    const version = ++generation.current;
    const p = await ledger<Position>("?asOf=" + asOf);
    if (version !== generation.current) return;
    setPosition(p);
    setSelected((old) =>
      p.accounts.some((a) => a.id === old) ? old : p.accounts[0]?.id || "",
    );
  }
  useEffect(() => {
    setPosition(null);
    setError("");
    void refresh().catch((e) => setError(e.message));
    return () => {
      generation.current++;
    };
  }, [asOf]);
  useEffect(() => {
    let live = true;
    setPreview(null);
    setProposals([]);
    setReconciliation(null);
    setStatement("");
    setStatementSource("");
    setAdjust(false);
    setEditId("");
    setRow(newRow(asOf));
    setRowSource("");
    setRowReviewed(false);
    setActivity([]);
    setHistory([]);
    setPublishReviewed(false);
    setRequestId(crypto.randomUUID());
    if (selected)
      void Promise.all([
        ledger<Activity[]>("/accounts/" + selected + "/activity"),
        ledger<Audit[]>("/accounts/" + selected + "/history"),
      ])
        .then(([a, h]) => {
          if (live) {
            setActivity(a);
            setHistory(h);
          }
        })
        .catch((e) => {
          if (live) setError(e.message);
        });
    return () => {
      live = false;
    };
  }, [selected, account?.revision, account?.reconciledDate, asOf]);
  async function run(action: () => Promise<void>) {
    setBusy(true);
    setError("");
    setMessage("");
    try {
      await action();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  function updateRow(field: keyof ActivityRow, value: string) {
    setRow((old) => ({ ...old, [field]: value }));
    setRowReviewed(false);
  }
  async function create(e: FormEvent) {
    e.preventDefault();
    await run(async () => {
      const a = await ledger<{ id: string }>("/accounts", {
        name,
        kind,
        openingDate,
        openingEuro: opening,
        source: openingSource,
      });
      await refresh();
      setSelected(a.id);
      setName("");
      setOpeningSource("");
      setMessage(
        "Account created. Review activity, then reconcile its booked closing balance.",
      );
    });
  }
  async function saveRow(e: FormEvent) {
    e.preventDefault();
    if (!account) return;
    await run(async () => {
      const path =
        "/accounts/" + selected + "/activity" + (editId ? "/" + editId : "");
      await ledger(
        path,
        editId
          ? { revision: account.revision, row, reason: rowSource }
          : {
              revision: account.revision,
              row,
              source: rowSource,
              acknowledgedOverlap: rowReviewed,
            },
        editId ? "PUT" : "POST",
      );
      await refresh();
      setEditId("");
      setRow(newRow(asOf));
      setRowSource("");
      setRowReviewed(false);
      setMessage(
        "Reviewed activity saved. Reconcile again before using this account in a decision.",
      );
    });
  }
  async function previewCsv() {
    await run(async () => {
      const p = await ledger<Preview>("/accounts/" + selected + "/preview", {
        csv,
        source,
      });
      setPreview(p);
      setProposals(
        p.proposals.map((x) => ({
          ...x,
          include:
            x.disposition !== "DUPLICATE" && x.disposition !== "CONFLICT",
        })),
      );
      setReviewed(false);
    });
  }
  async function approve() {
    if (!preview) return;
    await run(async () => {
      const result = await ledger<{
        added: number;
        duplicates: number;
        settled: number;
      }>("/accounts/" + selected + "/previews/" + preview.id + "/approve", {
        revision: preview.revision,
        rows: proposals.filter((p) => p.include).map((p) => p.row),
        acknowledgedOverlap: reviewed,
      });
      setPreview(null);
      setProposals([]);
      setReviewed(false);
      await refresh();
      setMessage(
        `Saved ${result.added} new activities; ${result.duplicates} duplicates skipped; ${result.settled} pending entries replaced by booked facts.`,
      );
    });
  }
  async function reconcile(commit: boolean) {
    if (!account) return;
    await run(async () => {
      const r = await ledger<Reconciliation>(
        "/accounts/" + selected + "/reconcile?asOf=" + asOf,
        {
          revision: account.revision,
          statementEuro: statement,
          source: statementSource,
          commit,
          acceptAdjustment: adjust,
        },
      );
      setReconciliation(r);
      if (commit) {
        await refresh();
        setMessage("Statement reconciliation saved with its evidence.");
      }
    });
  }
  function exportEdited() {
    setPublishReviewed(false);
    setRequestId(crypto.randomUUID());
  }
  async function publish() {
    if (!position) return;
    await run(async () => {
      await ledger("/publish", {
        requestId,
        asOf,
        revisions: Object.fromEntries(
          position.accounts.map((a) => [a.id, a.revision]),
        ),
        reserveEuro: reserve,
        commitments,
        acknowledged: publishReviewed,
      });
      openDecision();
    });
  }
  function edit(entry: Activity) {
    setEditId(entry.id);
    setRow({
      reference: entry.reference,
      date: entry.date,
      description: entry.description,
      amountEuro: euroText(entry.amountMinor),
      status: entry.status,
      kind: entry.kind,
    });
    setRowSource("");
    setRowReviewed(false);
    document
      .getElementById("ledger-entry-form")
      ?.scrollIntoView({ block: "start" });
  }
  return (
    <section className="money-workspace">
      <h2>Household money position</h2>
      <p>This limited view includes your own accounts and explicitly shared accounts. Other private accounts are excluded; these figures may be incomplete.</p>
      <p>
        Start with dated account evidence, review what moved, and check the
        result against a statement. All accounts here are in EUR.
      </p>
      {error && <p role="alert">{error}</p>}
      {message && <p role="status">{message}</p>}
      {busy && <p role="status">Saving or checking reviewed facts…</p>}
      <fieldset disabled={busy} className="ledger-controls">
        {(identity?.custodian??false)&&<BankSandboxPanel
          ledgerAccountId={selected}
          openingDate={account?.openingDate}
          onBusy={setBusy}
          onPreview={(value) => {
            setPreview(value.preview);
            setProposals(
              (value.preview?.proposals ?? []).map((p) => ({
                ...p,
                include:
                  p.disposition !== "DUPLICATE" && p.disposition !== "CONFLICT",
              })),
            );
            setReviewed(false);
            setMessage(
              value.preview
                ? "Bank activity fetched. Review proposed facts before approval."
                : "No bank transactions returned. Dated balance evidence is available; the ledger is unchanged.",
            );
            if (value.preview) setTimeout(
              () =>
                document
                  .getElementById("ledger-import-review")
                  ?.setAttribute("open", ""),
              0,
            );
          }}
        />}
        <button type="button" onClick={() => run(refresh)}>
          Reload reviewed position
        </button>
        <label>
          Position as at
          <input
            type="date"
            value={asOf}
            max={todayInDublin()}
            onChange={(e) => setAsOf(e.target.value)}
          />
        </label>
        {!position ? (
          <p>Loading reviewed position…</p>
        ) : (
          <>
            <div className="ledger-totals">
              <div>
                Booked cash<strong>{money(position.cashMinor)}</strong>
              </div>
              <div>
                Debt accounts (signed)
                <strong>{money(position.debtSignedMinor)}</strong>
              </div>
              <div>
                Net recorded position<strong>{money(position.netMinor)}</strong>
              </div>
              <div>
                Pending cash debits reserved
                <strong>{money(position.cashPendingDebitMinor)}</strong>
              </div>
            </div>
            <p>
              Reviewed activity since each account’s opening date: inflows{" "}
              {money(position.incomeMinor)}; spending{" "}
              {money(position.spendingMinor)}. Transfers and explicit balance
              adjustments are excluded from these activity totals. Borrowing
              limits are never cash.
            </p>
            {position.warnings.length > 0 && (
              <ul className="ledger-warnings">
                {position.warnings.map((w, i) => (
                  <li key={i}>{w}</li>
                ))}
              </ul>
            )}
            <details
              className="ledger-card"
              open={position.accounts.length === 0}
            >
              <summary>Add account evidence</summary>
              <form onSubmit={create} className="ledger-form">
                <label>
                  Account name
                  <input
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                    maxLength={80}
                    required
                  />
                </label>
                <label>
                  Account type
                  <select
                    value={kind}
                    onChange={(e) => setKind(e.target.value)}
                  >
                    <option value="CASH">Cash / current / savings</option>
                    <option value="DEBT">Debt / credit card / loan</option>
                  </select>
                </label>
                <label>
                  Opening date (start of day)
                  <input
                    type="date"
                    value={openingDate}
                    max={todayInDublin()}
                    onChange={(e) => setOpeningDate(e.target.value)}
                    required
                  />
                </label>
                <label>
                  Opening balance (€; debt owed is negative)
                  <input
                    inputMode="decimal"
                    value={opening}
                    onChange={(e) => setOpening(e.target.value)}
                    required
                  />
                </label>
                <label>
                  Opening evidence source
                  <input
                    value={openingSource}
                    onChange={(e) => setOpeningSource(e.target.value)}
                    maxLength={200}
                    required
                  />
                </label>
                <p>
                  The opening figure is before that day’s activity. Use the
                  previous day’s closing statement figure. Do not enter a credit
                  limit. A positive debt balance means an actual statement
                  credit or overpayment, never an unused borrowing limit; it is
                  excluded from spendable cash.
                </p>
                <button>Create ledger account</button>
              </form>
            </details>
            {position.accounts.length > 0 && (
              <>
                <label>
                  Review account
                  <select aria-label="Review account"
                    value={selected}
                    onChange={(e) => setSelected(e.target.value)}
                  >
                    {position.accounts.map((a) => (
                      <option key={a.id} value={a.id}>
                        {a.name} · {a.kind === "CASH" ? "cash" : "debt"}
                      </option>
                    ))}
                  </select>
                </label>
                {account && (
                  <>
                    <section
                      className="ledger-card"
                      aria-label="Account balance evidence"
                    >
                      <h3>{account.name}</h3>
                      <p>
                        Opening {money(account.openingMinor)} at start of{" "}
                        {account.openingDate} · {account.source}
                      </p>
                      <p>
                        Booked balance:{" "}
                        <strong>{money(account.bookedMinor)}</strong>. Pending
                        entries: {account.pendingEntries} (net{" "}
                        {money(account.pendingMinor)}); they are excluded from
                        the booked balance.
                      </p>
                      <p>
                        {account.reconciled
                          ? "Reconciled for this date and activity."
                          : "Unreconciled — activity alone does not prove the balance."}{" "}
                        {account.reconciledDate &&
                          `Last statement ${account.reconciledDate}: ${account.reconciledSource}`}
                      </p>
                    </section>
                    {!account.owned&&<p>This shared account is read-only. Its owner reviews activity and reconciliation.</p>}
                    <fieldset disabled={!account.owned} className="account-edit-controls"><legend>Owner activity tools</legend>
                    <details id="ledger-import-review" className="ledger-card">
                      <summary>Preview and review CSV activity</summary>
                      <p>
                        Use this exact header:{" "}
                        <code>
                          reference,date,description,amount,status,kind
                        </code>
                        . ISO dates; signed EUR amounts; BOOKED/PENDING;
                        ACTIVITY/TRANSFER. Stable references prevent re-import
                        duplication. Quote fields containing commas. Convert
                        bank-specific columns before import.
                      </p>
                      <label>
                        CSV file
                        <input
                          type="file"
                          accept=".csv,text/csv"
                          onChange={(e) => {
                            const f = e.target.files?.[0];
                            if (f)
                              void run(async () => {
                                if (f.size > 300_000)
                                  throw Error("CSV file is too large");
                                const text = new TextDecoder("utf-8", {
                                  fatal: true,
                                }).decode(await f.arrayBuffer());
                                setCsv(text);
                                setSource(f.name.slice(0, 200));
                                setPreview(null);
                              });
                          }}
                        />
                      </label>
                      <label htmlFor="ledger-csv">Activity CSV</label>
                      <textarea
                        id="ledger-csv"
                        rows={7}
                        value={csv}
                        onChange={(e) => {
                          setCsv(e.target.value);
                          setPreview(null);
                        }}
                        maxLength={100000}
                      />
                      <label>
                        CSV source reference
                        <input
                          value={source}
                          onChange={(e) => {
                            setSource(e.target.value);
                            setPreview(null);
                          }}
                          maxLength={200}
                        />
                      </label>
                      <button type="button" onClick={previewCsv}>
                        Preview CSV
                      </button>
                      {preview && (
                        <section aria-label="Review imported activity">
                          <h4>Review imported activity</h4>
                          <p>
                            Nothing has been saved yet. Preview expires{" "}
                            {new Date(preview.expiresAt).toLocaleTimeString()}.
                            Duplicates and conflicting booked references start
                            excluded; correct existing booked facts separately.
                          </p>
                          {proposals.map((p, index) => (
                            <fieldset
                              key={p.row.reference}
                              className="ledger-proposal"
                            >
                              <legend>
                                Row {index + 1}:{" "}
                                {p.disposition.replace("_", " ")}
                                {p.possibleOverlap
                                  ? " · Possible same-date/amount overlap"
                                  : ""}
                              </legend>
                              <label>
                                <input
                                  type="checkbox"
                                  aria-label={`Include import row ${index + 1}`}
                                  checked={p.include}
                                  onChange={(e) => {
                                    setProposals((old) =>
                                      old.map((r, i) =>
                                        i === index
                                          ? { ...r, include: e.target.checked }
                                          : r,
                                      ),
                                    );
                                    setReviewed(false);
                                  }}
                                />
                                Include this row
                              </label>
                              <span>Reference: {p.row.reference}</span>
                              {(
                                [
                                  "date",
                                  "description",
                                  "amountEuro",
                                  "status",
                                  "kind",
                                ] as const
                              ).map((field) => (
                                <label key={field}>
                                  {field === "amountEuro"
                                    ? "Amount (€)"
                                    : field}
                                  <input
                                    aria-label={`Import row ${index + 1} ${field}`}
                                    value={p.row[field]}
                                    onChange={(e) => {
                                      setProposals((old) =>
                                        old.map((r, i) =>
                                          i === index
                                            ? {
                                                ...r,
                                                row: {
                                                  ...r.row,
                                                  [field]: e.target.value,
                                                },
                                              }
                                            : r,
                                        ),
                                      );
                                      setReviewed(false);
                                    }}
                                  />
                                </label>
                              ))}
                            </fieldset>
                          ))}
                          <label className="ledger-check">
                            <input
                              type="checkbox"
                              checked={reviewed}
                              onChange={(e) => setReviewed(e.target.checked)}
                            />
                            I reviewed these facts and resolved duplicates or
                            overlaps with existing manual activity
                          </label>
                          <button
                            type="button"
                            disabled={
                              !reviewed || !proposals.some((p) => p.include)
                            }
                            onClick={approve}
                          >
                            Approve selected activity
                          </button>
                        </section>
                      )}
                    </details>
                    <section className="ledger-card" id="ledger-entry-form">
                      <h3>
                        {editId
                          ? "Correct saved activity"
                          : "Add reviewed activity manually"}
                      </h3>
                      <form onSubmit={saveRow} className="ledger-form">
                        <label>
                          Activity reference
                          <input
                            value={row.reference}
                            readOnly={!!editId}
                            onChange={(e) =>
                              updateRow("reference", e.target.value)
                            }
                            maxLength={100}
                            required
                          />
                        </label>
                        <label>
                          Activity date
                          <input
                            type="date"
                            value={row.date}
                            min={account.openingDate}
                            max={todayInDublin()}
                            onChange={(e) => updateRow("date", e.target.value)}
                            required
                          />
                        </label>
                        <label>
                          Activity description
                          <input
                            value={row.description}
                            onChange={(e) =>
                              updateRow("description", e.target.value)
                            }
                            maxLength={160}
                            required
                          />
                        </label>
                        <label>
                          Signed activity amount (€)
                          <input
                            inputMode="decimal"
                            value={row.amountEuro}
                            onChange={(e) =>
                              updateRow("amountEuro", e.target.value)
                            }
                            required
                          />
                        </label>
                        <label>
                          Activity state
                          <select
                            value={row.status}
                            onChange={(e) =>
                              updateRow("status", e.target.value)
                            }
                          >
                            <option>BOOKED</option>
                            <option>PENDING</option>
                            {editId && (
                              <option value="VOID">
                                VOID — cancelled or duplicate
                              </option>
                            )}
                          </select>
                        </label>
                        <label>
                          Activity kind
                          <select
                            value={row.kind}
                            onChange={(e) => updateRow("kind", e.target.value)}
                          >
                            <option value="ACTIVITY">Income / spending</option>
                            <option value="TRANSFER">
                              Transfer between own accounts
                            </option>
                          </select>
                        </label>
                        <label>
                          Activity source or correction reason
                          <input
                            value={rowSource}
                            onChange={(e) => {
                              setRowSource(e.target.value);
                              setRowReviewed(false);
                            }}
                            maxLength={200}
                            required
                          />
                        </label>
                        <label className="ledger-check">
                          <input
                            type="checkbox"
                            checked={rowReviewed}
                            onChange={(e) => setRowReviewed(e.target.checked)}
                          />
                          I checked the source and existing activity for
                          duplication
                        </label>
                        <p>
                          Negative means money leaving this account, positive
                          means entering. A credit-card purchase increases debt
                          with a negative amount; card payments are transfers.
                          Corrections retain previous facts and invalidate
                          reconciliation.
                        </p>
                        <button disabled={!rowReviewed}>
                          {editId
                            ? "Save correction"
                            : "Save reviewed activity"}
                        </button>
                        {editId && (
                          <button
                            type="button"
                            onClick={() => {
                              setEditId("");
                              setRow(newRow(asOf));
                              setRowReviewed(false);
                            }}
                          >
                            Cancel correction
                          </button>
                        )}
                      </form>
                    </section>
                    </fieldset>
                    <section
                      className="ledger-card"
                      aria-label="Reviewed account activity"
                    >
                      <h3>Reviewed account activity</h3>
                      <p>
                        Latest 500 saved entries; {account.entries} entries are
                        on/before the selected date. VOID entries stay visible
                        for traceability and do not affect balances.
                      </p>
                      <div className="ledger-table-wrap">
                        <table>
                          <thead>
                            <tr>
                              <th>Date</th>
                              <th>Reference / description</th>
                              <th>Amount</th>
                              <th>State / kind</th>
                              <th>Source</th>
                              <th>Correction</th>
                            </tr>
                          </thead>
                          <tbody>
                            {activity.map((e) => (
                              <tr key={e.id}>
                                <td>{e.date}</td>
                                <td>
                                  {e.reference}
                                  <br />
                                  {e.description}
                                </td>
                                <td>{money(e.amountMinor)}</td>
                                <td>
                                  {e.status} / {e.kind}
                                </td>
                                <td>{e.source}</td>
                                <td>
                                  {account.owned && e.kind !== "ADJUSTMENT" && (
                                    <button
                                      type="button"
                                      aria-label={"Correct " + e.reference}
                                      onClick={() => edit(e)}
                                    >
                                      Correct
                                    </button>
                                  )}
                                </td>
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      </div>
                    </section>
                    <fieldset disabled={!account.owned} className="account-edit-controls"><legend>Owner reconciliation tools</legend>
                    <section className="ledger-card">
                      <h3>Check against a statement</h3>
                      <p>
                        Compare the booked closing balance at the end of {asOf},
                        excluding pending authorisations and borrowing headroom.
                      </p>
                      <label>
                        Statement closing balance (€)
                        <input
                          inputMode="decimal"
                          value={statement}
                          onChange={(e) => {
                            setStatement(e.target.value);
                            setReconciliation(null);
                            setAdjust(false);
                          }}
                        />
                      </label>
                      <label>
                        Statement evidence source
                        <input
                          value={statementSource}
                          onChange={(e) => {
                            setStatementSource(e.target.value);
                            setReconciliation(null);
                            setAdjust(false);
                          }}
                          maxLength={200}
                        />
                      </label>
                      <button type="button" onClick={() => reconcile(false)}>
                        Check statement balance
                      </button>
                      {reconciliation && (
                        <section aria-label="Statement difference">
                          <p>
                            Expected booked balance:{" "}
                            {money(reconciliation.expectedMinor)}. Statement:{" "}
                            {money(reconciliation.statementMinor)}. Difference:{" "}
                            <strong>
                              {money(reconciliation.differenceMinor)}
                            </strong>
                            .
                          </p>
                          {reconciliation.differenceMinor !== 0 && (
                            <label className="ledger-check">
                              <input
                                type="checkbox"
                                checked={adjust}
                                onChange={(e) => setAdjust(e.target.checked)}
                              />
                              Record this difference as an explicit adjustment
                              with the statement source
                            </label>
                          )}
                          <button
                            type="button"
                            disabled={
                              reconciliation.applied ||
                              (reconciliation.differenceMinor !== 0 && !adjust)
                            }
                            onClick={() => reconcile(true)}
                          >
                            Confirm reconciliation
                          </button>
                        </section>
                      )}
                    </section>
                    </fieldset>
                    <details className="ledger-card">
                      <summary>Account correction and evidence history</summary>
                      {history.map((e) => (
                        <article key={e.id}>
                          <h4>{e.action.replace(/_/g, " ")}</h4>
                          <p>
                            {new Date(e.recordedAt).toLocaleString()} ·{" "}
                            {e.source}
                          </p>
                          <p>Previous: {explain(e.beforeJson)}</p>
                          <p>Reviewed: {explain(e.afterJson)}</p>
                        </article>
                      ))}
                    </details>
                  </>
                )}
                <section className="ledger-card">
                  <h3>Use reconciled cash in a debt decision</h3>
                  <p>
                    This saves a new cash snapshot and opens Debt decision. It
                    replaces the latest snapshot; enter unpaid commitments
                    below. Pending cash debits are included automatically once;
                    pending credits and all debt accounts are excluded from
                    cash.
                  </p>
                  <label>
                    Ledger protected reserve (€)
                    <input
                      value={reserve}
                      inputMode="decimal"
                      onChange={(e) => {
                        setReserve(e.target.value);
                        exportEdited();
                      }}
                    />
                  </label>
                  {commitments.map((c, index) => (
                    <div className="ledger-commitment" key={index}>
                      <label>
                        Unpaid commitment {index + 1}
                        <input
                          value={c.label}
                          maxLength={100}
                          onChange={(e) => {
                            setCommitments((old) =>
                              old.map((r, i) =>
                                i === index
                                  ? { ...r, label: e.target.value }
                                  : r,
                              ),
                            );
                            exportEdited();
                          }}
                        />
                      </label>
                      <label>
                        Commitment {index + 1} amount (€)
                        <input
                          value={c.amountEuro}
                          inputMode="decimal"
                          onChange={(e) => {
                            setCommitments((old) =>
                              old.map((r, i) =>
                                i === index
                                  ? { ...r, amountEuro: e.target.value }
                                  : r,
                              ),
                            );
                            exportEdited();
                          }}
                        />
                      </label>
                      <button
                        type="button"
                        onClick={() => {
                          setCommitments((old) =>
                            old.filter((_, i) => i !== index),
                          );
                          exportEdited();
                        }}
                      >
                        Remove commitment {index + 1}
                      </button>
                    </div>
                  ))}
                  <button
                    type="button"
                    disabled={commitments.length >= 30}
                    onClick={() => {
                      setCommitments((old) => [
                        ...old,
                        { label: "", amountEuro: "" },
                      ]);
                      exportEdited();
                    }}
                  >
                    Add unpaid commitment
                  </button>
                  <label className="ledger-check">
                    <input
                      type="checkbox"
                      checked={publishReviewed}
                      onChange={(e) => setPublishReviewed(e.target.checked)}
                    />
                    I checked reconciled cash and unpaid commitments against
                    pending debits to avoid counting payments twice
                  </label>
                  <button
                    type="button"
                    disabled={
                      !publishReviewed ||
                      !position.accounts.some((a) => a.kind === "CASH") ||
                      position.accounts.some(
                        (a) => a.kind === "CASH" && !a.reconciled,
                      )
                    }
                    onClick={publish}
                  >
                    Save position and open debt decision
                  </button>
                  <details>
                    <summary
                      onClick={() => {
                        void ledger<Audit[]>("/history")
                          .then(setPublicationHistory)
                          .catch((e) => setError(e.message));
                      }}
                    >
                      Published position provenance
                    </summary>
                    {publicationHistory.map((e) => (
                      <article key={e.id}>
                        <p>
                          {e.source} · {new Date(e.recordedAt).toLocaleString()}
                        </p>
                        <p>{explain(e.afterJson)}</p>
                      </article>
                    ))}
                  </details>
                </section>
              </>
            )}
          </>
        )}
      </fieldset>
    </section>
  );
}
