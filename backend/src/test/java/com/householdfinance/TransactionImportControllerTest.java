package com.householdfinance;

import com.householdfinance.controller.TransactionImportController;
import com.householdfinance.service.TransactionImportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc(addFilters=false)
@WebMvcTest(TransactionImportController.class)
@org.springframework.boot.test.mock.mockito.MockBean(com.householdfinance.access.AccessService.class)
class TransactionImportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TransactionImportService transactionImportService;

    @Test
    void previewEndpointReturnsParsedTransactions() throws Exception {
        var preview = new TransactionImportService.ImportPreview(
                "import-123",
                List.of(new TransactionImportService.ImportedTransaction("R1", "2026-06-01", "Groceries", -54.20, "Checking", false))
        );

        given(transactionImportService.previewCsv("date,description,amount,account\n2026-06-01,Groceries,-54.20,Checking"))
                .willReturn(preview);

        mockMvc.perform(post("/imports/preview")
                        .contentType("text/csv")
                        .content("date,description,amount,account\n2026-06-01,Groceries,-54.20,Checking"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {
                          "importId":"import-123",
                          "transactions":[{"rowId":"R1","date":"2026-06-01","description":"Groceries","amount":-54.2,"account":"Checking","duplicate":false}]
                        }
                        """));
    }

    @Test
    void acceptEndpointReturnsImportSummary() throws Exception {
        var request = new TransactionImportService.ImportAcceptRequest("import-123", List.of("R1", "R2"));
        var result = new TransactionImportService.ImportAcceptResult("import-123", 2, 0);

        given(transactionImportService.acceptImport(request)).willReturn(result);

        mockMvc.perform(post("/imports/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "importId":"import-123",
                                  "acceptedRowIds":["R1","R2"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {
                          "importId":"import-123",
                          "acceptedCount":2,
                          "skippedCount":0
                        }
                        """));
    }
}
