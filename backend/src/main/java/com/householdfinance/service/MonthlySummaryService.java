package com.householdfinance.service;

import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Service;

/** The legacy planner is not evidence of reviewed monthly cashflow or spendability. */
@Service
public class MonthlySummaryService {
    public record MonthlyTransaction(String date, String description, double amount, String account) {}
    public record MonthlyOverview(
            String month, Double startingBalance, Double actualInflow, Double actualOutflow,
            Double committedOutgoings, Double projectedEndBalance, Double allowableSpend,
            List<MonthlyTransaction> recentTransactions, String status, String explanation) {}

    public MonthlyOverview getMonthlyOverview() {
        // Unknown is not zero. Current balances, legacy plans and synthetic fixtures
        // cannot establish an opening balance or reviewed actual monthly activity.
        return new MonthlyOverview(
                YearMonth.now(ZoneId.of("Europe/Dublin")).toString(),
                null, null, null, null, null, null, List.of(), "UNAVAILABLE",
                "This legacy dashboard has no verified monthly cashflow summary. "
                + "Use Money position for reviewed account evidence and Plan ahead for dated affordability.");
    }
}
