export const contractPath = '/workspaces/planning/contracts';

export type Certainty = 'UNKNOWN' | 'ESTIMATED' | 'VERIFIED';
export type Evidence = { certainty: Certainty; date: string | null; source: string | null; note: string | null };
export type Price = { amount: string; currency: string; effectiveFrom: string | null };
export type References = { billId: string | null; accountId: string | null; transactionId: string | null };
export type Terms = {
  provider: string;
  plan: string | null;
  type: 'FIXED' | 'ROLLING';
  start: string;
  reviewEnabled: boolean;
  termEnd: Evidence;
  notice: Evidence;
  promotion: Evidence;
  priceChange: Evidence;
  nextCharge: Evidence;
  reviewTarget: string | null;
  reviewSource: string | null;
  reviewNote: string | null;
  frequency: string | null;
  promotionalPrice: Price | null;
  standardPrice: Price | null;
  exitFee: Price | null;
  noticeTerms: string | null;
  references: References | null;
};
export type Kind = 'CONTRACT' | 'SUBSCRIPTION';
export type Assessment = { usage: string; closing: string; notes: string | null };
export type Decision = 'KEEP' | 'RENEW' | 'SWITCH' | 'CANCEL' | 'UNDECIDED';
export type Outcome = 'RENEWED' | 'SWITCHED' | 'CANCELLED';
export type Stage = 'UPCOMING' | 'NEEDS_DATE' | 'DUE' | 'OVERDUE' | 'COMPLETED';
export type Summary = {
  id: string; name: string; kind: Kind; status: 'UPCOMING' | 'ACTIVE' | 'CLOSED';
  owned: boolean; shared: boolean; provider: string; currentTermId: string;
};
export type Deadline = {
  cutoff: string | null; suggested: string | null; target: string | null; stage: Stage; timing: string;
};
export type TermView = {
  term: {
    id: string;
    terms: Terms;
    revisions: { recordedOn: string; previous: Terms }[];
    decisions: { decision: Decision; date: string; evidence: string | null }[];
    attempts: { action: Decision; date: string; evidence: string }[];
    confirmation: { outcome: Outcome; date: string; effective: string; evidence: string } | null;
  };
  deadline: Deadline | null;
  editable: boolean;
};
export type Detail = { summary: Summary; revision: number; latestTermId: string; assessment: Assessment | null; terms: TermView[] };
export type Option = { id: string; label: string };
export type LinkOptions = { bills: Option[]; accounts: Option[] };
export type Notification = {
  contractId: string; termId: string; name: string; cutoff: string | null; target: string | null;
  stage: Stage; unread: boolean; dismissed: boolean; snoozedUntil: string | null; timing: string;
};
export type Inbox = { today: string; zone: string; notifications: Notification[] };

export class ContractError extends Error {
  constructor(message: string, readonly status: number) { super(message); }
}

export async function contracts<T>(path = '', body?: unknown, method = body === undefined ? 'GET' : 'POST'): Promise<T> {
  const response = await fetch('/api/contracts' + path, {
    method,
    headers: body === undefined ? undefined : { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (!response.ok) {
    let message = await response.text();
    try { message = JSON.parse(message).error ?? message; } catch { /* access failures are plain text */ }
    throw new ContractError(message || 'The contract request failed.', response.status);
  }
  return response.status === 204 ? undefined as T : response.json() as Promise<T>;
}

export function termLink(contractId: string, termId?: string) {
  const query = new URLSearchParams({ contract: contractId });
  if (termId) query.set('term', termId);
  return contractPath + '?' + query;
}
