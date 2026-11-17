package com.householdfinance;

import com.householdfinance.controller.WorkbookIngestionController;
import com.householdfinance.service.WorkbookIngestionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc(addFilters=false)
@WebMvcTest(WorkbookIngestionController.class)
@org.springframework.test.context.bean.override.mockito.MockitoBean(types=com.householdfinance.access.AccessService.class)
@SuppressWarnings("null")
class WorkbookIngestionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WorkbookIngestionService workbookIngestionService;

    @Test
    void workbookPreviewEndpointReturnsSheetSummary() throws Exception {
        var file = new MockMultipartFile(
                "file",
                "House_v2.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "fake-xlsx-content".getBytes()
        );

        var preview = new WorkbookIngestionService.WorkbookPreview(
                "wb-123",
                2,
                40,
                List.of(new WorkbookIngestionService.WorkbookSheetPreview(
                        "MonthlyBudget",
                        20,
                        List.of("Metric", "Amount"),
                        List.of(List.of("Checking", "4120.75"))
                ))
        );

        given(workbookIngestionService.previewWorkbook(file)).willReturn(preview);

        mockMvc.perform(multipart("/migration/workbook/preview").file(file))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {
                          "importId":"wb-123",
                          "sheetCount":2,
                          "totalRowCount":40,
                          "sheets":[
                            {
                              "sheetName":"MonthlyBudget",
                              "rowCount":20,
                              "headers":["Metric","Amount"],
                              "sampleRows":[["Checking","4120.75"]]
                            }
                          ]
                        }
                        """));
    }

    @Test
    void workbookMapEndpointReturnsDomainMappings() throws Exception {
        var file = new MockMultipartFile(
                "file",
                "House_v2.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "fake-xlsx-content".getBytes()
        );

        var result = new WorkbookIngestionService.WorkbookMappingResult(
                "map-123",
                4,
                2,
                2,
                1,
                3,
          1,
          0,
          List.of("Monthly Budget -> recurring commitments"),
                List.of("Notes"),
                List.of(new WorkbookIngestionService.MappingWarning("Transactions", 9, "Skipped transaction row without description.")),
                List.of(new WorkbookIngestionService.MappedAccount("Checking", "Bank", 4120.75, "Accounts", 2)),
                List.of(new WorkbookIngestionService.MappedCategory("Housing", "$1,200.00", "Categories", 3)),
                List.of(new WorkbookIngestionService.MappedCommitment("Rent", 1200.00, "2026-07-01", "Commitments", 4)),
                List.of(new WorkbookIngestionService.MappedTransaction("2026-06-01", "Groceries", -54.20, "Checking", "Transactions", 5))
        );

        given(workbookIngestionService.mapWorkbook(file)).willReturn(result);

        mockMvc.perform(multipart("/migration/workbook/map").file(file))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {
                          "importId":"map-123",
                          "sheetCount":4,
                          "mappedAccountsCount":2,
                          "mappedCategoriesCount":2,
                          "mappedCommitmentsCount":1,
                          "mappedTransactionsCount":3,
                          "unmappedSheets":["Notes"],
                          "warnings":[
                            {
                              "sheetName":"Transactions",
                              "rowNumber":9,
                              "message":"Skipped transaction row without description."
                            }
                          ],
                          "accounts":[
                            {
                              "name":"Checking",
                              "type":"Bank",
                              "balance":4120.75,
                              "sourceSheet":"Accounts",
                              "sourceRow":2
                            }
                          ],
                          "categories":[
                            {
                              "name":"Housing",
                              "budget":"$1,200.00",
                              "sourceSheet":"Categories",
                              "sourceRow":3
                            }
                          ],
                          "commitments":[
                            {
                              "title":"Rent",
                              "amount":1200.0,
                              "dueDate":"2026-07-01",
                              "sourceSheet":"Commitments",
                              "sourceRow":4
                            }
                          ],
                          "transactions":[
                            {
                              "date":"2026-06-01",
                              "description":"Groceries",
                              "amount":-54.2,
                              "account":"Checking",
                              "sourceSheet":"Transactions",
                              "sourceRow":5
                            }
                          ]
                        }
                        """));
    }

    @Test
    void workbookValidateEndpointReturnsValidationSummary() throws Exception {
        var file = new MockMultipartFile(
                "file",
                "House_v2.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "fake-xlsx-content".getBytes()
        );

        var validation = new WorkbookIngestionService.WorkbookLoadValidationResult(
                "val-123",
                "MERGE",
                true,
                0,
                1,
                new WorkbookIngestionService.LoadComponentCounts(2, 2, 1, 3),
          List.of(new WorkbookIngestionService.ValidationIssue("TRANSACTIONS", "Workbook", "WARNING", "Mapped transaction rows are validated but not persisted in this phase.")),
                "confirm-123"
        );

        given(workbookIngestionService.validateWorkbookLoad(file, "MERGE")).willReturn(validation);

        mockMvc.perform(multipart("/migration/workbook/validate").file(file).param("mode", "MERGE"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {
                          "validationId":"val-123",
                          "mode":"MERGE",
                          "valid":true,
                          "hardErrorCount":0,
                          "warningCount":1,
                          "counts":{
                            "accounts":2,
                            "categories":2,
                            "commitments":1,
                            "transactions":3
                          },
                          "issues":[
                            {
                              "component":"TRANSACTIONS",
                              "rowReference":"Workbook",
                              "severity":"WARNING",
                              "message":"Mapped transaction rows are validated but not persisted in this phase."
                            }
                          ],
                          "confirmationToken":"confirm-123"
                        }
                        """));
    }

    @Test
    void workbookExecuteEndpointReturnsExecutionSummary() throws Exception {
        var request = new WorkbookIngestionService.WorkbookLoadExecuteRequest("val-123", "confirm-123", "MERGE");
        var result = new WorkbookIngestionService.WorkbookLoadExecutionResult(
                "exec-123",
                "val-123",
                "MERGE",
                2,
                2,
                1,
                3,
                "Workbook load applied for persisted components. Mapped transaction rows are not persisted yet."
        );

        given(workbookIngestionService.executeValidatedLoad(request)).willReturn(result);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/migration/workbook/execute")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "validationId":"val-123",
                                  "confirmationToken":"confirm-123",
                                  "mode":"MERGE"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {
                          "executionId":"exec-123",
                          "validationId":"val-123",
                          "mode":"MERGE",
                          "appliedAccounts":2,
                          "appliedCategories":2,
                          "appliedCommitments":1,
                          "skippedTransactions":3
                        }
                        """));
    }

                      @Test
                      void latestLoadSummaryEndpointReturnsPostLoadSummary() throws Exception {
                    var summary = new WorkbookIngestionService.WorkbookLoadPostSummary(
                      "exec-123",
                      "val-123",
                      "MERGE",
                      "APPLIED",
                      "2026-06-29T22:55:00Z",
                      2,
                      2,
                      1,
                      3,
                      "Workbook load applied for persisted components. Mapped transaction rows are not persisted yet."
                    );

                    given(workbookIngestionService.getLatestLoadSummary()).willReturn(summary);

                    mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/migration/workbook/loads/latest"))
                      .andExpect(status().isOk())
                      .andExpect(content().json("""
                        {
                          "executionId":"exec-123",
                          "validationId":"val-123",
                          "mode":"MERGE",
                          "status":"APPLIED",
                          "appliedAccounts":2,
                          "appliedCategories":2,
                          "appliedCommitments":1,
                          "rejectedRows":3
                        }
                        """));
                      }

                      @Test
                      void rejectionsEndpointReturnsRejectedRows() throws Exception {
                    given(workbookIngestionService.getRejectedRows("exec-123")).willReturn(List.of(
                      new WorkbookIngestionService.LoadRejectedRow(
                        "TRANSACTIONS",
                        "Transactions:5",
                        "Mapped transaction rows are not persisted in this phase."
                      )
                    ));

                    mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/migration/workbook/loads/exec-123/rejections"))
                      .andExpect(status().isOk())
                      .andExpect(content().json("""
                        [
                          {
                            "component":"TRANSACTIONS",
                            "rowReference":"Transactions:5",
                            "reason":"Mapped transaction rows are not persisted in this phase."
                          }
                        ]
                        """));
                      }

                      @Test
                      void rollbackEndpointReturnsRollbackStatus() throws Exception {
                    given(workbookIngestionService.rollbackLoad("exec-123")).willReturn(
                      new WorkbookIngestionService.WorkbookLoadRollbackResult(
                        "exec-123",
                        "ROLLED_BACK",
                        "Rollback completed. Planner components restored to pre-load snapshot."
                      )
                    );

                    mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/migration/workbook/loads/exec-123/rollback"))
                      .andExpect(status().isOk())
                      .andExpect(content().json("""
                        {
                          "executionId":"exec-123",
                          "status":"ROLLED_BACK"
                        }
                        """));
                      }
}
