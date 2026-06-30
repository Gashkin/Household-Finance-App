package com.householdfinance.controller;

import com.householdfinance.service.MonthlySummaryService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin(origins = "http://localhost:5173")
@RestController
public class MonthlySummaryController {

    private final MonthlySummaryService monthlySummaryService;

    public MonthlySummaryController(MonthlySummaryService monthlySummaryService) {
        this.monthlySummaryService = monthlySummaryService;
    }

    @GetMapping("/reports/monthly-overview")
    public MonthlySummaryService.MonthlyOverview monthlyOverview() {
        return monthlySummaryService.getMonthlyOverview();
    }
}
