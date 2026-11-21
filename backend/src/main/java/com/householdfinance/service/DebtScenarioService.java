package com.householdfinance.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DebtScenarioService {
    public record Input(long balanceMinor, BigDecimal annualRatePercent, long monthlyPaymentMinor,
                        long extraPaymentMinor, long liquidCashMinor, long minimumReserveMinor) {}
    public record Schedule(int months, long interestMinor, long totalPaidMinor, long finalPaymentMinor) {}
    public record Result(Schedule baseline, Schedule withExtraPayment, long interestSavedMinor,
                         int monthsSaved, long remainingCashMinor, long reserveShortfallMinor,
                         List<String> warnings, List<String> assumptions) {}

    public Result compare(Input input) {
        requireNonNegative(input.balanceMinor(), "balanceMinor");
        requireNonNegative(input.monthlyPaymentMinor(), "monthlyPaymentMinor");
        requireNonNegative(input.extraPaymentMinor(), "extraPaymentMinor");
        requireNonNegative(input.liquidCashMinor(), "liquidCashMinor");
        requireNonNegative(input.minimumReserveMinor(), "minimumReserveMinor");
        if (input.balanceMinor() == 0 || input.monthlyPaymentMinor() == 0)
            throw new IllegalArgumentException("Balance and monthly payment must be greater than zero");
        if (input.annualRatePercent() == null || input.annualRatePercent().compareTo(BigDecimal.ZERO) < 0 || input.annualRatePercent().compareTo(BigDecimal.valueOf(100)) > 0)
            throw new IllegalArgumentException("annualRatePercent must be between 0 and 100");
        if (input.extraPaymentMinor() > input.balanceMinor())
            throw new IllegalArgumentException("extraPaymentMinor cannot exceed outstanding balance");
        if (input.extraPaymentMinor() > input.liquidCashMinor())
            throw new IllegalArgumentException("extraPaymentMinor cannot exceed available liquid cash");
        BigDecimal annualRate = input.annualRatePercent();
        Schedule baseline = simulate(input.balanceMinor(), input.monthlyPaymentMinor(), annualRate);
        Schedule extra = input.extraPaymentMinor() == input.balanceMinor()
                ? new Schedule(0, 0, 0, 0)
                : simulate(input.balanceMinor() - input.extraPaymentMinor(), input.monthlyPaymentMinor(), annualRate);
        long remainingCash = input.liquidCashMinor() - input.extraPaymentMinor();
        long shortfall = Math.max(0, input.minimumReserveMinor() - remainingCash);
        List<String> warnings = new ArrayList<>();
        if (shortfall > 0) warnings.add("Extra repayment would leave cash below the stated minimum reserve");
        if (input.annualRatePercent().signum() == 0) warnings.add("Interest savings are zero at a 0% rate");
        return new Result(baseline, extra, baseline.interestMinor() - extra.interestMinor(),
                baseline.months() - extra.months(), remainingCash, shortfall, warnings, List.of(
                "Annual interest rate stays fixed and is divided by 12 for monthly interest",
                "Interest is rounded half-up to minor currency units each month",
                "Regular payment is made at month end; extra repayment is applied immediately before the first modelled month",
                "No fees, penalties, tax effects, offset balances or variable rates are included",
                "This scenario does not alter recorded household balances or initiate a payment"));
    }

    private static void requireNonNegative(long value, String field) {
        if (value < 0 || value > Number.MAX_SAFE_INTEGER) throw new IllegalArgumentException(field + " must be a non-negative safe integer");
    }

    public static long monthlyInterest(long balance, BigDecimal annualRate) {
        return BigDecimal.valueOf(balance).multiply(annualRate).divide(BigDecimal.valueOf(1200), 0, RoundingMode.HALF_UP).longValueExact();
    }

    private static Schedule simulate(long balance, long payment, BigDecimal annualRate) {
        long remaining = balance, interestTotal = 0, paidTotal = 0, finalPayment = 0;
        int months = 0;
        while (remaining > 0) {
            if (months >= 1200) throw new IllegalArgumentException("Debt is not repaid within 1200 months");
            long interest = monthlyInterest(remaining, annualRate);
            if (payment <= interest) throw new IllegalArgumentException("Monthly payment does not cover interest and reduce principal");
            long due = Math.addExact(remaining, interest);
            long actual = Math.min(payment, due);
            remaining = due - actual;
            interestTotal = Math.addExact(interestTotal, interest);
            paidTotal = Math.addExact(paidTotal, actual);
            if (interestTotal > Number.MAX_SAFE_INTEGER || paidTotal > Number.MAX_SAFE_INTEGER)
                throw new IllegalArgumentException("Cumulative amounts exceed safe integer range");
            finalPayment = actual;
            months++;
        }
        return new Schedule(months, interestTotal, paidTotal, finalPayment);
    }

    private static final class Number { static final long MAX_SAFE_INTEGER = 9007199254740991L; }
}
