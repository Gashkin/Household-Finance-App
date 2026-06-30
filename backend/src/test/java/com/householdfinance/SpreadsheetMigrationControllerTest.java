package com.householdfinance;

import com.householdfinance.controller.SpreadsheetMigrationController;
import com.householdfinance.service.SpreadsheetMigrationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Objects;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SpreadsheetMigrationController.class)
class SpreadsheetMigrationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SpreadsheetMigrationService spreadsheetMigrationService;

    @Test
    void migrationPreviewEndpointReturnsVarianceRows() throws Exception {
        var preview = new SpreadsheetMigrationService.MigrationPreview(
                "mig-123",
                17000.00,
                16620.75,
                379.25,
                1,
                1,
                List.of(
                        new SpreadsheetMigrationService.MigrationRow("R1", "MonthlyBudget", "Checking", 4200.00, 4120.75, 79.25, "Mismatch"),
                        new SpreadsheetMigrationService.MigrationRow("R2", "MonthlyBudget", "Rent", 1200.00, 1200.00, 0.00, "Matched")
                )
        );

        var csv = "sheet,metric,amount\\nMonthlyBudget,Checking,4200.00\\nMonthlyBudget,Rent,1200.00";
        given(spreadsheetMigrationService.previewBaselineCsv(csv)).willReturn(preview);

        mockMvc.perform(post("/migration/preview")
                        .contentType("text/csv")
                        .content(csv))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {
                          "importId":"mig-123",
                          "baselineTotal":17000.0,
                          "appTotal":16620.75,
                          "variance":379.25,
                          "matchedCount":1,
                          "mismatchedCount":1,
                          "rows":[
                            {"rowId":"R1","sheet":"MonthlyBudget","metric":"Checking","baselineAmount":4200.0,"appAmount":4120.75,"variance":79.25,"status":"Mismatch"},
                            {"rowId":"R2","sheet":"MonthlyBudget","metric":"Rent","baselineAmount":1200.0,"appAmount":1200.0,"variance":0.0,"status":"Matched"}
                          ]
                        }
                        """));
    }

    @Test
    void reconcileEndpointReturnsAcceptedAndMismatchCounts() throws Exception {
        var request = new SpreadsheetMigrationService.ReconcileRequest("mig-123", List.of("R1", "R2"));
        var result = new SpreadsheetMigrationService.ReconcileResult(
                "mig-123",
                2,
                1,
                "Reconciliation recorded. Review mismatches before final cutover."
        );

        given(spreadsheetMigrationService.reconcile(request)).willReturn(result);

        mockMvc.perform(post("/migration/reconcile")
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content("""
                                {
                                  "importId":"mig-123",
                                  "acceptedRowIds":["R1","R2"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {
                          "importId":"mig-123",
                          "acceptedCount":2,
                          "mismatchCount":1,
                          "notes":"Reconciliation recorded. Review mismatches before final cutover."
                        }
                        """));
    }

    @Test
    void reportEndpointReturnsPerSheetTotalsAndUnresolvedCounts() throws Exception {
        var report = new SpreadsheetMigrationService.MigrationExecutionReport(
                "mig-123",
                4,
                2,
                1,
                List.of(
                        new SpreadsheetMigrationService.MigrationSheetReport(
                                "MonthlyBudget",
                                4,
                                2,
                                1,
                                17000.00,
                                16620.75,
                                379.25
                        )
                )
        );

        given(spreadsheetMigrationService.executionReport("mig-123")).willReturn(report);

        mockMvc.perform(get("/migration/report/mig-123"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {
                          "importId":"mig-123",
                          "totalRows":4,
                          "mismatchedRows":2,
                          "unresolvedRows":1,
                          "sheets":[
                            {
                              "sheet":"MonthlyBudget",
                              "totalRows":4,
                              "mismatchRows":2,
                              "unresolvedRows":1,
                              "baselineTotal":17000.0,
                              "appTotal":16620.75,
                              "varianceTotal":379.25
                            }
                          ]
                        }
                        """));
    }
}
