import { useEffect, useState } from "react";
import {
  sandbox,
  completeSandboxCallback,
  type SandboxStatus,
  type SandboxBank,
  type SandboxFetched,
} from "../api/bankSandbox";
import { todayInDublin } from "../domain/dates";
export default function BankSandboxPanel({
  ledgerAccountId,
  openingDate,
  onPreview,
  onBusy,
}: {
  ledgerAccountId: string;
  openingDate?: string;
  onPreview: (value: SandboxFetched) => void;
  onBusy: (value: boolean) => void;
}) {
  const [status, setStatus] = useState<SandboxStatus | null>(null),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false);
  const [banks, setBanks] = useState<SandboxBank[]>([]),
    [bank, setBank] = useState(""),
    [account, setAccount] = useState(""),
    [consent, setConsent] = useState(false),
    [forget, setForget] = useState(false),
    [revoked, setRevoked] = useState(false);
  const [from, setFrom] = useState(openingDate || todayInDublin()),
    [to, setTo] = useState(todayInDublin()),
    [balance, setBalance] = useState<SandboxFetched | null>(null);
  useEffect(() => {
    setFrom(openingDate || todayInDublin());
    setBalance(null);
  }, [ledgerAccountId, openingDate]);
  useEffect(() => {
    let live = true;
    const done = completeSandboxCallback();
    (done || sandbox<SandboxStatus>("status"))
      .then((value) => {
        if (live) setStatus(value);
      })
      .catch(async (e) => {
        if (live) setError(e.message);
        // A failed/cancelled exchange must not hide the controls for a fresh connection.
        try {
          const current = await sandbox<SandboxStatus>("status");
          if (live) setStatus(current);
        } catch { /* keep the original failure; the local backend may be unavailable */ }
      });
    return () => {
      live = false;
    };
  }, []);
  useEffect(() => {
    if (status && !status.accounts.some((a) => a.handle === account))
      setAccount(status.accounts[0]?.handle || "");
  }, [status]);
  async function run(action: () => Promise<void>) {
    setBusy(true);
    onBusy(true);
    setError("");
    try {
      await action();
    } catch (e) {
      setError((e as Error).message);
      try {
        setStatus(await sandbox<SandboxStatus>("status"));
      } catch {
        /* preserve original failure */
      }
    } finally {
      setBusy(false);
      onBusy(false);
    }
  }
  async function load() {
    await run(async () => {
      const result = await sandbox<SandboxBank[]>("banks", {});
      setBanks(result);
      setBank(result[0]?.country || "");
    });
  }
  async function connect() {
    await run(async () => {
      const selected = banks.find((b) => b.country === bank);
      if (!selected) throw Error("Select a listed Mock ASPSP");
      const response = await sandbox<{ url: string }>("start", {
        name: selected.name,
        country: selected.country,
        acknowledged: consent,
      });
      location.assign(response.url);
    });
  }
  async function fetchPreview() {
    await run(async () => {
      const value = await sandbox<SandboxFetched>("preview", {
        accountHandle: account,
        ledgerAccountId,
        from,
        to,
        acknowledged: consent,
      });
      setBalance(value);
      setStatus(await sandbox<SandboxStatus>("status"));
      onPreview(value);
    });
  }
  const production = status?.environment === "PRODUCTION";
  return (
    <section
      className="ledger-card"
      aria-label={production ? "AIB bank connection" : "Enable Banking sandbox connection"}
    >
      <h3>{production ? "AIB bank connection" : "Enable Banking sandbox connection"}</h3>
      <p>
        {production ? <>Read-only balances and transactions through Enable Banking for your own AIB accounts.
          Choose the accounts you authorise at the bank. No payments. Credit-card coverage is unverified.
          Manual and CSV entry remain available.</> : <>Read-only connection to synthetic <strong>Mock ASPSP</strong> accounts.
          Production applications, real AIB access and payment operations are blocked.</>}
      </p>
      {error && <p role="alert">{error}</p>}
      {!status ? (
        <p>Checking local connector status…</p>
      ) : (
        <>
          <p>
            Environment: {status.environment}. Connection:{" "}
            <strong>{status.state}</strong>.{" "}
            {status.expiresAt &&
              `Expires ${new Date(status.expiresAt).toLocaleString()}.`}
          </p>
          {!status.configured && (
            <p>
              Configure the sandbox app ID and an owner-only private key file on
              the backend machine, outside this repository. Use the local setup
              instructions in{" "}
              <code>product/stories/enable-banking-sandbox.md</code>. No key is
              entered or displayed in this browser.
            </p>
          )}
          <p>
            Registered redirect must be exactly <code>{status.callback}</code>.{" "}
            {production ? "Connection references stay encrypted on this device across restarts and are excluded from recovery backups. New-device recovery requires reconnecting. Refresh is manual, limited to four attempts per day across the connection. No background refresh or AI forwarding." : "Credentials and provider session references remain server-side and are not saved in backups or browser storage. Restarting the backend requires reconnecting."}
          </p>
          <fieldset
            disabled={busy || !status.configured}
            className="ledger-controls"
          >
            <label className="ledger-check">
              <input
                type="checkbox"
                checked={consent}
                onChange={(e) => setConsent(e.target.checked)}
              />
              {production ? "I authorise Enable Banking to access only my selected AIB accounts, balances and transactions, and retain reviewed records locally" : "I will use only synthetic sandbox accounts and allow read-only account, balance and transaction access"}
            </label>
            {status.state === "DISCONNECTED" ? (
              <>
                <button type="button" disabled={!consent} onClick={load}>
                  {production ? "Check AIB production application" : "Check sandbox application and load mock banks"}
                </button>
                {banks.length > 0 && (
                  <>
                    <label>
                      Sandbox bank
                      <select
                        value={bank}
                        onChange={(e) => setBank(e.target.value)}
                      >
                        {banks.map((b) => (
                          <option key={b.country} value={b.country}>
                            {b.name} · {b.country}
                          </option>
                        ))}
                      </select>
                    </label>
                    <button type="button" disabled={!consent} onClick={connect}>
                      {production ? "Connect my AIB accounts" : "Connect to Enable Banking sandbox"}
                    </button>
                  </>
                )}
              </>
            ) : (
              <>
                <label>
                  {production ? "Authorized bank account" : "Authorized sandbox account"}
                  <select
                    value={account}
                    onChange={(e) => {
                      setAccount(e.target.value);
                      setBalance(null);
                    }}
                  >
                    {status.accounts.map((a) => (
                      <option key={a.handle} value={a.handle}>
                        {a.label} · {a.kind} · {a.currency}
                      </option>
                    ))}
                  </select>
                </label>
                <p>
                  Map the selected {production ? "bank" : "synthetic"} account to the local ledger selected below. Create a matching cash/debt ledger with dated
                  opening evidence first. Reconnecting must reuse that same
                  ledger.
                </p>
                <label>
                  {production ? "Bank activity from" : "Sandbox activity from"}
                  <input
                    type="date"
                    value={from}
                    min={openingDate}
                    max={to}
                    onChange={(e) => setFrom(e.target.value)}
                  />
                </label>
                <label>
                  {production ? "Bank activity through" : "Sandbox activity through"}
                  <input
                    type="date"
                    value={to}
                    min={from}
                    max={todayInDublin()}
                    onChange={(e) => setTo(e.target.value)}
                  />
                </label>
                <button
                  type="button"
                  disabled={
                    status.state !== "CONNECTED" ||
                    !consent ||
                    !ledgerAccountId ||
                    !account
                  }
                  onClick={fetchPreview}
                >
                  {production ? "Fetch bank activity for review" : "Fetch sandbox activity for review"}
                </button>
                <button
                  type="button"
                  onClick={() =>
                    run(async () => {
                      setStatus(await sandbox<SandboxStatus>("disconnect", {}));
                      setBalance(null);
                      setConsent(false);
                    })
                  }
                >
                  {production ? "Disconnect and revoke bank session" : "Disconnect and revoke sandbox session"}
                </button>
                <label className="ledger-check">
                  <input
                    type="checkbox"
                    checked={forget}
                    onChange={(e) => setForget(e.target.checked)}
                  />
                  I understand local forgetting does not revoke provider consent
                  or erase reviewed ledger records
                </label>
                <button
                  type="button"
                  disabled={!forget || status.state === "REVOCATION_PENDING"}
                  onClick={() =>
                    run(async () => {
                      setStatus(
                        await sandbox<SandboxStatus>("forget", {
                          acknowledged: forget,
                        }),
                      );
                      setBalance(null);
                      setConsent(false);
                      setForget(false);
                    })
                  }
                >
                  {production ? "Forget local bank connection only" : "Forget local sandbox connection only"}
                </button>
                {status.state === "REVOCATION_PENDING" && <>
                  <p>Keep this reference to retry revocation. If no usable reference was returned, revoke access in your bank or Enable Banking consent controls first.</p>
                  <label className="ledger-check"><input type="checkbox" checked={revoked} onChange={e => setRevoked(e.target.checked)} />I have personally revoked this application's access at the bank or provider</label>
                  <button type="button" disabled={!revoked} onClick={() => run(async () => {
                    setStatus(await sandbox<SandboxStatus>("external-revocation", {acknowledged: revoked}));
                    setRevoked(false); setForget(false); setConsent(false); setBalance(null);
                  })}>Confirm completed external revocation and clear connection</button>
                </>}
              </>
            )}
          </fieldset>
          <p>
            Last complete fetch:{" "}
            {status.lastSuccessfulFetch
              ? new Date(status.lastSuccessfulFetch).toLocaleString()
              : "none"}
            . {status.lastError}
          </p>
          {balance && (
            <div aria-label={production ? "Bank balance evidence" : "Sandbox balance evidence"}>
              <h4>{production ? "Bank balance evidence" : "Sandbox balance evidence"}</h4>
              {balance.balances.map((b, i) => (
                <p key={i}>
                  {b.type}: €{b.amountEuro} · {b.date}
                </p>
              ))}
              <ul>
                {balance.warnings.map((w) => (
                  <li key={w}>{w}</li>
                ))}
              </ul>
              <p>
                Review and approve the proposed activity below, then explicitly
                compare your booked statement balance. No account balance has
                been overwritten.
              </p>
            </div>
          )}
        </>
      )}
      {production && <p>Disconnect revokes this API session; use your bank or <a href="https://enablebanking.com/data-sharing-consents/" target="_blank" rel="noreferrer">Enable Banking consent controls</a> to terminate wider consent. Forgetting keeps approved records and backups. To erase local records, stop the app and use Remove-LocalData.ps1 from your installed release. Delete backup copies separately.</p>}
      {busy && <p role="status">Connecting to the bank provider…</p>}
    </section>
  );
}
