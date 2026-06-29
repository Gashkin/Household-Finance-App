package com.householdfinance.controller;

import com.householdfinance.service.TransactionImportService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin(origins = "http://localhost:5173")
@RestController
public class TransactionImportController {

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
}
