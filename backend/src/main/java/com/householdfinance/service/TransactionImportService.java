package com.householdfinance.service;

import com.householdfinance.entity.TransactionType;
import com.householdfinance.repository.AccountRepository;
import com.householdfinance.repository.TransactionRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class TransactionImportService {

    public record ImportedTransaction(String rowId, String date, String description, double amount, String account, boolean duplicate) {}
    public record ImportPreview(String importId, List<ImportedTransaction> transactions) {}
    public record ImportAcceptRequest(String importId, List<String> acceptedRowIds) {}
    public record ImportAcceptResult(String importId, int acceptedCount, int skippedCount) {}
    public record TransactionHistoryItem(
            String historyId,
            String importId,
            String rowId,
            String date,
            String description,
            double amount,
            String account,
            boolean duplicate,
                String mappedFutureCommitmentId,
                String category,
                String resolution
    ) {}
    public record MapToFutureRequest(String historyId, String futureCommitmentId) {}
    public record MapToFutureResult(String historyId, String futureCommitmentId) {}
            public record ResolveHistoryRequest(String historyId, String action, String futureCommitmentId, String category, String dueDate) {}
            public record ResolveHistoryResult(
                String historyId,
                String action,
                String futureCommitmentId,
                String category,
                String accountId,
                Double updatedBalance
            ) {}

            private record AccountBalanceUpdate(String accountId, double updatedBalance) {}

            private static final String RESOLUTION_UNRESOLVED = "UNRESOLVED";
            private static final String ACTION_MAP_TO_FUTURE = "MAP_TO_FUTURE";
            private static final String ACTION_CREATE_FUTURE = "CREATE_FUTURE";
            private static final String ACTION_SET_ACCOUNTED_FOR = "SET_ACCOUNTED_FOR";

    private final MigrationArtifactService migrationArtifactService;
    private final TransactionRepository transactionRepository;
            private final AccountRepository accountRepository;
    private final Map<String, List<ImportedTransaction>> previewCacheByImportId = new ConcurrentHashMap<>();
    private final List<TransactionHistoryItem> historyItems = new CopyOnWriteArrayList<>();

    public TransactionImportService(
            MigrationArtifactService migrationArtifactService,
                TransactionRepository transactionRepository,
                AccountRepository accountRepository
    ) {
        this.migrationArtifactService = migrationArtifactService;
        this.transactionRepository = transactionRepository;
            this.accountRepository = accountRepository;
    }

    public ImportPreview previewCsv(String csvContent) {
        com.householdfinance.access.AccessContext.requireLegacy();
        var importId = UUID.randomUUID().toString();
        var transactions = new ArrayList<ImportedTransaction>();
        var duplicateKeys = new HashSet<String>();

        if (csvContent == null || csvContent.isBlank()) {
            var emptyPreview = new ImportPreview(importId, List.of());
            migrationArtifactService.recordArtifact(importId, "IMPORT_PREVIEW", emptyPreview);
            return emptyPreview;
        }

        var lines = csvContent.split("\\r?\\n");
        if (lines.length <= 1) {
            var emptyPreview = new ImportPreview(importId, List.of());
            migrationArtifactService.recordArtifact(importId, "IMPORT_PREVIEW", emptyPreview);
            return emptyPreview;
        }

        for (int index = 1; index < lines.length; index++) {
            var line = lines[index].trim();
            if (line.isBlank()) {
                continue;
            }

            var parts = line.split(",");
            if (parts.length < 4) {
                continue;
            }

            var date = parts[0].trim();
            var description = parts[1].trim();
            var amount = parseAmount(parts[2].trim());
            var account = parts[3].trim();

            var dedupeKey = (date + "|" + description + "|" + amount).toLowerCase();
            var duplicate = !duplicateKeys.add(dedupeKey);
            var rowId = "R" + (transactions.size() + 1);

            transactions.add(new ImportedTransaction(rowId, date, description, amount, account, duplicate));
        }

        var preview = new ImportPreview(importId, List.copyOf(transactions));
        previewCacheByImportId.put(importId, preview.transactions());
        migrationArtifactService.recordArtifact(importId, "IMPORT_PREVIEW", preview);
        return preview;
    }

    public ImportAcceptResult acceptImport(ImportAcceptRequest request) {
        com.householdfinance.access.AccessContext.requireLegacy();
        var importId = request.importId();
        var acceptedRowIds = request.acceptedRowIds() == null ? List.<String>of() : request.acceptedRowIds();
        var previewRows = previewCacheByImportId.getOrDefault(importId, List.of());
        var acceptedCount = 0;
        var skippedCount = 0;

        for (var rowId : acceptedRowIds) {
            var previewRow = previewRows.stream()
                    .filter(row -> row.rowId().equals(rowId))
                    .findFirst();
            if (previewRow.isEmpty()) {
                skippedCount += 1;
                continue;
            }

            var row = previewRow.get();
            historyItems.add(new TransactionHistoryItem(
                    "H-" + UUID.randomUUID(),
                    importId,
                    row.rowId(),
                    row.date(),
                    row.description(),
                    row.amount(),
                    row.account(),
                    row.duplicate(),
                    null,
                    null,
                    RESOLUTION_UNRESOLVED
            ));
            acceptedCount += 1;
        }

        var result = new ImportAcceptResult(importId, acceptedCount, skippedCount);
        migrationArtifactService.recordArtifact(request.importId(), "IMPORT_ACCEPT", result);
        return result;
    }

    public List<TransactionHistoryItem> getHistory() {
        com.householdfinance.access.AccessContext.requireLegacy();
        return historyItems.stream()
                .sorted(Comparator.comparing(TransactionHistoryItem::historyId))
                .toList();
    }

    public MapToFutureResult mapToFuture(MapToFutureRequest request) {
        com.householdfinance.access.AccessContext.requireLegacy();
        resolveHistory(new ResolveHistoryRequest(
                request == null ? null : request.historyId(),
                ACTION_MAP_TO_FUTURE,
                request == null ? null : request.futureCommitmentId(),
                null,
                null
        ));
        return new MapToFutureResult(request.historyId(), request.futureCommitmentId());
    }

    public ResolveHistoryResult resolveHistory(ResolveHistoryRequest request) {
        com.householdfinance.access.AccessContext.requireLegacy();
        if (request == null || request.historyId() == null || request.historyId().isBlank()) {
            throw new IllegalArgumentException("historyId is required.");
        }
        if (request.action() == null || request.action().isBlank()) {
            throw new IllegalArgumentException("action is required.");
        }

        var action = request.action().trim().toUpperCase(Locale.ROOT);
        if (!List.of(ACTION_MAP_TO_FUTURE, ACTION_CREATE_FUTURE, ACTION_SET_ACCOUNTED_FOR).contains(action)) {
            throw new IllegalArgumentException("Unsupported action: " + request.action());
        }

        var currentItem = findHistoryItem(request.historyId());

        if (currentItem == null) {
            throw new IllegalArgumentException("Transaction history row not found: " + request.historyId());
        }
        if (!RESOLUTION_UNRESOLVED.equals(currentItem.resolution())) {
            throw new IllegalArgumentException("Transaction history row already resolved: " + request.historyId());
        }

        String resolvedFutureCommitmentId = null;
        String resolvedCategory = currentItem.category();
        String resolvedAction = action;
        AccountBalanceUpdate balanceUpdate = null;

        if (ACTION_MAP_TO_FUTURE.equals(action)) {
            if (request.futureCommitmentId() == null || request.futureCommitmentId().isBlank()) {
                throw new IllegalArgumentException("futureCommitmentId is required for MAP_TO_FUTURE.");
            }

            var futureEntity = transactionRepository.findById(request.futureCommitmentId())
                    .filter(entity -> entity.getTxnType() == TransactionType.ONE_OFF_FUTURE)
                    .orElseThrow(() -> new IllegalArgumentException("Future one-off commitment not found: " + request.futureCommitmentId()));

            resolvedFutureCommitmentId = futureEntity.getId();
            resolvedCategory = coalesceCategory(currentItem.category(), futureEntity.getCategory());
            balanceUpdate = applyAccountBalance(currentItem.account(), currentItem.amount());
        } else if (ACTION_CREATE_FUTURE.equals(action)) {
            var normalizedCategory = normalizeCategory(request.category());
            if (normalizedCategory == null) {
                throw new IllegalArgumentException("category is required for CREATE_FUTURE.");
            }

            var dueDate = parseDateOrDefault(request.dueDate(), currentItem.date());
            var createdId = "T-" + UUID.randomUUID();
            var created = transactionRepository.save(new com.householdfinance.entity.TransactionEntity(
                    createdId,
                    dueDate,
                    currentItem.description(),
                    currentItem.amount(),
                    normalizedCategory,
                    TransactionType.ONE_OFF_FUTURE,
                    null
            ));

            resolvedFutureCommitmentId = created.getId();
            resolvedCategory = normalizedCategory;
            balanceUpdate = applyAccountBalance(currentItem.account(), currentItem.amount());
        } else if (ACTION_SET_ACCOUNTED_FOR.equals(action)) {
            var normalizedCategory = normalizeCategory(request.category());
            if (normalizedCategory == null) {
                throw new IllegalArgumentException("category is required for SET_ACCOUNTED_FOR.");
            }
            resolvedCategory = normalizedCategory;
        }

        historyItems.remove(currentItem);
        historyItems.add(new TransactionHistoryItem(
                currentItem.historyId(),
                currentItem.importId(),
                currentItem.rowId(),
                currentItem.date(),
                currentItem.description(),
                currentItem.amount(),
                currentItem.account(),
                currentItem.duplicate(),
                resolvedFutureCommitmentId,
                resolvedCategory,
                resolvedAction
        ));

        return new ResolveHistoryResult(
                request.historyId(),
                resolvedAction,
                resolvedFutureCommitmentId,
                resolvedCategory,
                balanceUpdate == null ? null : balanceUpdate.accountId(),
                balanceUpdate == null ? null : balanceUpdate.updatedBalance()
        );
    }

    private TransactionHistoryItem findHistoryItem(String historyId) {
        for (var item : historyItems) {
            if (item.historyId().equals(historyId)) {
                return item;
            }
        }
        return null;
    }

    private AccountBalanceUpdate applyAccountBalance(String historyAccountName, double amount) {
        var account = accountRepository.findByNameIgnoreCase(historyAccountName)
                .or(() -> accountRepository.findById(historyAccountName))
                .orElseThrow(() -> new IllegalArgumentException("Account not found for transaction history row: " + historyAccountName));

        account.setBalance(account.getBalance() + amount);
        var saved = accountRepository.save(account);
        return new AccountBalanceUpdate(saved.getId(), saved.getBalance());
    }

    private String normalizeCategory(String category) {
        if (category == null) {
            return null;
        }
        var trimmed = category.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String coalesceCategory(String current, String fallback) {
        var normalizedCurrent = normalizeCategory(current);
        if (normalizedCurrent != null) {
            return normalizedCurrent;
        }
        return normalizeCategory(fallback);
    }

    private LocalDate parseDateOrDefault(String date, String fallbackDate) {
        var candidate = date == null || date.isBlank() ? fallbackDate : date;
        try {
            return LocalDate.parse(candidate.trim());
        } catch (Exception exception) {
            throw new IllegalArgumentException("dueDate must be an ISO date like 2026-07-27.");
        }
    }

    private double parseAmount(String rawAmount) {
        try {
            return Double.parseDouble(rawAmount.replace("$", "").replace(",", ""));
        } catch (NumberFormatException ignored) {
            return 0.0;
        }
    }
}
