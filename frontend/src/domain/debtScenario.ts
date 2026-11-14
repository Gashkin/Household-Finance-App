/**
 * Pure, deterministic debt repayment scenario calculations.
 * Amounts are integer minor currency units (cents for EUR).
 * Monthly interest is rounded half-up to the nearest minor unit.
 * This is an illustrative fixed-rate model, not a lender quotation.
 */
export type DebtScenarioInput = {
  balanceMinor: number;
  annualRatePercent: number;
  monthlyPaymentMinor: number;
  extraPaymentMinor: number;
  liquidCashMinor: number;
  minimumReserveMinor: number;
  maximumMonths?: number;
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

function requireMinor(value: number, field: string): void {
  if (!Number.isSafeInteger(value) || value < 0) {
    throw new RangeError(`${field} must be a non-negative safe integer in minor currency units`);
  }
}

function simulate(balanceMinor: number, monthlyPaymentMinor: number, monthlyRate: number, maximumMonths: number): DebtSchedule {
  let remaining = balanceMinor;
  let interestMinor = 0;
  let totalPaidMinor = 0;
  let finalPaymentMinor = 0;
  let months = 0;

  while (remaining > 0) {
    if (months >= maximumMonths) throw new RangeError('Debt is not repaid within maximumMonths');
    const monthlyInterest = Math.round(remaining * monthlyRate);
    if (!Number.isSafeInteger(monthlyInterest)) throw new RangeError('Interest exceeds safe integer range');
    if (monthlyPaymentMinor <= monthlyInterest) {
      throw new RangeError('Monthly payment does not cover interest and reduce principal');
    }
    const due = remaining + monthlyInterest;
    if (!Number.isSafeInteger(due)) throw new RangeError('Balance exceeds safe integer range');
    const payment = Math.min(monthlyPaymentMinor, due);
    remaining = due - payment;
    interestMinor += monthlyInterest;
    totalPaidMinor += payment;
    if (!Number.isSafeInteger(interestMinor) || !Number.isSafeInteger(totalPaidMinor)) {
      throw new RangeError('Cumulative amounts exceed safe integer range');
    }
    finalPaymentMinor = payment;
    months++;
  }

  return { months, interestMinor, totalPaidMinor, finalPaymentMinor };
}

export function calculateDebtScenario(input: DebtScenarioInput): DebtScenarioResult {
  const { balanceMinor, annualRatePercent, monthlyPaymentMinor, extraPaymentMinor, liquidCashMinor, minimumReserveMinor } = input;
  for (const [field, value] of Object.entries({ balanceMinor, monthlyPaymentMinor, extraPaymentMinor, liquidCashMinor, minimumReserveMinor })) {
    requireMinor(value, field);
  }
  if (!Number.isFinite(annualRatePercent) || annualRatePercent < 0 || annualRatePercent > 100) {
    throw new RangeError('annualRatePercent must be between 0 and 100');
  }
  const maximumMonths = input.maximumMonths ?? 1200;
  if (!Number.isSafeInteger(maximumMonths) || maximumMonths < 1 || maximumMonths > 1200) {
    throw new RangeError('maximumMonths must be an integer between 1 and 1200');
  }
  if (balanceMinor === 0) throw new RangeError('balanceMinor must be greater than zero');
  if (monthlyPaymentMinor === 0) throw new RangeError('monthlyPaymentMinor must be greater than zero');
  if (extraPaymentMinor > balanceMinor) throw new RangeError('extraPaymentMinor cannot exceed outstanding balance');
  if (extraPaymentMinor > liquidCashMinor) throw new RangeError('extraPaymentMinor cannot exceed available liquid cash');

  const monthlyRate = annualRatePercent / 1200;
  const baseline = simulate(balanceMinor, monthlyPaymentMinor, monthlyRate, maximumMonths);
  const withExtraPayment = extraPaymentMinor === balanceMinor
    ? { months: 0, interestMinor: 0, totalPaidMinor: 0, finalPaymentMinor: 0 }
    : simulate(balanceMinor - extraPaymentMinor, monthlyPaymentMinor, monthlyRate, maximumMonths);
  const remainingCashMinor = liquidCashMinor - extraPaymentMinor;
  const reserveShortfallMinor = Math.max(0, minimumReserveMinor - remainingCashMinor);
  const warnings = [];
  if (reserveShortfallMinor > 0) warnings.push('Extra repayment would leave cash below the stated minimum reserve');
  if (annualRatePercent === 0) warnings.push('Interest savings are zero at a 0% rate');

  return {
    baseline,
    withExtraPayment,
    interestSavedMinor: baseline.interestMinor - withExtraPayment.interestMinor,
    monthsSaved: baseline.months - withExtraPayment.months,
    remainingCashMinor,
    reserveShortfallMinor,
    warnings,
    assumptions: [
      'Annual interest rate stays fixed and is divided by 12 for monthly interest',
      'Interest is rounded half-up to minor currency units each month',
      'Regular payment is made at month end; extra repayment is applied immediately before the first modelled month',
      'No fees, penalties, tax effects, offset balances or variable rates are included',
      'This scenario does not alter recorded household balances or initiate a payment',
    ],
  };
}
