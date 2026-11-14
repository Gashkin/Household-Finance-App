package com.householdfinance.controller;

import com.householdfinance.service.MigrationArtifactService;
import com.householdfinance.service.SpreadsheetMigrationService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class SpreadsheetMigrationController {

    private final SpreadsheetMigrationService spreadsheetMigrationService;

    public SpreadsheetMigrationController(SpreadsheetMigrationService spreadsheetMigrationService) {
        this.spreadsheetMigrationService = spreadsheetMigrationService;
    }

    @PostMapping(path = "/migration/preview", consumes = "text/csv", produces = MediaType.APPLICATION_JSON_VALUE)
    public SpreadsheetMigrationService.MigrationPreview previewMigration(@RequestBody String csvContent) {
        return spreadsheetMigrationService.previewBaselineCsv(csvContent);
    }

    @PostMapping(path = "/migration/reconcile", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public SpreadsheetMigrationService.ReconcileResult reconcile(
            @RequestBody SpreadsheetMigrationService.ReconcileRequest request) {
        return spreadsheetMigrationService.reconcile(request);
    }

    @GetMapping(path = "/migration/report/{importId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public SpreadsheetMigrationService.MigrationExecutionReport executionReport(@PathVariable("importId") String importId) {
        return spreadsheetMigrationService.executionReport(importId);
    }

    @GetMapping(path = "/migration/history/{importId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<MigrationArtifactService.ArtifactHistoryEntry> history(@PathVariable("importId") String importId) {
        return spreadsheetMigrationService.history(importId);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleBadRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(exception.getMessage());
    }
}
