import type { DebtComparisonReport } from "./debtScenarios";
export type Evidence = {
  id: string;
  source: string;
  observedOn: string;
  summary: string;
  uncertainty: string;
};
export type Option = {
  id: string;
  label: string;
  impact: string;
  uncertainty: string;
};
export type PlanContent = {
  title: string;
  goal: string;
  evidence: Evidence[];
  options: Option[];
};
export type FollowUp = {
  id: string;
  label: string;
  dueOn: string;
  outcome: string | null;
  completedAt: string | null;
};
export type Decision = {
  optionId: string;
  optionLabel: string;
  rationale: string;
  reviewOn: string;
  decidedAt: string;
};
export type PlanHistory = {
  kind: string;
  at: string;
  content: PlanContent;
  report: DebtComparisonReport | null;
  decision: Decision | null;
  actions: FollowUp[];
  note: string;
};
export type DecisionPlan = {
  id: string;
  revision: number;
  updatedAt: string;
  status: string;
  data: {
    content: PlanContent;
    report: DebtComparisonReport | null;
    decision: Decision | null;
    actions: FollowUp[];
    history: PlanHistory[];
  };
};
export async function plans<T>(
  path = "",
  body?: unknown,
  method = "POST",
): Promise<T> {
  let response: Response;
  try {
    response = await fetch("/api/plans" + path, {
      method: body === undefined ? "GET" : method,
      headers:
        body === undefined ? undefined : { "Content-Type": "application/json" },
      body: body === undefined ? undefined : JSON.stringify(body),
      cache: "no-store",
    });
  } catch {
    throw Error(
      "Cannot confirm this request. Reload saved plans before retrying; the save may have completed.",
    );
  }
  if (!response.ok) {
    let message = await response.text();
    try {
      message = JSON.parse(message).error || message;
    } catch {
      /* filter returns plain text */
    }
    throw Error(
      response.status === 401
        ? "Unlock the workspace to continue"
        : response.status >= 500
          ? "Could not save. Reload saved plans before retrying."
          : message || "Plan request failed",
    );
  }
  return response.json();
}
