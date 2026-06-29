package com.householdfinance.service;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MonthlySummaryService {

    public record MonthlyTransaction(String date, String description, double amount, String account) {}

    public record MonthlyOverview(
            String month,
            double startingBalance,
            double actualInflow,
            double actualOutflow,
            double committedOutgoings,
            double projectedEndBalance,
            double allowableSpend,
            List<MonthlyTransaction> recentTransactions
    ) {}

    private final PlannerService plannerService;

    public MonthlySummaryService(PlannerService plannerService) {
        this.plannerService = plannerService;
    }

    public MonthlyOverview getMonthlyOverview() {
        var plannerData = plannerService.getPlannerData();
        var recentTransactions = sampleTransactions();

        var startingBalance = plannerData.accounts().stream()
                .mapToDouble(PlannerService.Account::balance)
                .sum();

        var committedOutgoings = plannerData.commitments().stream()
                .mapToDouble(PlannerService.Commitment::amount)
                .sum();

        var actualInflow = recentTransactions.stream()
                .mapToDouble(MonthlyTransaction::amount)
                .filter(amount -> amount > 0)
                .sum();

        var actualOutflow = recentTransactions.stream()
                .mapToDouble(MonthlyTransaction::amount)
                .filter(amount -> amount < 0)
                .map(Math::abs)
                .sum();

        var projectedEndBalance = startingBalance + actualInflow - actualOutflow - committedOutgoings;
        var allowableSpend = Math.max(0, projectedEndBalance);

        return new MonthlyOverview(
                "2026-07",
                round(startingBalance),
                round(actualInflow),
                round(actualOutflow),
                round(committedOutgoings),
                round(projectedEndBalance),
                round(allowableSpend),
                recentTransactions
        );
    }

    private List<MonthlyTransaction> sampleTransactions() {
        return List.of(
                new MonthlyTransaction("2026-06-04", "Salary", 3200.00, "Checking"),
                new MonthlyTransaction("2026-06-03", "Fuel", -68.11, "Credit Card"),
                new MonthlyTransaction("2026-06-01", "Groceries", -54.20, "Checking")
        );
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
