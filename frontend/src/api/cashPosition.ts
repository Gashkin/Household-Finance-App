export type CashLine = { label: string; amountMinor: number };

export type CashPositionInput = {
  asOfDate: string;
  sourceNote: string;
  verification: 'REVIEWED' | 'ESTIMATED';
  cashBalances: CashLine[];
  upcomingCommitments: CashLine[];
  minimumReserveMinor: number;
};

export type CashPositionSnapshot = CashPositionInput & {
  id: string;
  recordedAt: string;
  liquidCashMinor: number;
  committedMinor: number;
  availableAfterCommitmentsMinor: number;
  discretionaryAfterReserveMinor: number;
  stale: boolean;
  warnings: string[];
};

const endpoint = '/api/cash-position';

async function readError(response: Response): Promise<never> {
  const message = await response.text();
  throw new Error(response.status === 400 && message ? message : 'Cash position service failed. Please retry.');
}

export async function getLatestCashPosition(): Promise<CashPositionSnapshot | null> {
  let response: Response;
  try {
    response = await fetch(endpoint + '/latest');
  } catch {
    throw new Error('Cannot reach the backend. Start it to load saved cash evidence.');
  }
  if (response.status === 204) return null;
  if (!response.ok) return readError(response);
  return response.json() as Promise<CashPositionSnapshot>;
}

export async function saveCashPosition(input: CashPositionInput): Promise<CashPositionSnapshot> {
  let response: Response;
  try {
    response = await fetch(endpoint + '/snapshots', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(input),
    });
  } catch {
    throw new Error('Cannot reach the backend. Cash evidence was not saved.');
  }
  if (!response.ok) return readError(response);
  return response.json() as Promise<CashPositionSnapshot>;
}
