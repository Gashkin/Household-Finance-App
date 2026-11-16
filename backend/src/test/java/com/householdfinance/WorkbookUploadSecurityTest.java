package com.householdfinance;

import com.householdfinance.controller.WorkbookIngestionController;
import com.householdfinance.service.*;
import com.householdfinance.repository.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;

/** Real controller/parser path; only persistence is substituted with empty synthetic mocks. */
@WebMvcTest(WorkbookIngestionController.class)
@AutoConfigureMockMvc(addFilters=false)
@Import(WorkbookIngestionService.class)
class WorkbookUploadSecurityTest {
    @Autowired MockMvc http;
    @MockitoBean MigrationArtifactService artifacts;
    @MockitoBean AccountRepository accounts;
    @MockitoBean CategoryRepository categories;
    @MockitoBean MonthlyCommitmentRepository commitments;
    @MockitoBean TransactionRepository transactions;

    @Test void ambiguousDuplicatePartsAreRejectedBeforePreviewMappingOrValidation() throws Exception {
        byte[] duplicate=rewriteWorkbook("xl/worksheets/sheet9.xml",
            "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData><row r=\"1\"><c r=\"A1\"><v>999</v></c></row></sheetData></worksheet>",false);
        // Equal-length name replacement preserves a valid ZIP, including central-directory offsets.
        duplicate=new String(duplicate,StandardCharsets.ISO_8859_1)
            .replace("sheet9.xml","sheet1.xml").getBytes(StandardCharsets.ISO_8859_1);
        rejectAll(duplicate,"Duplicate workbook part");
    }

    @Test void sparseExtremeRowAndColumnCannotForceExpandedParserWork() throws Exception {
        rejectAll(rewriteWorkbook("xl/worksheets/sheet1.xml",
            "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData><row r=\"1048576\"><c r=\"XFD1048576\"><v>1</v></c></row></sheetData></worksheet>",true),"safe intake bounds");
    }

    @Test void expandedZipPartIsBoundedBeforePoiAndArtifactPersistence() throws Exception {
        rejectAll(rewriteWorkbook("synthetic.bin","x".repeat(8*1024*1024+1),false),"expanded content");
    }

    @Test void externalXmlEntitiesAreRejected() throws Exception {
        rejectAll(rewriteWorkbook("xl/worksheets/sheet1.xml",
            "<!DOCTYPE worksheet [<!ENTITY external SYSTEM 'file:///synthetic-never-read'>]><worksheet>&external;</worksheet>",true),"safe intake bounds");
    }

    @Test void ordinarySyntheticWorkbookStillPreviewsAndMaps() throws Exception {
        for(String route:new String[]{"preview","map","validate"})
            http.perform(multipart("/migration/workbook/"+route).file(file(workbook())))
                .andExpect(status().isOk());
    }

    private void rejectAll(byte[] bytes,String message) throws Exception {
        for(String route:new String[]{"preview","map","validate"})
            http.perform(multipart("/migration/workbook/"+route).file(file(bytes)))
                .andExpect(status().isBadRequest()).andExpect(content().string(org.hamcrest.Matchers.containsString(message)));
        verifyNoInteractions(artifacts,accounts,categories,commitments,transactions);
    }
    private MockMultipartFile file(byte[] bytes) {
        return new MockMultipartFile("file","synthetic.xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",bytes);
    }
    private byte[] workbook() throws Exception {
        try(var workbook=new XSSFWorkbook();var output=new ByteArrayOutputStream()) {
            var sheet=workbook.createSheet("Monthly Budget");
            sheet.createRow(0).createCell(0).setCellValue("Metric");
            sheet.getRow(0).createCell(1).setCellValue("Amount");
            sheet.createRow(1).createCell(0).setCellValue("Synthetic rent");
            sheet.getRow(1).createCell(1).setCellValue(100);
            workbook.write(output);return output.toByteArray();
        }
    }
    private byte[] rewriteWorkbook(String name,String payload,boolean replace) throws Exception {
        try(var input=new ZipInputStream(new ByteArrayInputStream(workbook()));
            var output=new ByteArrayOutputStream();var zip=new ZipOutputStream(output)) {
            ZipEntry entry;
            while((entry=input.getNextEntry())!=null) {
                if(replace && entry.getName().equals(name)) continue;
                zip.putNextEntry(new ZipEntry(entry.getName()));input.transferTo(zip);zip.closeEntry();
            }
            zip.putNextEntry(new ZipEntry(name));zip.write(payload.getBytes(StandardCharsets.UTF_8));zip.closeEntry();
            zip.finish();return output.toByteArray();
        }
    }
}
