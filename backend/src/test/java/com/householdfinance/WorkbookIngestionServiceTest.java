package com.householdfinance;

import com.householdfinance.repository.AccountRepository;
import com.householdfinance.repository.CategoryRepository;
import com.householdfinance.repository.CommitmentRepository;
import com.householdfinance.service.MigrationArtifactService;
import com.householdfinance.service.WorkbookIngestionService;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class WorkbookIngestionServiceTest {

    @Test
    void mapWorkbookAppliesTargetingRulesForMonthlyBudgetAndJun26() throws Exception {
        var migrationArtifactService = mock(MigrationArtifactService.class);
        var accountRepository = mock(AccountRepository.class);
        var categoryRepository = mock(CategoryRepository.class);
        var commitmentRepository = mock(CommitmentRepository.class);

        var service = new WorkbookIngestionService(
                migrationArtifactService,
                accountRepository,
                categoryRepository,
                commitmentRepository
        );

        var workbook = new XSSFWorkbook();

        var monthlyBudget = workbook.createSheet("Monthly Budget");
        var monthlyHeader = monthlyBudget.createRow(0);
        monthlyHeader.createCell(0).setCellValue("Metric");
        monthlyHeader.createCell(1).setCellValue("Amount");
        var monthlyRow = monthlyBudget.createRow(1);
        monthlyRow.createCell(0).setCellValue("Rent");
        monthlyRow.createCell(1).setCellValue(1200.0);

        var junSheet = workbook.createSheet("Jun26");
        var junHeader = junSheet.createRow(0);
        junHeader.createCell(0).setCellValue("Metric");
        junHeader.createCell(1).setCellValue("Amount");

        var junBalance = junSheet.createRow(1);
        junBalance.createCell(0).setCellValue("Checking");
        junBalance.createCell(1).setCellValue(4200.0);

        var junOneOff = junSheet.createRow(2);
        junOneOff.createCell(0).setCellValue("Car Service");
        junOneOff.createCell(1).setCellValue(300.0);

        var ignoredSheet = workbook.createSheet("Categories");
        var ignoredHeader = ignoredSheet.createRow(0);
        ignoredHeader.createCell(0).setCellValue("Category");
        ignoredHeader.createCell(1).setCellValue("Budget");
        var ignoredRow = ignoredSheet.createRow(1);
        ignoredRow.createCell(0).setCellValue("Groceries");
        ignoredRow.createCell(1).setCellValue(450.0);

        var output = new ByteArrayOutputStream();
        workbook.write(output);
        workbook.close();

        var multipartFile = new MockMultipartFile(
                "file",
                "targeting.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                output.toByteArray()
        );

        var result = service.mapWorkbook(multipartFile);

        assertEquals(1, result.mappedAccountsCount());
        assertEquals(0, result.mappedCategoriesCount());
        assertEquals(2, result.mappedCommitmentsCount());
        assertEquals(1, result.recurringCommitmentsCount());
        assertEquals(1, result.oneOffCommitmentsCount());
        assertTrue(result.unmappedSheets().contains("Categories"));
        assertTrue(result.warnings().stream().anyMatch(warning -> warning.message().contains("Only Monthly Budget and tabs ending in 26")));
        assertTrue(result.targetingRulesApplied().contains("Monthly Budget -> recurring commitments"));
        assertTrue(result.targetingRulesApplied().contains("*26 tab -> one-off commitments"));
        assertTrue(result.targetingRulesApplied().contains("Jun26 bank amounts -> current balances"));
        assertTrue(result.accounts().stream().anyMatch(account -> "Checking".equals(account.name())));
        assertTrue(result.commitments().stream().anyMatch(commitment -> "Rent".equals(commitment.title()) && "Recurring".equals(commitment.dueDate())));
        assertTrue(result.commitments().stream().anyMatch(commitment -> "Car Service".equals(commitment.title()) && "Jun26".equals(commitment.dueDate())));

        verify(migrationArtifactService).recordArtifact(result.importId(), "WORKBOOK_MAPPING", result);
    }
}
