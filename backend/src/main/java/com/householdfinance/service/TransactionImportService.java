package com.householdfinance.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Service
public class TransactionImportService {

    public record ImportedTransaction(String rowId, String date, String description, double amount, String account, boolean duplicate) {}
    public record ImportPreview(String importId, List<ImportedTransaction> transactions) {}
    public record ImportAcceptRequest(String importId, List<String> acceptedRowIds) {}
    public record ImportAcceptResult(String importId, int acceptedCount, int skippedCount) {}

    private final MigrationArtifactService migrationArtifactService;

    public TransactionImportService(MigrationArtifactService migrationArtifactService) {
        this.migrationArtifactService = migrationArtifactService;
    }

    public ImportPreview previewCsv(String csvContent) {
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
        migrationArtifactService.recordArtifact(importId, "IMPORT_PREVIEW", preview);
        return preview;
    }

    public ImportAcceptResult acceptImport(ImportAcceptRequest request) {
        var acceptedCount = request.acceptedRowIds() == null ? 0 : request.acceptedRowIds().size();
        var result = new ImportAcceptResult(request.importId(), acceptedCount, 0);
        migrationArtifactService.recordArtifact(request.importId(), "IMPORT_ACCEPT", result);
        return result;
    }

    private double parseAmount(String rawAmount) {
        try {
            return Double.parseDouble(rawAmount.replace("$", "").replace(",", ""));
        } catch (NumberFormatException ignored) {
            return 0.0;
        }
    }
}
