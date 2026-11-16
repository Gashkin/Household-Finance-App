package com.householdfinance.service;

import com.householdfinance.entity.AccountEntity;
import com.householdfinance.entity.CategoryEntity;
import com.householdfinance.entity.MonthlyCommitmentEntity;
import com.householdfinance.entity.TransactionEntity;
import com.householdfinance.entity.TransactionType;
import com.householdfinance.repository.AccountRepository;
import com.householdfinance.repository.CategoryRepository;
import com.householdfinance.repository.MonthlyCommitmentRepository;
import com.householdfinance.repository.TransactionRepository;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.concurrent.ConcurrentHashMap;

@Service
@SuppressWarnings("null")
public class WorkbookIngestionService {

    private enum DomainTarget {
        ACCOUNTS,
        CATEGORIES,
        COMMITMENTS,
        TRANSACTIONS,
        UNKNOWN
    }

    private enum LoadMode {
        REPLACE,
        MERGE
    }

    private enum ValidationSeverity {
        ERROR,
        WARNING
    }

    private static final String CURRENT_MONTH_SHEET = "Jun26";
    private static final Pattern MONTH_SUFFIX_TAB_PATTERN = Pattern.compile(".*26$");
        private static final DateTimeFormatter DUE_MONTH_LABEL = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("MMMyy")
            .toFormatter(Locale.ENGLISH);

    public record WorkbookSheetPreview(String sheetName, int rowCount, List<String> headers, List<List<String>> sampleRows) {}

    public record WorkbookPreview(String importId, int sheetCount, int totalRowCount, List<WorkbookSheetPreview> sheets) {}

    public record MappedAccount(String name, String type, double balance, String sourceSheet, int sourceRow) {}

    public record MappedCategory(String name, String budget, String sourceSheet, int sourceRow) {}

    public record MappedCommitment(String title, double amount, String dueDate, String sourceSheet, int sourceRow) {}

    public record MappedTransaction(String date, String description, double amount, String account, String sourceSheet, int sourceRow) {}

    public record MappingWarning(String sheetName, int rowNumber, String message) {}

    public record WorkbookMappingResult(
            String importId,
            int sheetCount,
            int mappedAccountsCount,
            int mappedCategoriesCount,
            int mappedCommitmentsCount,
            int mappedTransactionsCount,
            int recurringCommitmentsCount,
            int oneOffCommitmentsCount,
            List<String> targetingRulesApplied,
            List<String> unmappedSheets,
            List<MappingWarning> warnings,
            List<MappedAccount> accounts,
            List<MappedCategory> categories,
            List<MappedCommitment> commitments,
            List<MappedTransaction> transactions
            ) {}

            public record ValidationIssue(String component, String rowReference, String severity, String message) {}

            public record LoadComponentCounts(int accounts, int categories, int commitments, int transactions) {}

            public record WorkbookLoadValidationResult(
                String validationId,
                String mode,
                boolean valid,
                int hardErrorCount,
                int warningCount,
                LoadComponentCounts counts,
                List<ValidationIssue> issues,
                String confirmationToken
            ) {}

            public record WorkbookLoadExecuteRequest(String validationId, String confirmationToken, String mode) {}

            public record WorkbookLoadExecutionResult(
                String executionId,
                String validationId,
                String mode,
                int appliedAccounts,
                int appliedCategories,
                int appliedCommitments,
                int skippedTransactions,
                String notes
            ) {}

            public record LoadRejectedRow(String component, String rowReference, String reason) {}

            public record WorkbookLoadPostSummary(
                String executionId,
                String validationId,
                String mode,
                String status,
                String createdAt,
                int appliedAccounts,
                int appliedCategories,
                int appliedCommitments,
                int rejectedRows,
                String notes
            ) {}

            public record WorkbookLoadRollbackResult(String executionId, String status, String message) {}

            public record LoadRejectedRowsPayload(List<LoadRejectedRow> rows) {}

            public record SnapshotAccount(String id, String name, String type, double balance) {}

            public record SnapshotCategory(String id, String name, String budget) {}

            public record SnapshotMonthlyCommitment(String id, String title, double amount, int dayOfMonth, String category) {}

            public record SnapshotTransaction(
                String id,
                String txnDate,
                String title,
                double amount,
                String category,
                String txnType,
                String sourceMonthlyCommitmentId
            ) {}

            public record PlannerSnapshotPayload(
                List<SnapshotAccount> accounts,
                List<SnapshotCategory> categories,
                List<SnapshotMonthlyCommitment> monthlyCommitments,
                List<SnapshotTransaction> transactions
            ) {}

            private record PendingValidation(
                String validationId,
                LoadMode mode,
                WorkbookMappingResult mapping,
                Instant createdAt
            ) {}

    private final MigrationArtifactService migrationArtifactService;
            private final AccountRepository accountRepository;
            private final CategoryRepository categoryRepository;
            private final MonthlyCommitmentRepository monthlyCommitmentRepository;
            private final TransactionRepository transactionRepository;
            private final Map<String, PendingValidation> pendingValidations = new ConcurrentHashMap<>();

            public WorkbookIngestionService(
                MigrationArtifactService migrationArtifactService,
                AccountRepository accountRepository,
                CategoryRepository categoryRepository,
                MonthlyCommitmentRepository monthlyCommitmentRepository,
                TransactionRepository transactionRepository
            ) {
        this.migrationArtifactService = migrationArtifactService;
            this.accountRepository = accountRepository;
            this.categoryRepository = categoryRepository;
            this.monthlyCommitmentRepository = monthlyCommitmentRepository;
            this.transactionRepository = transactionRepository;
    }

    public WorkbookPreview previewWorkbook(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Workbook file is required.");
        }

        try (Workbook workbook = WorkbookSafety.open(file)) {
            var formatter = new DataFormatter();
            var sheetPreviews = new ArrayList<WorkbookSheetPreview>();
            var totalRows = 0;

            for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
                var sheet = workbook.getSheetAt(sheetIndex);
                var headers = extractHeaders(sheet.getRow(sheet.getFirstRowNum()), formatter);
                var sampleRows = extractSampleRows(sheet, formatter, 5);
                var rowCount = Math.max(0, sheet.getLastRowNum() - sheet.getFirstRowNum());

                totalRows += rowCount;
                sheetPreviews.add(new WorkbookSheetPreview(sheet.getSheetName(), rowCount, headers, sampleRows));
            }

            var preview = new WorkbookPreview(
                    UUID.randomUUID().toString(),
                    workbook.getNumberOfSheets(),
                    totalRows,
                    List.copyOf(sheetPreviews)
            );
            migrationArtifactService.recordArtifact(preview.importId(), "WORKBOOK_PREVIEW", preview);
            return preview;
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to read workbook file.", exception);
        }
    }

    public WorkbookMappingResult mapWorkbook(MultipartFile file) {
        var mappingResult = mapWorkbookInternal(file);
        migrationArtifactService.recordArtifact(mappingResult.importId(), "WORKBOOK_MAPPING", mappingResult);
        return mappingResult;
    }

    public WorkbookLoadValidationResult validateWorkbookLoad(MultipartFile file, String mode) {
        var loadMode = parseLoadMode(mode);
        var mappingResult = mapWorkbookInternal(file);

        var issues = new ArrayList<ValidationIssue>();
        for (var warning : mappingResult.warnings()) {
            issues.add(new ValidationIssue(
                    "MAPPING",
                    warning.sheetName() + ":" + warning.rowNumber(),
                    ValidationSeverity.WARNING.name(),
                    warning.message()
            ));
        }

        validateMappedAccounts(mappingResult.accounts(), issues);
        validateMappedCategories(mappingResult.categories(), issues);
        validateMappedCommitments(mappingResult.commitments(), issues);
        validateMappedTransactions(mappingResult.transactions(), issues);

        if (mappingResult.accounts().isEmpty() && mappingResult.categories().isEmpty() && mappingResult.commitments().isEmpty()) {
            issues.add(new ValidationIssue(
                    "LOAD",
                    "Workbook",
                    ValidationSeverity.ERROR.name(),
                    "No loadable account/category/commitment rows were found."
            ));
        }

        var hardErrors = (int) issues.stream()
                .filter(issue -> ValidationSeverity.ERROR.name().equals(issue.severity()))
                .count();
        var warnings = (int) issues.stream()
                .filter(issue -> ValidationSeverity.WARNING.name().equals(issue.severity()))
                .count();

        var validationId = UUID.randomUUID().toString();
        var token = hardErrors == 0 ? UUID.randomUUID().toString() : "";
        var result = new WorkbookLoadValidationResult(
                validationId,
                loadMode.name(),
                hardErrors == 0,
                hardErrors,
                warnings,
                new LoadComponentCounts(
                        mappingResult.accounts().size(),
                        mappingResult.categories().size(),
                        mappingResult.commitments().size(),
                        mappingResult.transactions().size()
                ),
                List.copyOf(issues),
                token
        );

        if (!token.isBlank()) {
            pendingValidations.put(token, new PendingValidation(validationId, loadMode, mappingResult, Instant.now()));
        }

        migrationArtifactService.recordArtifact(validationId, "WORKBOOK_LOAD_VALIDATE", result);
        return result;
    }

    @Transactional
    public WorkbookLoadExecutionResult executeValidatedLoad(WorkbookLoadExecuteRequest request) {
        if (request == null || request.confirmationToken() == null || request.confirmationToken().isBlank()) {
            throw new IllegalArgumentException("Confirmation token is required. Validate first.");
        }

        var pending = pendingValidations.remove(request.confirmationToken());
        if (pending == null) {
            throw new IllegalArgumentException("Invalid or expired confirmation token. Re-run validation.");
        }

        if (request.validationId() == null || !request.validationId().equals(pending.validationId())) {
            throw new IllegalArgumentException("Validation id does not match confirmation token.");
        }

        var requestedMode = parseLoadMode(request.mode());
        if (requestedMode != pending.mode()) {
            throw new IllegalArgumentException("Load mode mismatch. Re-run validation.");
        }

        var beforeSnapshot = snapshotPlannerState();

        if (pending.mode() == LoadMode.REPLACE) {
            transactionRepository.deleteAll();
            monthlyCommitmentRepository.deleteAll();
            categoryRepository.deleteAll();
            accountRepository.deleteAll();
        }

        var appliedAccounts = applyAccounts(pending.mapping().accounts(), pending.mode());
        var appliedCategories = applyCategories(pending.mapping().categories(), pending.mode());
        var appliedCommitments = applyCommitments(pending.mapping().commitments(), pending.mode());
        var skippedTransactions = pending.mapping().transactions().size();

        var result = new WorkbookLoadExecutionResult(
                UUID.randomUUID().toString(),
                pending.validationId(),
                pending.mode().name(),
                appliedAccounts,
                appliedCategories,
                appliedCommitments,
                skippedTransactions,
                skippedTransactions == 0
                        ? "Workbook load applied successfully."
                    : "Workbook load applied for persisted components. Mapped transaction rows are not persisted yet."
        );

        var rejectedRows = pending.mapping().transactions().stream()
                .map(transaction -> new LoadRejectedRow(
                        "TRANSACTIONS",
                        rowRef(transaction.sourceSheet(), transaction.sourceRow()),
                    "Mapped transaction rows are not persisted in this phase."
                ))
                .toList();

        var summary = new WorkbookLoadPostSummary(
                result.executionId(),
                result.validationId(),
                result.mode(),
                "APPLIED",
                Instant.now().toString(),
                result.appliedAccounts(),
                result.appliedCategories(),
                result.appliedCommitments(),
                rejectedRows.size(),
                result.notes()
        );

        migrationArtifactService.recordArtifact(result.validationId(), "WORKBOOK_LOAD_EXECUTE", result);
        migrationArtifactService.recordArtifact(result.executionId(), "WORKBOOK_LOAD_SUMMARY", summary);
        migrationArtifactService.recordArtifact(result.executionId(), "WORKBOOK_LOAD_REJECTIONS", new LoadRejectedRowsPayload(rejectedRows));
        migrationArtifactService.recordArtifact(result.executionId(), "WORKBOOK_LOAD_SNAPSHOT", beforeSnapshot);
        return result;
    }

    public WorkbookLoadPostSummary getLatestLoadSummary() {
        var summary = migrationArtifactService.getLatestPayloadByArtifactType(
                "WORKBOOK_LOAD_SUMMARY",
                WorkbookLoadPostSummary.class
        );
        if (summary == null) {
            throw new IllegalArgumentException("No load execution has been recorded yet.");
        }
        return summary;
    }

    public WorkbookLoadPostSummary getLoadSummary(String executionId) {
        var summary = migrationArtifactService.getLatestPayload(
                executionId,
                "WORKBOOK_LOAD_SUMMARY",
                WorkbookLoadPostSummary.class
        );
        if (summary == null) {
            throw new IllegalArgumentException("Load execution not found: " + executionId);
        }
        return summary;
    }

    public List<LoadRejectedRow> getRejectedRows(String executionId) {
        var payload = migrationArtifactService.getLatestPayload(
                executionId,
                "WORKBOOK_LOAD_REJECTIONS",
                LoadRejectedRowsPayload.class
        );
        if (payload == null) {
            throw new IllegalArgumentException("Load execution not found: " + executionId);
        }
        return payload.rows();
    }

    @Transactional
    public WorkbookLoadRollbackResult rollbackLoad(String executionId) {
        var summary = getLoadSummary(executionId);
        if ("ROLLED_BACK".equals(summary.status())) {
            throw new IllegalArgumentException("Load execution already rolled back: " + executionId);
        }

        var snapshot = migrationArtifactService.getLatestPayload(
                executionId,
                "WORKBOOK_LOAD_SNAPSHOT",
                PlannerSnapshotPayload.class
        );
        if (snapshot == null) {
            throw new IllegalArgumentException("No rollback snapshot found for load execution: " + executionId);
        }

        restorePlannerState(snapshot);

        var updatedSummary = new WorkbookLoadPostSummary(
                summary.executionId(),
                summary.validationId(),
                summary.mode(),
                "ROLLED_BACK",
                summary.createdAt(),
                summary.appliedAccounts(),
                summary.appliedCategories(),
                summary.appliedCommitments(),
                summary.rejectedRows(),
                "Rollback completed. Planner components restored to pre-load snapshot."
        );

        var rollback = new WorkbookLoadRollbackResult(executionId, "ROLLED_BACK", updatedSummary.notes());
        migrationArtifactService.recordArtifact(executionId, "WORKBOOK_LOAD_SUMMARY", updatedSummary);
        migrationArtifactService.recordArtifact(executionId, "WORKBOOK_LOAD_ROLLBACK", rollback);
        return rollback;
    }

    private PlannerSnapshotPayload snapshotPlannerState() {
        var accounts = accountRepository.findAll().stream()
                .map(entity -> new SnapshotAccount(entity.getId(), entity.getName(), entity.getType(), entity.getBalance()))
                .toList();
        var categories = categoryRepository.findAll().stream()
                .map(entity -> new SnapshotCategory(entity.getId(), entity.getName(), entity.getBudget()))
                .toList();
        var monthlyCommitments = monthlyCommitmentRepository.findAll().stream()
            .map(entity -> new SnapshotMonthlyCommitment(
                entity.getId(),
                entity.getTitle(),
                entity.getAmount(),
                entity.getDayOfMonth(),
                entity.getCategory()
            ))
            .toList();
        var transactions = transactionRepository.findAll().stream()
            .map(entity -> new SnapshotTransaction(
                entity.getId(),
                entity.getTxnDate().toString(),
                entity.getTitle(),
                entity.getAmount(),
                entity.getCategory(),
                entity.getTxnType().name(),
                entity.getSourceMonthlyCommitmentId()
            ))
                .toList();

        return new PlannerSnapshotPayload(accounts, categories, monthlyCommitments, transactions);
    }

    private void restorePlannerState(PlannerSnapshotPayload snapshot) {
        transactionRepository.deleteAll();
        monthlyCommitmentRepository.deleteAll();
        categoryRepository.deleteAll();
        accountRepository.deleteAll();

        accountRepository.saveAll(snapshot.accounts().stream()
            .map(account -> new AccountEntity(account.id(), account.name(), account.type(), account.balance()))
            .toList());
        categoryRepository.saveAll(snapshot.categories().stream()
            .map(category -> new CategoryEntity(category.id(), category.name(), category.budget()))
            .toList());
        monthlyCommitmentRepository.saveAll(snapshot.monthlyCommitments().stream()
            .map(commitment -> new MonthlyCommitmentEntity(
                commitment.id(),
                commitment.title(),
                commitment.amount(),
                commitment.dayOfMonth(),
                commitment.category()
                ))
                .toList());
        transactionRepository.saveAll(snapshot.transactions().stream()
            .map(transaction -> new TransactionEntity(
                transaction.id(),
                LocalDate.parse(transaction.txnDate()),
                transaction.title(),
                transaction.amount(),
                transaction.category(),
                TransactionType.valueOf(transaction.txnType()),
                transaction.sourceMonthlyCommitmentId()
            ))
                .toList());
    }

    private WorkbookMappingResult mapWorkbookInternal(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Workbook file is required.");
        }

        try (Workbook workbook = WorkbookSafety.open(file)) {
            var formatter = new DataFormatter();
            var accounts = new ArrayList<MappedAccount>();
            var categories = new ArrayList<MappedCategory>();
            var commitments = new ArrayList<MappedCommitment>();
            var transactions = new ArrayList<MappedTransaction>();
            var warnings = new ArrayList<MappingWarning>();
            var unmappedSheets = new ArrayList<String>();
            var targetingRulesApplied = new ArrayList<String>();

            for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
                var sheet = workbook.getSheetAt(sheetIndex);
                var headerRow = sheet.getRow(sheet.getFirstRowNum());
                var headers = extractHeaders(headerRow, formatter);
                var headerIndex = createHeaderIndex(headers);

                if (isMonthlyBudgetSheet(sheet.getSheetName())) {
                    targetingRulesApplied.add("Monthly Budget -> recurring commitments");
                    mapRecurringCommitmentSheet(sheet.getSheetName(), sheet, headerIndex, formatter, commitments, warnings);
                    continue;
                }

                if (isMonthSuffixSheet(sheet.getSheetName())) {
                    targetingRulesApplied.add("*26 tab -> one-off commitments");
                    if (isCurrentMonthSheet(sheet.getSheetName())) {
                        targetingRulesApplied.add("Jun26 bank amounts -> current balances");
                    }
                    mapMonthSuffixSheet(
                            sheet.getSheetName(),
                            sheet,
                            headerIndex,
                            formatter,
                            accounts,
                            commitments,
                            warnings
                    );
                    continue;
                }

                unmappedSheets.add(sheet.getSheetName());
                warnings.add(new MappingWarning(
                        sheet.getSheetName(),
                        sheet.getFirstRowNum() + 1,
                        "Sheet ignored by targeted upload rules. Only Monthly Budget and tabs ending in 26 are loaded."
                ));
            }

                var recurringCommitmentsCount = (int) commitments.stream()
                    .filter(commitment -> "Recurring".equalsIgnoreCase(commitment.dueDate()))
                    .count();
                var oneOffCommitmentsCount = commitments.size() - recurringCommitmentsCount;

            return new WorkbookMappingResult(
                    UUID.randomUUID().toString(),
                    workbook.getNumberOfSheets(),
                    accounts.size(),
                    categories.size(),
                    commitments.size(),
                    transactions.size(),
                    recurringCommitmentsCount,
                    oneOffCommitmentsCount,
                    List.copyOf(targetingRulesApplied),
                    List.copyOf(unmappedSheets),
                    List.copyOf(warnings),
                    List.copyOf(accounts),
                    List.copyOf(categories),
                    List.copyOf(commitments),
                    List.copyOf(transactions)
            );
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to read workbook file.", exception);
        }
    }

    private void validateMappedAccounts(List<MappedAccount> accounts, List<ValidationIssue> issues) {
        for (var account : accounts) {
            if (account.name() == null || account.name().isBlank()) {
                issues.add(new ValidationIssue("ACCOUNTS", rowRef(account.sourceSheet(), account.sourceRow()), ValidationSeverity.ERROR.name(), "Account name is required."));
            }
            if (account.type() == null || account.type().isBlank()) {
                issues.add(new ValidationIssue("ACCOUNTS", rowRef(account.sourceSheet(), account.sourceRow()), ValidationSeverity.ERROR.name(), "Account type is required."));
            }
        }
    }

    private void validateMappedCategories(List<MappedCategory> categories, List<ValidationIssue> issues) {
        for (var category : categories) {
            if (category.name() == null || category.name().isBlank()) {
                issues.add(new ValidationIssue("CATEGORIES", rowRef(category.sourceSheet(), category.sourceRow()), ValidationSeverity.ERROR.name(), "Category name is required."));
            }

            var parsedBudget = parseAmount(category.budget());
            if (parsedBudget <= 0) {
                issues.add(new ValidationIssue("CATEGORIES", rowRef(category.sourceSheet(), category.sourceRow()), ValidationSeverity.WARNING.name(), "Category budget is zero or invalid."));
            }
        }
    }

    private void validateMappedCommitments(List<MappedCommitment> commitments, List<ValidationIssue> issues) {
        for (var commitment : commitments) {
            if (commitment.title() == null || commitment.title().isBlank()) {
                issues.add(new ValidationIssue("COMMITMENTS", rowRef(commitment.sourceSheet(), commitment.sourceRow()), ValidationSeverity.ERROR.name(), "Commitment title is required."));
            }
            if (commitment.dueDate() == null || commitment.dueDate().isBlank()) {
                issues.add(new ValidationIssue("COMMITMENTS", rowRef(commitment.sourceSheet(), commitment.sourceRow()), ValidationSeverity.ERROR.name(), "Commitment due date is required."));
            }
        }
    }

    private void mapRecurringCommitmentSheet(
            String sheetName,
            org.apache.poi.ss.usermodel.Sheet sheet,
            Map<String, Integer> headerIndex,
            DataFormatter formatter,
            List<MappedCommitment> commitments,
            List<MappingWarning> warnings
    ) {
        var start = sheet.getFirstRowNum() + 1;
        for (int rowIndex = start; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            var row = sheet.getRow(rowIndex);
            if (row == null || isRowEmpty(row, formatter)) {
                continue;
            }

            var title = cellValue(row, formatter, headerIndex, "metric", "commitment", "title", "name", "category");
            var amount = parseAmount(cellValue(row, formatter, headerIndex, "amount", "value", "budget"));

            if (title.isBlank()) {
                warnings.add(new MappingWarning(sheetName, rowIndex + 1, "Monthly Budget row skipped: commitment title is missing."));
                continue;
            }

            commitments.add(new MappedCommitment(title, round(amount), "Recurring", sheetName, rowIndex + 1));
        }
    }

    private void mapMonthSuffixSheet(
            String sheetName,
            org.apache.poi.ss.usermodel.Sheet sheet,
            Map<String, Integer> headerIndex,
            DataFormatter formatter,
            List<MappedAccount> accounts,
            List<MappedCommitment> commitments,
            List<MappingWarning> warnings
    ) {
        var start = sheet.getFirstRowNum() + 1;
        for (int rowIndex = start; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            var row = sheet.getRow(rowIndex);
            if (row == null || isRowEmpty(row, formatter)) {
                continue;
            }

            var name = cellValue(row, formatter, headerIndex, "metric", "name", "account", "title", "category");
            var amount = parseAmount(cellValue(row, formatter, headerIndex, "amount", "value", "balance", "budget"));

            if (name.isBlank()) {
                warnings.add(new MappingWarning(sheetName, rowIndex + 1, "Month-suffix row skipped: name/metric is missing."));
                continue;
            }

            if (isCurrentMonthSheet(sheetName) && isBankAccountLabel(name)) {
                accounts.add(new MappedAccount(name, "Bank", round(amount), sheetName, rowIndex + 1));
            } else {
                commitments.add(new MappedCommitment(name, round(amount), sheetName, sheetName, rowIndex + 1));
            }
        }
    }

    private boolean isMonthlyBudgetSheet(String sheetName) {
        return "monthlybudget".equals(normalize(sheetName));
    }

    private boolean isMonthSuffixSheet(String sheetName) {
        return MONTH_SUFFIX_TAB_PATTERN.matcher(normalize(sheetName)).matches();
    }

    private boolean isCurrentMonthSheet(String sheetName) {
        return normalize(CURRENT_MONTH_SHEET).equals(normalize(sheetName));
    }

    private boolean isBankAccountLabel(String label) {
        var normalized = normalize(label);
        return normalized.contains("checking")
                || normalized.contains("savings")
                || normalized.contains("bank")
                || normalized.contains("creditcard");
    }

    private void validateMappedTransactions(List<MappedTransaction> transactions, List<ValidationIssue> issues) {
        for (var transaction : transactions) {
            if (transaction.description() == null || transaction.description().isBlank()) {
                issues.add(new ValidationIssue("TRANSACTIONS", rowRef(transaction.sourceSheet(), transaction.sourceRow()), ValidationSeverity.ERROR.name(), "Transaction description is required."));
            }
        }

        if (!transactions.isEmpty()) {
            issues.add(new ValidationIssue(
                    "TRANSACTIONS",
                    "Workbook",
                    ValidationSeverity.WARNING.name(),
                    "Mapped transaction rows are validated but not persisted in this phase."
            ));
        }
    }

    private int applyAccounts(List<MappedAccount> accounts, LoadMode mode) {
        Map<String, AccountEntity> existingByName = new HashMap<>();
        for (var entity : accountRepository.findAll()) {
            existingByName.put(normalize(entity.getName()), entity);
        }

        var applied = 0;
        for (int i = 0; i < accounts.size(); i++) {
            var mapped = accounts.get(i);
            var key = normalize(mapped.name());
            var existing = existingByName.get(key);

            if (mode == LoadMode.MERGE && existing != null) {
                existing.setType(mapped.type());
                existing.setBalance(mapped.balance());
                accountRepository.save(existing);
            } else {
                var id = existing != null ? existing.getId() : generatedId("A", i + 1);
                accountRepository.save(new AccountEntity(id, mapped.name(), mapped.type(), mapped.balance()));
            }
            applied++;
        }
        return applied;
    }

    private int applyCategories(List<MappedCategory> categories, LoadMode mode) {
        Map<String, CategoryEntity> existingByName = new HashMap<>();
        for (var entity : categoryRepository.findAll()) {
            existingByName.put(normalize(entity.getName()), entity);
        }

        var applied = 0;
        for (int i = 0; i < categories.size(); i++) {
            var mapped = categories.get(i);
            var key = normalize(mapped.name());
            var existing = existingByName.get(key);

            if (mode == LoadMode.MERGE && existing != null) {
                existing.setBudget(mapped.budget());
                categoryRepository.save(existing);
            } else {
                var id = existing != null ? existing.getId() : generatedId("C", i + 1);
                categoryRepository.save(new CategoryEntity(id, mapped.name(), mapped.budget()));
            }
            applied++;
        }
        return applied;
    }

    private int applyCommitments(List<MappedCommitment> commitments, LoadMode mode) {
        Map<String, MonthlyCommitmentEntity> existingRecurringByTitle = new HashMap<>();
        for (var entity : monthlyCommitmentRepository.findAll()) {
            existingRecurringByTitle.put(normalize(entity.getTitle()), entity);
        }

        Map<String, TransactionEntity> existingOneOffByTitle = new HashMap<>();
        for (var entity : transactionRepository.findByTxnTypeIn(List.of(TransactionType.ONE_OFF_FUTURE))) {
            existingOneOffByTitle.put(normalize(entity.getTitle()), entity);
        }

        var applied = 0;
        for (int i = 0; i < commitments.size(); i++) {
            var mapped = commitments.get(i);
            var key = normalize(mapped.title());

            if (isRecurringDueDate(mapped.dueDate())) {
                var existingRecurring = existingRecurringByTitle.get(key);
                if (mode == LoadMode.MERGE && existingRecurring != null) {
                    existingRecurring.setAmount(mapped.amount());
                    existingRecurring.setDayOfMonth(1);
                    monthlyCommitmentRepository.save(existingRecurring);
                } else {
                    var id = existingRecurring != null ? existingRecurring.getId() : generatedId("M", i + 1);
                    monthlyCommitmentRepository.save(new MonthlyCommitmentEntity(id, mapped.title(), mapped.amount(), 1, null));
                }
            } else {
                var existingOneOff = existingOneOffByTitle.get(key);
                var dueDate = parseDueMonthLabel(mapped.dueDate());

                if (mode == LoadMode.MERGE && existingOneOff != null) {
                    existingOneOff.setTxnDate(dueDate);
                    existingOneOff.setAmount(mapped.amount());
                    existingOneOff.setTxnType(TransactionType.ONE_OFF_FUTURE);
                    existingOneOff.setSourceMonthlyCommitmentId(null);
                    transactionRepository.save(existingOneOff);
                } else {
                    var id = existingOneOff != null ? existingOneOff.getId() : generatedId("T", i + 1);
                    transactionRepository.save(new TransactionEntity(
                            id,
                            dueDate,
                            mapped.title(),
                            mapped.amount(),
                            null,
                            TransactionType.ONE_OFF_FUTURE,
                            null
                    ));
                }
            }
            applied++;
        }
        return applied;
    }

    private boolean isRecurringDueDate(String dueDate) {
        return dueDate != null && "Recurring".equalsIgnoreCase(dueDate.trim());
    }

    private LocalDate parseDueMonthLabel(String dueDate) {
        try {
            var monthYear = YearMonth.parse(dueDate, DUE_MONTH_LABEL);
            return monthYear.atDay(1);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Commitment dueDate must be 'Recurring' or a month label like Jul26.");
        }
    }

    private LoadMode parseLoadMode(String rawMode) {
        if (rawMode == null || rawMode.isBlank()) {
            return LoadMode.MERGE;
        }

        try {
            return LoadMode.valueOf(rawMode.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported load mode: " + rawMode);
        }
    }

    private String generatedId(String prefix, int index) {
        return "WL-" + prefix + "-" + index + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
    }

    private String rowRef(String sheet, int row) {
        var safeSheet = (sheet == null || sheet.isBlank()) ? "UnknownSheet" : sheet;
        return safeSheet + ":" + row;
    }

    private List<String> extractHeaders(Row headerRow, DataFormatter formatter) {
        if (headerRow == null) {
            return List.of();
        }

        var headers = new ArrayList<String>();
        for (int cellIndex = 0; cellIndex < headerRow.getLastCellNum(); cellIndex++) {
            headers.add(formatter.formatCellValue(headerRow.getCell(cellIndex)));
        }
        return List.copyOf(headers);
    }

    private Map<String, Integer> createHeaderIndex(List<String> headers) {
        var index = new HashMap<String, Integer>();
        for (int i = 0; i < headers.size(); i++) {
            index.put(normalize(headers.get(i)), i);
        }
        return index;
    }

    private DomainTarget detectTarget(String sheetName, List<String> headers) {
        var normalizedSheet = normalize(sheetName);
        if (normalizedSheet.contains("account")) {
            return DomainTarget.ACCOUNTS;
        }
        if (normalizedSheet.contains("categor") || normalizedSheet.contains("budget")) {
            return DomainTarget.CATEGORIES;
        }
        if (normalizedSheet.contains("commit") || normalizedSheet.contains("bill")) {
            return DomainTarget.COMMITMENTS;
        }
        if (normalizedSheet.contains("transact") || normalizedSheet.contains("ledger")) {
            return DomainTarget.TRANSACTIONS;
        }

        var normalizedHeaders = headers.stream().map(this::normalize).toList();
        if (containsAny(normalizedHeaders, "balance", "account", "type")) {
            return DomainTarget.ACCOUNTS;
        }
        if (containsAny(normalizedHeaders, "category", "budget")) {
            return DomainTarget.CATEGORIES;
        }
        if (containsAny(normalizedHeaders, "due", "commitment", "title")) {
            return DomainTarget.COMMITMENTS;
        }
        if (containsAny(normalizedHeaders, "description", "amount", "date")) {
            return DomainTarget.TRANSACTIONS;
        }
        return DomainTarget.UNKNOWN;
    }

    private boolean containsAny(List<String> values, String... candidates) {
        for (var value : values) {
            for (var candidate : candidates) {
                if (value.contains(candidate)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void mapAccountRow(
            String sheetName,
            int rowIndex,
            Row row,
            Map<String, Integer> headerIndex,
            DataFormatter formatter,
            List<MappedAccount> accounts,
            List<MappingWarning> warnings
    ) {
        var name = cellValue(row, formatter, headerIndex, "account", "name", "metric", "title");
        if (name.isBlank()) {
            warnings.add(new MappingWarning(sheetName, rowIndex + 1, "Skipped account row without account name."));
            return;
        }

        var type = cellValue(row, formatter, headerIndex, "type", "kind");
        var balance = parseAmount(cellValue(row, formatter, headerIndex, "balance", "amount", "value"));
        accounts.add(new MappedAccount(name, type.isBlank() ? "Unspecified" : type, round(balance), sheetName, rowIndex + 1));
    }

    private void mapCategoryRow(
            String sheetName,
            int rowIndex,
            Row row,
            Map<String, Integer> headerIndex,
            DataFormatter formatter,
            List<MappedCategory> categories,
            List<MappingWarning> warnings
    ) {
        var name = cellValue(row, formatter, headerIndex, "category", "name", "metric");
        if (name.isBlank()) {
            warnings.add(new MappingWarning(sheetName, rowIndex + 1, "Skipped category row without category name."));
            return;
        }

        var budgetAmount = parseAmount(cellValue(row, formatter, headerIndex, "budget", "amount", "value"));
        var budget = "$" + String.format(Locale.US, "%,.2f", budgetAmount);
        categories.add(new MappedCategory(name, budget, sheetName, rowIndex + 1));
    }

    private void mapCommitmentRow(
            String sheetName,
            int rowIndex,
            Row row,
            Map<String, Integer> headerIndex,
            DataFormatter formatter,
            List<MappedCommitment> commitments,
            List<MappingWarning> warnings
    ) {
        var title = cellValue(row, formatter, headerIndex, "commitment", "title", "name", "metric");
        if (title.isBlank()) {
            warnings.add(new MappingWarning(sheetName, rowIndex + 1, "Skipped commitment row without title."));
            return;
        }

        var amount = parseAmount(cellValue(row, formatter, headerIndex, "amount", "value", "budget"));
        var dueDate = cellValue(row, formatter, headerIndex, "due", "duedate", "date");
        commitments.add(new MappedCommitment(title, round(amount), dueDate, sheetName, rowIndex + 1));
    }

    private void mapTransactionRow(
            String sheetName,
            int rowIndex,
            Row row,
            Map<String, Integer> headerIndex,
            DataFormatter formatter,
            List<MappedTransaction> transactions,
            List<MappingWarning> warnings
    ) {
        var description = cellValue(row, formatter, headerIndex, "description", "memo", "details");
        if (description.isBlank()) {
            warnings.add(new MappingWarning(sheetName, rowIndex + 1, "Skipped transaction row without description."));
            return;
        }

        var date = cellValue(row, formatter, headerIndex, "date", "posted", "transactiondate");
        var account = cellValue(row, formatter, headerIndex, "account", "source", "from");
        var amount = parseAmount(cellValue(row, formatter, headerIndex, "amount", "value", "debit", "credit"));
        transactions.add(new MappedTransaction(date, description, round(amount), account, sheetName, rowIndex + 1));
    }

    private String cellValue(Row row, DataFormatter formatter, Map<String, Integer> headerIndex, String... candidates) {
        for (var candidate : candidates) {
            var index = findHeaderIndex(headerIndex, candidate);
            if (index != null) {
                var value = formatter.formatCellValue(row.getCell(index));
                if (!value.isBlank()) {
                    return value.trim();
                }
            }
        }
        return "";
    }

    private Integer findHeaderIndex(Map<String, Integer> headerIndex, String candidate) {
        var normalizedCandidate = normalize(candidate);
        for (var entry : headerIndex.entrySet()) {
            if (entry.getKey().contains(normalizedCandidate)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private boolean isRowEmpty(Row row, DataFormatter formatter) {
        for (int cellIndex = 0; cellIndex < row.getLastCellNum(); cellIndex++) {
            if (!formatter.formatCellValue(row.getCell(cellIndex)).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replace("_", "").replace(" ", "");
    }

    private double parseAmount(String rawAmount) {
        if (rawAmount == null || rawAmount.isBlank()) {
            return 0.0;
        }

        try {
            return Double.parseDouble(rawAmount.replace("$", "").replace(",", ""));
        } catch (NumberFormatException ignored) {
            return 0.0;
        }
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private List<List<String>> extractSampleRows(org.apache.poi.ss.usermodel.Sheet sheet, DataFormatter formatter, int limit) {
        var rows = new ArrayList<List<String>>();
        var start = sheet.getFirstRowNum() + 1;
        var end = Math.min(sheet.getLastRowNum(), start + limit - 1);

        for (int rowIndex = start; rowIndex <= end; rowIndex++) {
            var row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }

            var cells = new ArrayList<String>();
            for (int cellIndex = 0; cellIndex < row.getLastCellNum(); cellIndex++) {
                cells.add(formatter.formatCellValue(row.getCell(cellIndex)));
            }
            rows.add(List.copyOf(cells));
        }

        return List.copyOf(rows);
    }
}
