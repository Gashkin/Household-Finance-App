package com.householdfinance.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@SuppressWarnings("null")
public class SpreadsheetMigrationService {

    public record MigrationRow(String rowId, String sheet, String metric, double baselineAmount, double appAmount, double variance, String status) {}

    public record MigrationPreview(
            String importId,
            double baselineTotal,
            double appTotal,
            double variance,
            int matchedCount,
            int mismatchedCount,
            List<MigrationRow> rows
    ) {}

    public record ReconcileRequest(String importId, List<String> acceptedRowIds) {}

    public record ReconcileResult(String importId, int acceptedCount, int mismatchCount, String notes) {}

    public record MigrationSheetReport(
            String sheet,
            int totalRows,
            int mismatchRows,
            int unresolvedRows,
            double baselineTotal,
            double appTotal,
            double varianceTotal
    ) {}

    public record MigrationExecutionReport(
            String importId,
            int totalRows,
            int mismatchedRows,
            int unresolvedRows,
            List<MigrationSheetReport> sheets
    ) {}

    private record ReconcileAudit(String importId, List<String> acceptedRowIds, ReconcileResult result) {}

    private final PlannerService plannerService;
    private final MigrationArtifactService migrationArtifactService;
    private final Map<String, MigrationPreview> previews = new ConcurrentHashMap<>();
    private final Map<String, List<String>> acceptedRowsByImportId = new ConcurrentHashMap<>();

    public SpreadsheetMigrationService(PlannerService plannerService, MigrationArtifactService migrationArtifactService) {
        this.plannerService = plannerService;
        this.migrationArtifactService = migrationArtifactService;
    }

    public MigrationPreview previewBaselineCsv(String csvContent) {
        var importId = UUID.randomUUID().toString();
        var appValueByMetric = createAppValueByMetric();
        var rows = new ArrayList<MigrationRow>();

        if (csvContent == null || csvContent.isBlank()) {
            var emptyPreview = new MigrationPreview(importId, 0.0, 0.0, 0.0, 0, 0, List.of());
            previews.put(importId, emptyPreview);
            migrationArtifactService.recordArtifact(importId, "BASELINE_PREVIEW", emptyPreview);
            return emptyPreview;
        }

        var lines = csvContent.split("\\r?\\n");
        for (int index = 1; index < lines.length; index++) {
            var line = lines[index].trim();
            if (line.isBlank()) {
                continue;
            }

            var parts = line.split(",");
            if (parts.length < 3) {
                continue;
            }

            var sheet = parts[0].trim();
            var metric = parts[1].trim();
            var baselineAmount = parseAmount(parts[2].trim());
            var appAmount = appValueByMetric.getOrDefault(metric.toLowerCase(), 0.0);
            var variance = round(baselineAmount - appAmount);
            var status = Math.abs(variance) < 0.01 ? "Matched" : "Mismatch";
            var rowId = "R" + (rows.size() + 1);

            rows.add(new MigrationRow(
                    rowId,
                    sheet,
                    metric,
                    round(baselineAmount),
                    round(appAmount),
                    variance,
                    status
            ));
        }

        var baselineTotal = round(rows.stream().mapToDouble(MigrationRow::baselineAmount).sum());
        var appTotal = round(rows.stream().mapToDouble(MigrationRow::appAmount).sum());
        var overallVariance = round(baselineTotal - appTotal);
        var matchedCount = (int) rows.stream().filter(row -> "Matched".equals(row.status())).count();
        var mismatchedCount = rows.size() - matchedCount;

        var preview = new MigrationPreview(
                importId,
                baselineTotal,
                appTotal,
                overallVariance,
                matchedCount,
                mismatchedCount,
                List.copyOf(rows)
        );

        previews.put(importId, preview);
        migrationArtifactService.recordArtifact(importId, "BASELINE_PREVIEW", preview);
        return preview;
    }

    public ReconcileResult reconcile(ReconcileRequest request) {
        var importId = request == null || request.importId() == null ? "unknown" : request.importId();
        var preview = previews.get(importId);
        if (preview == null && request != null && request.importId() != null) {
            preview = migrationArtifactService.getLatestPayload(importId, "BASELINE_PREVIEW", MigrationPreview.class);
        }

        var acceptedRowIds = request == null || request.acceptedRowIds() == null ? List.<String>of() : List.copyOf(request.acceptedRowIds());

        var mismatchCount = 0;
        if (preview != null) {
            mismatchCount = (int) preview.rows().stream()
                    .filter(row -> acceptedRowIds.contains(row.rowId()))
                    .filter(row -> "Mismatch".equals(row.status()))
                    .count();
        }

        var result = new ReconcileResult(
                importId,
                acceptedRowIds.size(),
                mismatchCount,
                "Reconciliation recorded. Review mismatches before final cutover."
        );

        acceptedRowsByImportId.put(importId, acceptedRowIds);
        migrationArtifactService.recordArtifact(importId, "BASELINE_RECONCILE", new ReconcileAudit(importId, acceptedRowIds, result));
        return result;
    }

    public MigrationExecutionReport executionReport(String importId) {
        var preview = previews.get(importId);
        if (preview == null) {
            preview = migrationArtifactService.getLatestPayload(importId, "BASELINE_PREVIEW", MigrationPreview.class);
        }
        if (preview == null) {
            throw new IllegalArgumentException("No migration preview found for importId: " + importId);
        }

        var acceptedRows = acceptedRowsByImportId.get(importId);
        if (acceptedRows == null) {
            var reconcileAudit = migrationArtifactService.getLatestPayload(importId, "BASELINE_RECONCILE", ReconcileAudit.class);
            acceptedRows = reconcileAudit == null || reconcileAudit.acceptedRowIds() == null
                    ? List.of()
                    : reconcileAudit.acceptedRowIds();
        }
        var acceptedRowsFinal = acceptedRows;

        var rowsBySheet = new HashMap<String, List<MigrationRow>>();
        for (var row : preview.rows()) {
            rowsBySheet.computeIfAbsent(row.sheet(), ignored -> new ArrayList<>()).add(row);
        }

        var sheetReports = new ArrayList<MigrationSheetReport>();
        var mismatchedRows = 0;
        var unresolvedRows = 0;

        for (var entry : rowsBySheet.entrySet()) {
            var sheetRows = entry.getValue();
            var sheetMismatchRows = (int) sheetRows.stream().filter(row -> "Mismatch".equals(row.status())).count();
            var sheetUnresolvedRows = (int) sheetRows.stream()
                    .filter(row -> "Mismatch".equals(row.status()))
                    .filter(row -> !acceptedRowsFinal.contains(row.rowId()))
                    .count();

            mismatchedRows += sheetMismatchRows;
            unresolvedRows += sheetUnresolvedRows;

            var baselineTotal = round(sheetRows.stream().mapToDouble(MigrationRow::baselineAmount).sum());
            var appTotal = round(sheetRows.stream().mapToDouble(MigrationRow::appAmount).sum());
            var varianceTotal = round(sheetRows.stream().mapToDouble(MigrationRow::variance).sum());

            sheetReports.add(new MigrationSheetReport(
                    entry.getKey(),
                    sheetRows.size(),
                    sheetMismatchRows,
                    sheetUnresolvedRows,
                    baselineTotal,
                    appTotal,
                    varianceTotal
            ));
        }

        sheetReports.sort((left, right) -> left.sheet().compareToIgnoreCase(right.sheet()));

        var report = new MigrationExecutionReport(
                importId,
                preview.rows().size(),
                mismatchedRows,
                unresolvedRows,
                List.copyOf(sheetReports)
        );

        migrationArtifactService.recordArtifact(importId, "MIGRATION_REPORT", report);
        return report;
    }

    public List<MigrationArtifactService.ArtifactHistoryEntry> history(String importId) {
        return migrationArtifactService.getHistory(importId);
    }

    private Map<String, Double> createAppValueByMetric() {
        var plannerData = plannerService.getPlannerData();
        var values = new HashMap<String, Double>();

        for (var account : plannerData.accounts()) {
            values.put(account.name().toLowerCase(), account.balance());
        }

        for (var commitment : plannerData.commitments()) {
            values.put(commitment.title().toLowerCase(), commitment.amount());
        }

        values.put("total commitments", plannerData.commitments().stream().mapToDouble(PlannerService.Commitment::amount).sum());
        values.put("net position", plannerData.accounts().stream().mapToDouble(PlannerService.Account::balance).sum());
        return values;
    }

    private double parseAmount(String rawAmount) {
        try {
            return Double.parseDouble(rawAmount.replace("$", "").replace(",", ""));
        } catch (NumberFormatException ignored) {
            return 0.0;
        }
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
