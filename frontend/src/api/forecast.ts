export type FutureEvent = {
  id: string;
  date: string;
  label: string;
  direction: string;
  certainty: string;
  amountMinor: number;
  source: string;
};
export type Baseline = {
  mode: string;
  date: string;
  cashMinor: number;
  pendingMinor: number;
  source: string;
  capturedAt: string;
  revisions: Record<string, number>;
};
export type Day = {
  date: string;
  openingMinor: number;
  expensesMinor: number;
  expectedIncomeMinor: number;
  uncertainIncomeMinor: number;
  appliedIncomeMinor: number;
  lowMinor: number;
  closingMinor: number;
  headroomMinor: number;
  events: FutureEvent[];
};
export type Projection = {
  startingMinor: number;
  totalExpensesMinor: number;
  expectedIncomeMinor: number;
  uncertainIncomeMinor: number;
  appliedIncomeMinor: number;
  closingMinor: number;
  lowestMinor: number;
  lowestDate: string;
  shortfallDate: string | null;
  reserveBreachDate: string | null;
  cashOnlyHeadroomMinor: number;
  planningHeadroomMinor: number;
  days: Day[];
};
export type Inputs = {
  name: string;
  baseline: Baseline;
  endDate: string;
  reserveMinor: number;
  events: FutureEvent[];
};
export type Plan = {
  revision: number;
  updatedAt: string;
  inputs: Inputs;
  evidenceState: string;
  currentDecisionReady: boolean;
  warnings: string[];
  projection: Projection;
};
export type ForecastWorkspace = {
  today: string;
  suggestedLedgerDate: string | null;
  plan: Plan | null;
  hiddenPlanRevision?:number|null;
};
export type Comparison = {
  revision: number;
  evidenceState: string;
  currentDecisionReady: boolean;
  warnings: string[];
  base: Projection;
  scenario: Projection;
  checkedPlan: Plan;
};
export async function forecast<T>(path = "", body?: unknown): Promise<T> {
  let response: Response;
  try {
    response = await fetch("/api/forecast" + path, {
      method: body === undefined ? "GET" : "POST",
      headers:
        body === undefined ? undefined : { "Content-Type": "application/json" },
      body: body === undefined ? undefined : JSON.stringify(body),
      cache: "no-store",
    });
  } catch {
    throw Error(
      "Cannot confirm this request. Reload the saved plan before retrying; a save may have completed.",
    );
  }
  if (!response.ok) {
    let message = await response.text();
    try {
      message = JSON.parse(message).error || message;
    } catch {
      /* access filter */
    }
    throw Error(
      response.status >= 500
        ? "Local forecast service unavailable. Reload before retrying."
        : message || "Forecast request failed",
    );
  }
  return response.json();
}
