package com.householdfinance.controller;

import com.householdfinance.service.TransactionImportService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class TransactionImportController {
    @org.springframework.web.bind.annotation.ExceptionHandler(IllegalArgumentException.class)
    public org.springframework.http.ResponseEntity<java.util.Map<String,String>> invalid(IllegalArgumentException error) {
        return org.springframework.http.ResponseEntity.badRequest().body(java.util.Map.of("error",error.getMessage()));
    }

    private final TransactionImportService transactionImportService;

    public TransactionImportController(TransactionImportService transactionImportService) {
        this.transactionImportService = transactionImportService;
    }

    @PostMapping(path = "/imports/preview", consumes = "text/csv", produces = MediaType.APPLICATION_JSON_VALUE)
    public TransactionImportService.ImportPreview previewImport(@RequestBody String csvContent) {
        return transactionImportService.previewCsv(csvContent);
    }

    @PostMapping(path = "/imports/accept", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public TransactionImportService.ImportAcceptResult acceptImport(
            @RequestBody TransactionImportService.ImportAcceptRequest request) {
        return transactionImportService.acceptImport(request);
    }

    @GetMapping(path = "/imports/history", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<TransactionImportService.TransactionHistoryItem> history() {
        return transactionImportService.getHistory();
    }

    @PostMapping(path = "/imports/map-to-future", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public org.springframework.http.ResponseEntity<java.util.Map<String,String>> mapToFuture() {
        return legacyMatchingUnavailable();
    }

    @PostMapping(path = "/imports/resolve-history", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public org.springframework.http.ResponseEntity<java.util.Map<String,String>> resolveHistory() {
        return legacyMatchingUnavailable();
    }

    private org.springframework.http.ResponseEntity<java.util.Map<String,String>> legacyMatchingUnavailable() {
        // Process-local history cannot authorise durable balance changes safely
        // across retries, cross-import duplicates or restart (audit F2).
        return org.springframework.http.ResponseEntity.status(org.springframework.http.HttpStatus.GONE)
            .body(java.util.Map.of("error", "Legacy financial matching is unavailable. Use Money position to review and approve activity."));
    }
}
