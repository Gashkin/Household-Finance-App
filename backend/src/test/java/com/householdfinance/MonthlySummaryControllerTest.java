package com.householdfinance;

import com.householdfinance.controller.MonthlySummaryController;
import com.householdfinance.service.MonthlySummaryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc(addFilters=false)
@WebMvcTest(MonthlySummaryController.class)
class MonthlySummaryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MonthlySummaryService monthlySummaryService;

    @Test
    void monthlyOverviewEndpointReturnsCalculatedSummary() throws Exception {
        var summary = new MonthlySummaryService.MonthlyOverview(
                "2026-07",
                15770.65,
                3200.00,
                122.31,
                1360.60,
                17487.74,
                17487.74,
                List.of(new MonthlySummaryService.MonthlyTransaction("2026-06-04", "Salary", 3200.00, "Checking"))
        );

        given(monthlySummaryService.getMonthlyOverview()).willReturn(summary);

        mockMvc.perform(get("/reports/monthly-overview"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {
                          "month":"2026-07",
                          "startingBalance":15770.65,
                          "actualInflow":3200.0,
                          "actualOutflow":122.31,
                          "committedOutgoings":1360.6,
                          "projectedEndBalance":17487.74,
                          "allowableSpend":17487.74,
                          "recentTransactions":[
                            {"date":"2026-06-04","description":"Salary","amount":3200.0,"account":"Checking"}
                          ]
                        }
                        """));
    }
}
