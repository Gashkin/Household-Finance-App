import type { CashPositionSnapshot } from './cashPosition';
export type IntakeCapabilities = { enabled: boolean; modelConfigured: boolean; modelDailyLimit: number; maxOutputTokens: number };
export type IntakeProposal = { lineNumber: number; kind: 'CASH' | 'COMMITMENT'; label: string; amountEuro: string; quote: string; uncertainty: string };
export type IntakeDraft = { id: string; expiresAt: string; method: 'LOCAL' | 'MODEL'; proposals: IntakeProposal[]; warnings: string[] };
export type ReviewedFact = Pick<IntakeProposal, 'lineNumber' | 'kind' | 'label' | 'amountEuro'>;
export async function intakeRequest<T>(path: string, key: string, method = 'GET', data?: unknown): Promise<T> {
  let response: Response;
  try {
    response = await fetch('/api/evidence-intake' + path, {
      method, cache: 'no-store', headers: { ...(key ? { 'X-Intake-Key': key } : {}), ...(data === undefined ? {} : { 'Content-Type': 'application/json' }) },
      ...(data === undefined ? {} : { body: JSON.stringify(data) }),
    });
  } catch { throw new Error('Cannot reach the evidence intake service. Start the backend and retry.'); }
  if (!response.ok) throw new Error(response.status < 500 ? await response.text() : 'Evidence intake is unavailable on this instance. Please retry or contact its owner.');
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}
export const approveIntake = (id: string, key: string, values: { asOfDate: string; minimumReserveEuro: string; acknowledged: boolean; rows: ReviewedFact[] }) =>
  intakeRequest<CashPositionSnapshot>('/drafts/' + encodeURIComponent(id) + '/approve', key, 'POST', values);
