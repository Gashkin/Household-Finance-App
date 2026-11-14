import type { Preview } from "./ledger";
export type SandboxAccount = {
  handle: string;
  label: string;
  currency: string;
  kind: string;
};
export type SandboxStatus = {
  configured: boolean;
  environment: string;
  state: string;
  expiresAt: string | null;
  lastSuccessfulFetch: string | null;
  lastError: string;
  accounts: SandboxAccount[];
  callback: string;
};
export type SandboxBank = {
  name: string;
  country: string;
  maximumConsentSeconds: number;
};
export type SandboxFetched = {
  preview: Preview;
  balances: { type: string; amountEuro: string; date: string }[];
  warnings: string[];
  fetchedAt: string;
};
export async function sandbox<T>(path: string, body?: unknown): Promise<T> {
  let response: Response;
  try {
    response = await fetch("/api/bank-sandbox/" + path, {
      method: body === undefined ? "GET" : "POST",
      headers:
        body === undefined ? undefined : { "Content-Type": "application/json" },
      body: body === undefined ? undefined : JSON.stringify(body),
      cache: "no-store",
    });
  } catch {
    throw Error(
      "Sandbox service unavailable; no activity has been approved. Reload status before retrying.",
    );
  }
  if (!response.ok) {
    let message = await response.text();
    try {
      message = JSON.parse(message).error || message;
    } catch {
      /* local access filter */
    }
    throw Error(
      response.status >= 500
        ? "Sandbox service unavailable; reconnect after checking local setup."
        : message || "Sandbox request failed",
    );
  }
  return response.json();
}
let callback: { state: string; code: string; denied: boolean } | null = null;
let completion: Promise<SandboxStatus> | null = null;
// Capture then erase the query before any app network request or rendered error can disclose it.
export function captureSandboxCallback() {
  if (location.pathname !== "/bank/callback") return;
  const query = new URLSearchParams(location.search);
  callback = {
    state: query.get("state") || "",
    code: query.get("code") || "",
    denied: query.has("error"),
  };
  history.replaceState(null, "", "/bank/callback");
}
export function completeSandboxCallback() {
  if (completion) return completion;
  if (!callback) return null;
  const value = callback;
  callback = null;
  completion = value.denied
    ? Promise.reject(
        Error("Sandbox consent was cancelled. Start a new connection."),
      )
    : sandbox<SandboxStatus>("complete", {
        state: value.state,
        code: value.code,
      });
  return completion;
}
