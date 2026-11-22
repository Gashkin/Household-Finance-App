import { useEffect, useState } from "react";
type Approval = {
  id: string;
  caller: string;
  expiresAt: string;
  state: string;
};
type Status = {
  configured: boolean;
  enabled: boolean;
  approvals: Approval[];
  audit: {
    timestamp: string;
    caller: string;
    outcome: string;
    requestId: string;
  }[];
};
export default function McpConnection() {
  const [status, setStatus] = useState<Status | null>(null),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false);
  async function load() {
    setError("");
    try {
      const r = await fetch("/api/mcp");
      if (!r.ok)
        throw new Error(
          "Connection controls are unavailable. Unlock as the financial owner.",
        );
      setStatus(await r.json());
    } catch (e) {
      setError((e as Error).message);
    }
  }
  useEffect(() => {
    void load();
  }, []);
  async function action(path: string) {
    setBusy(true);
    setError("");
    try {
      const r = await fetch("/api/mcp/" + path, { method: "POST" });
      if (!r.ok)
        throw new Error(
          "Change was refused. Refresh; the request may have expired or already been decided.",
        );
      await load();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <section className="panel mcp-connection">
      <h2>ChatGPT connection</h2>
      <p>
        Share a minimal snapshot of the current financial period: balances,
        dated commitments and coverage warnings. Account names, counterparties
        and statement narratives are excluded.
      </p>
      <p>
        Each read needs your approval here. Approvals expire after two minutes
        and work once. Approve only a request you have just made in ChatGPT. A
        connection or token alone cannot read your finances.
      </p>
      {error && <p role="alert">{error}</p>}
      {!status ? (
        <>
          <p>
            {error
              ? "Connection controls could not be loaded."
              : "Loading connection controls…"}
          </p>
          {error && (
            <button onClick={() => void load()}>
              Retry connection controls
            </button>
          )}
        </>
      ) : (
        <>
          <p>
            Connection:{" "}
            <strong>{status.enabled ? "Enabled" : "Disabled"}</strong>.{" "}
            {status.configured
              ? "Secure sign-in is configured."
              : "Secure sign-in setup is required before enabling this connection."}
          </p>
          <button
            disabled={busy || !status.configured || status.enabled}
            onClick={() => void action("enable")}
          >
            Enable connection
          </button>{" "}
          <button
            disabled={busy || !status.enabled}
            onClick={() => void action("disable")}
          >
            Disable and revoke approvals
          </button>{" "}
          <button disabled={busy} onClick={() => void load()}>
            Refresh requests
          </button>
          <h3>Read requests</h3>
          {!status.approvals.length ? (
            <p>
              No requests awaiting a decision. Ask ChatGPT for your current
              financial month, then refresh.
            </p>
          ) : (
            status.approvals.map((a) => (
              <article key={a.id}>
                <p>
                  Request <code>{a.id}</code> from connection {a.caller}.
                  Expires {new Date(a.expiresAt).toLocaleTimeString()}.{" "}
                  {a.state}
                </p>
                {a.state === "PENDING" && (
                  <>
                    <button
                      disabled={busy}
                      onClick={() =>
                        void action("approvals/" + a.id + "/approve")
                      }
                    >
                      Approve this one read
                    </button>{" "}
                    <button
                      disabled={busy}
                      onClick={() => void action("approvals/" + a.id + "/deny")}
                    >
                      Deny
                    </button>
                  </>
                )}
              </article>
            ))
          )}
          <h3>Recent connection activity</h3>
          <p>
            Only connection outcomes are recorded here; financial payloads and
            credentials are excluded. This list resets when the app restarts.
          </p>
          <ul>
            {status.audit
              .slice()
              .reverse()
              .map((a) => (
                <li key={a.requestId + ":" + a.outcome}>
                  {new Date(a.timestamp).toLocaleTimeString()} — {a.outcome} —{" "}
                  {a.caller}
                </li>
              ))}
          </ul>
        </>
      )}
    </section>
  );
}
