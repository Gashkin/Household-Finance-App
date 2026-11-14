export type DebtScenarioInput = {
  balanceMinor: number;
  annualRatePercent: number | string;
  monthlyPaymentMinor: number;
  extraPaymentMinor: number;
  liquidCashMinor: number;
  minimumReserveMinor: number;
  cashPositionSnapshotId?: string;
};
export type DebtSchedule = {
  months: number;
  interestMinor: number;
  totalPaidMinor: number;
  finalPaymentMinor: number;
};
export type DebtScenarioResult = {
  baseline: DebtSchedule;
  withExtraPayment: DebtSchedule;
  interestSavedMinor: number;
  monthsSaved: number;
  remainingCashMinor: number;
  reserveShortfallMinor: number;
  warnings: string[];
  assumptions: string[];
};

export async function compareDebtScenario(input: DebtScenarioInput): Promise<DebtScenarioResult> {
  let response: Response;
  try {
    response = await fetch('/api/debt-scenarios/compare', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(input),
    });
  } catch {
    throw new Error('Cannot reach the financial calculation service. Start the backend and retry.');
  }
  if (!response.ok) {
    const message = await response.text();
    throw new Error(response.status === 400 ? message : 'The calculation service failed. Please retry.');
  }
  return response.json() as Promise<DebtScenarioResult>;
}

export type DebtComparisonReport = {
  schemaVersion: string;
  calculationVersion: string;
  generatedAt: string;
  currency: string;
  input: DebtScenarioInput;
  result: DebtScenarioResult;
};
export async function createDebtReport(input: DebtScenarioInput): Promise<DebtComparisonReport> {
  let response: Response;
  try {
    response = await fetch('/api/debt-scenarios/report', {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(input),
    });
  } catch {
    throw new Error('Cannot reach the financial calculation service. Start the backend and retry.');
  }
  if (!response.ok) throw new Error(response.status === 400 ? await response.text() : 'Unable to prepare the comparison report. Please retry.');
  return response.json() as Promise<DebtComparisonReport>;
}
