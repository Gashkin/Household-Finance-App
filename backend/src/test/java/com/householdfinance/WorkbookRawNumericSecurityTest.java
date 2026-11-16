package com.householdfinance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.householdfinance.repository.*;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import org.apache.poi.ss.usermodel.FormulaError;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:workbook-raw-numbers;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc(addFilters=false)
class WorkbookRawNumericSecurityTest {
    @Autowired MockMvc http;
    @Autowired ObjectMapper json;
    @Autowired AccountRepository accounts;
    @Autowired CategoryRepository categories;
    @Autowired MonthlyCommitmentRepository commitments;
    @Autowired TransactionRepository transactions;
    @Autowired MigrationArtifactRepository artifacts;

    @BeforeEach void clearSyntheticRecords() {
        transactions.deleteAll();commitments.deleteAll();categories.deleteAll();accounts.deleteAll();artifacts.deleteAll();
    }

    @ParameterizedTest
    @ValueSource(strings={"1.000000000000000001","90071992547409.91","1E309","1E-400","1E2147483648","NaN","INF","","#ERROR","#FORMULA"})
    void rawInvalidNumbersCannotBecomeDifferentAdmissibleAmounts(String raw) throws Exception {
        for(String sheet:new String[]{"Monthly Budget","Jun26"}) {
            for(String route:new String[]{"map","validate"}) {
                http.perform(multipart("/migration/workbook/"+route).file(workbook(sheet,raw)))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string(anyOf(containsString("cell B2"),containsString("row 2"))));
                assertEquals(0,artifacts.count(),"Rejected input must not commit a preview/validation receipt");
                assertEquals(0,accounts.count());assertEquals(0,commitments.count());
                assertEquals(0,categories.count());assertEquals(0,transactions.count());
            }
        }
    }

    @ParameterizedTest
    @CsvSource({"12.34,12.34","1.234E1,12.34","1E2,100.0","0E-400,0.0"})
    void exactRawCentsRemainReviewableAndPersistWithoutRounding(String raw,double expected) throws Exception {
        for(String sheet:new String[]{"Monthly Budget","Jun26"}) {
            http.perform(multipart("/migration/workbook/map").file(workbook(sheet,raw))).andExpect(status().isOk());
            var result=http.perform(multipart("/migration/workbook/validate").file(workbook(sheet,raw)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.valid").value(true)).andReturn();
            var validation=json.readTree(result.getResponse().getContentAsString());
            http.perform(post("/migration/workbook/execute").contentType("application/json")
                .content(json.writeValueAsString(Map.of("validationId",validation.path("validationId").asText(),
                    "confirmationToken",validation.path("confirmationToken").asText(),"mode","MERGE"))))
                .andExpect(status().isOk());
            if(sheet.equals("Jun26")) assertEquals(expected,accounts.findAll().getFirst().getBalance());
            else assertEquals(expected,commitments.findAll().getFirst().getAmount());
        }
    }

    private MockMultipartFile workbook(String sheetName,String raw) throws Exception {
        try(var workbook=new XSSFWorkbook();var output=new ByteArrayOutputStream()) {
            var sheet=workbook.createSheet(sheetName);
            var header=sheet.createRow(0);header.createCell(0).setCellValue("Metric");header.createCell(1).setCellValue("Amount");
            var row=sheet.createRow(1);row.createCell(0).setCellValue(sheetName.equals("Jun26")?"Checking":"Synthetic rent");
            var amount=row.createCell(1);amount.setCellValue(1.0);
            var style=workbook.createCellStyle();style.setDataFormat(workbook.createDataFormat().getFormat("0.00"));amount.setCellStyle(style);
            if(raw.equals("#ERROR")) amount.setCellErrorValue(FormulaError.DIV0.getCode());
            else if(raw.equals("#FORMULA")) amount.setCellFormula("1+1");
            else amount.getCTCell().setV(raw); // Preserve the crafted OOXML lexeme, never a test-side double conversion.
            workbook.write(output);
            return new MockMultipartFile("file","synthetic-raw-number.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",output.toByteArray());
        }
    }
}
