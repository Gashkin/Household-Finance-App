package com.householdfinance;

import com.householdfinance.controller.WorkbookIngestionController;
import com.householdfinance.service.*;
import com.householdfinance.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value=WorkbookIngestionController.class, properties="workbook.import.enabled=false")
@AutoConfigureMockMvc(addFilters=false)
@Import(WorkbookIngestionService.class)
@MockitoBean(types=com.householdfinance.access.AccessService.class)
@org.junit.jupiter.api.extension.ExtendWith(com.householdfinance.access.OwnerFixture.class)
class WorkbookImportPolicyTest {
    @Autowired MockMvc http;
    @Autowired WorkbookIngestionService service;
    @MockitoBean MigrationArtifactService artifacts;
    @MockitoBean AccountRepository accounts;
    @MockitoBean CategoryRepository categories;
    @MockitoBean MonthlyCommitmentRepository commitments;
    @MockitoBean TransactionRepository transactions;

    @ParameterizedTest @ValueSource(strings={"preview","map","validate"})
    void disabledBinaryIntakeRejectsEveryRouteBeforeParsing(String route) throws Exception {
        var file=new MockMultipartFile("file","synthetic.xlsx","application/octet-stream",new byte[]{1,2,3});
        http.perform(multipart("/migration/workbook/"+route).file(file))
            .andExpect(status().isBadRequest())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Workbook imports are disabled")));
        verifyNoInteractions(artifacts,accounts,categories,commitments,transactions);
    }

    @Test void disabledServiceDoesNotEvenInspectMultipartBytes() {
        var file=mock(MultipartFile.class);
        for (var action:java.util.List.<org.junit.jupiter.api.function.Executable>of(
                ()->service.previewWorkbook(file),()->service.mapWorkbook(file),
                ()->service.validateWorkbookLoad(file,"MERGE"))) {
            var error=assertThrows(IllegalArgumentException.class,action);
            assertTrue(error.getMessage().contains("Workbook imports are disabled"));
        }
        verifyNoInteractions(file,artifacts,accounts,categories,commitments,transactions);
    }

    @Test void disabledPolicyRejectsAnAttemptToExecuteAnOldReview() throws Exception {
        http.perform(post("/migration/workbook/execute").contentType("application/json")
                .content("{\"validationId\":\"old\",\"confirmationToken\":\"old\",\"mode\":\"REPLACE\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Workbook imports are disabled")));
        verifyNoInteractions(artifacts,accounts,categories,commitments,transactions);
    }
}
