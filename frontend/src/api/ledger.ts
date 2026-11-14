export type ActivityRow = {
  reference: string;
  date: string;
  description: string;
  amountEuro: string;
  status: string;
  kind: string;
};
export type LedgerAccount = {
  id: string;
  name: string;
  kind: string;
  openingDate: string;
  openingMinor: number;
  source: string;
  revision: number;
  bookedMinor: number;
  pendingMinor: number;
  pendingDebitMinor: number;
  pendingEntries: number;
  incomeMinor: number;
  spendingMinor: number;
  reconciledDate: string | null;
  reconciledSource: string | null;
  reconciled: boolean;
  entries: number;
  warnings: string[];
};
export type Position = {
  asOf: string;
  accounts: LedgerAccount[];
  cashMinor: number;
  debtSignedMinor: number;
  netMinor: number;
  cashPendingDebitMinor: number;
  incomeMinor: number;
  spendingMinor: number;
  warnings: string[];
};
export type Activity = {
  id: string;
  reference: string;
  date: string;
  description: string;
  amountMinor: number;
  status: string;
  kind: string;
  source: string;
};
export type Preview = {
  id: string;
  revision: number;
  expiresAt: string;
  proposals: {
    row: ActivityRow;
    disposition: string;
    possibleOverlap: boolean;
  }[];
};
export type Audit = {
  id: string;
  action: string;
  recordedAt: string;
  source: string;
  beforeJson: string;
  afterJson: string;
};
export type Reconciliation = {
  expectedMinor: number;
  statementMinor: number;
  differenceMinor: number;
  applied: boolean;
};
export async function ledger<T>(
  path: string,
  body?: unknown,
  method = body === undefined ? "GET" : "POST",
): Promise<T> {
  let response: Response;
  try {
    response = await fetch("/api/ledger" + path, {
      method,
      headers:
        body === undefined ? undefined : { "Content-Type": "application/json" },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw Error(
      "Cannot confirm this request. Reload the position before retrying; it may have been saved.",
    );
  }
  if (!response.ok) {
    let message = await response.text();
    try {
      message = JSON.parse(message).error || message;
    } catch {
      /* plain access boundary */
    }
    throw Error(
      response.status >= 500
        ? "Ledger service is unavailable. Reload before retrying."
        : message || "Ledger request failed",
    );
  }
  return response.json() as Promise<T>;
}
export function euroText(minor: number) {
  const n = BigInt(minor),
    absolute = n < 0n ? -n : n;
  return (
    (n < 0n ? "-" : "") +
    (absolute / 100n).toString() +
    "." +
    (absolute % 100n).toString().padStart(2, "0")
  );
}
