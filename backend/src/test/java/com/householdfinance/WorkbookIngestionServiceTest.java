package com.householdfinance;

import com.householdfinance.entity.AccountEntity;
import com.householdfinance.entity.CategoryEntity;
import com.householdfinance.entity.MonthlyCommitmentEntity;
import com.householdfinance.entity.TransactionEntity;
import com.householdfinance.entity.TransactionType;
import com.householdfinance.repository.AccountRepository;
import com.householdfinance.repository.CategoryRepository;
import com.householdfinance.repository.MonthlyCommitmentRepository;
import com.householdfinance.repository.TransactionRepository;
import com.householdfinance.service.MigrationArtifactService;
import com.householdfinance.service.WorkbookIngestionService;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkbookIngestionServiceTest {

    @Test
    void mapWorkbookAppliesTargetingRulesForMonthlyBudgetAndJun26() throws Exception {
        var migrationArtifactService = mock(MigrationArtifactService.class);
        var accountRepository = mock(AccountRepository.class);
        var categoryRepository = mock(CategoryRepository.class);
        var monthlyCommitmentRepository = mock(MonthlyCommitmentRepository.class);
        var transactionRepository = mock(TransactionRepository.class);

        var service = new WorkbookIngestionService(
                migrationArtifactService,
                accountRepository,
                categoryRepository,
            monthlyCommitmentRepository,
            transactionRepository
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

    @Test
    void executeValidatedLoadPersistsRecurringAndOneOffCommitmentsToSplitTables() throws Exception {
    var migrationArtifactService = mock(MigrationArtifactService.class);
    var accountRepository = mock(AccountRepository.class);
    var categoryRepository = mock(CategoryRepository.class);
    var monthlyCommitmentRepository = mock(MonthlyCommitmentRepository.class);
    var transactionRepository = mock(TransactionRepository.class);

    when(accountRepository.findAll()).thenReturn(List.of());
    when(categoryRepository.findAll()).thenReturn(List.of());
    when(monthlyCommitmentRepository.findAll()).thenReturn(List.of());
    when(transactionRepository.findAll()).thenReturn(List.of());
    when(transactionRepository.findByTxnTypeIn(any())).thenReturn(List.of());
    doAnswer(invocation -> invocation.getArgument(0)).when(accountRepository).save(any());
    doAnswer(invocation -> invocation.getArgument(0)).when(monthlyCommitmentRepository).save(any());
    doAnswer(invocation -> invocation.getArgument(0)).when(transactionRepository).save(any());

    var service = new WorkbookIngestionService(
        migrationArtifactService,
        accountRepository,
        categoryRepository,
        monthlyCommitmentRepository,
        transactionRepository
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

    var output = new ByteArrayOutputStream();
    workbook.write(output);
    workbook.close();

    var multipartFile = new MockMultipartFile(
        "file",
        "split-persistence.xlsx",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        output.toByteArray()
    );

    var validation = service.validateWorkbookLoad(multipartFile, "MERGE");
    var result = service.executeValidatedLoad(new WorkbookIngestionService.WorkbookLoadExecuteRequest(
        validation.validationId(),
        validation.confirmationToken(),
        "MERGE"
    ));

    assertEquals(2, result.appliedCommitments());
    assertEquals(0, result.skippedTransactions());
    assertEquals("Workbook load applied successfully.", result.notes());

    var recurringCaptor = ArgumentCaptor.forClass(MonthlyCommitmentEntity.class);
    verify(monthlyCommitmentRepository, times(1)).save(recurringCaptor.capture());
    assertEquals("Rent", recurringCaptor.getValue().getTitle());
    assertEquals(1200.0, recurringCaptor.getValue().getAmount());
    assertEquals(1, recurringCaptor.getValue().getDayOfMonth());

    var oneOffCaptor = ArgumentCaptor.forClass(TransactionEntity.class);
    verify(transactionRepository, times(1)).save(oneOffCaptor.capture());
    assertEquals("Car Service", oneOffCaptor.getValue().getTitle());
    assertEquals(300.0, oneOffCaptor.getValue().getAmount());
    assertEquals(LocalDate.of(2026, 6, 1), oneOffCaptor.getValue().getTxnDate());
    assertEquals(TransactionType.ONE_OFF_FUTURE, oneOffCaptor.getValue().getTxnType());
    assertNull(oneOffCaptor.getValue().getSourceMonthlyCommitmentId());
    }

    @Test
    void rollbackLoadRestoresSplitTablesFromSnapshot() {
    var migrationArtifactService = mock(MigrationArtifactService.class);
    var accountRepository = mock(AccountRepository.class);
    var categoryRepository = mock(CategoryRepository.class);
    var monthlyCommitmentRepository = mock(MonthlyCommitmentRepository.class);
    var transactionRepository = mock(TransactionRepository.class);

    var service = new WorkbookIngestionService(
        migrationArtifactService,
        accountRepository,
        categoryRepository,
        monthlyCommitmentRepository,
        transactionRepository
    );

    var summary = new WorkbookIngestionService.WorkbookLoadPostSummary(
        "exec-123",
        "val-123",
        "MERGE",
        "APPLIED",
        "2026-07-13T10:00:00Z",
        1,
        1,
        2,
        0,
        "Workbook load applied successfully."
    );

    var snapshot = new WorkbookIngestionService.PlannerSnapshotPayload(
        List.of(new WorkbookIngestionService.SnapshotAccount("A-1", "Checking", "Bank", 3200.0)),
        List.of(new WorkbookIngestionService.SnapshotCategory("C-1", "Housing", "$1,200.00")),
        List.of(new WorkbookIngestionService.SnapshotMonthlyCommitment("M-1", "Rent", 1200.0, 1, "Housing")),
        List.of(new WorkbookIngestionService.SnapshotTransaction("T-1", "2026-06-01", "Car Service", 300.0, "Auto", "ONE_OFF_FUTURE", null))
    );

    when(migrationArtifactService.getLatestPayload(eq("exec-123"), eq("WORKBOOK_LOAD_SUMMARY"), eq(WorkbookIngestionService.WorkbookLoadPostSummary.class)))
        .thenReturn(summary);
    when(migrationArtifactService.getLatestPayload(eq("exec-123"), eq("WORKBOOK_LOAD_SNAPSHOT"), eq(WorkbookIngestionService.PlannerSnapshotPayload.class)))
        .thenReturn(snapshot);

    var rollback = service.rollbackLoad("exec-123");
    assertEquals("ROLLED_BACK", rollback.status());

    verify(transactionRepository).deleteAll();
    verify(monthlyCommitmentRepository).deleteAll();
    verify(categoryRepository).deleteAll();
    verify(accountRepository).deleteAll();

    verify(accountRepository).saveAll(any());
    verify(categoryRepository).saveAll(any());
    verify(monthlyCommitmentRepository).saveAll(any());
    verify(transactionRepository).saveAll(any());
    verify(transactionRepository, never()).save(any());
    }
}
