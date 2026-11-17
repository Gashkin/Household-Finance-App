package com.householdfinance.service;

import com.householdfinance.repository.*;
import java.io.ByteArrayOutputStream;
import java.time.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@org.junit.jupiter.api.extension.ExtendWith(com.householdfinance.access.OwnerFixture.class)
public class WorkbookReviewBoundaryTest {
    @Test void confirmationExpiresAtFiveMinutesAndExpiredReviewsDoNotOccupyCapacity() throws Exception {
        var clock=mock(Clock.class);
        Instant start=Instant.parse("2026-11-16T12:00:00Z");
        when(clock.instant()).thenReturn(start);
        var accounts=mock(AccountRepository.class);
        var categories=mock(CategoryRepository.class);
        var commitments=mock(MonthlyCommitmentRepository.class);
        var transactions=mock(TransactionRepository.class);
        var service=new WorkbookIngestionService(mock(MigrationArtifactService.class),accounts,categories,commitments,transactions,clock);
        var first=service.validateWorkbookLoad(workbook(),"REPLACE");
        for(int i=0;i<3;i++) service.validateWorkbookLoad(workbook(),"REPLACE");
        assertThrows(IllegalArgumentException.class,()->service.validateWorkbookLoad(workbook(),"REPLACE"));
        when(clock.instant()).thenReturn(start.plusSeconds(300));
        assertThrows(IllegalArgumentException.class,()->service.executeValidatedLoad(
            new WorkbookIngestionService.WorkbookLoadExecuteRequest(first.validationId(),first.confirmationToken(),"REPLACE")));
        assertTrue(service.validateWorkbookLoad(workbook(),"REPLACE").valid());
        verify(accounts,never()).deleteAll();verify(categories,never()).deleteAll();
        verify(commitments,never()).deleteAll();verify(transactions,never()).deleteAll();
    }

    public static MockMultipartFile workbook() throws Exception {
        try(var workbook=new XSSFWorkbook();var output=new ByteArrayOutputStream()) {
            var sheet=workbook.createSheet("Monthly Budget");
            var header=sheet.createRow(0);header.createCell(0).setCellValue("Metric");header.createCell(1).setCellValue("Amount");
            var row=sheet.createRow(1);row.createCell(0).setCellValue("Synthetic rent");row.createCell(1).setCellValue(1200);
            workbook.write(output);
            return new MockMultipartFile("file","synthetic.xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",output.toByteArray());
        }
    }
}
