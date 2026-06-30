package com.householdfinance.controller;

import com.householdfinance.service.WorkbookIngestionService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@CrossOrigin(origins = "http://localhost:5173")
@RestController
public class WorkbookIngestionController {

    private final WorkbookIngestionService workbookIngestionService;

    public WorkbookIngestionController(WorkbookIngestionService workbookIngestionService) {
        this.workbookIngestionService = workbookIngestionService;
    }

    @PostMapping(path = "/migration/workbook/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public WorkbookIngestionService.WorkbookPreview previewWorkbook(@RequestParam("file") MultipartFile file) {
        return workbookIngestionService.previewWorkbook(file);
    }

    @PostMapping(path = "/migration/workbook/map", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public WorkbookIngestionService.WorkbookMappingResult mapWorkbook(@RequestParam("file") MultipartFile file) {
        return workbookIngestionService.mapWorkbook(file);
    }

    @PostMapping(path = "/migration/workbook/validate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public WorkbookIngestionService.WorkbookLoadValidationResult validateWorkbookLoad(
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "mode", defaultValue = "MERGE") String mode
    ) {
        return workbookIngestionService.validateWorkbookLoad(file, mode);
    }

    @PostMapping(path = "/migration/workbook/execute", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public WorkbookIngestionService.WorkbookLoadExecutionResult executeWorkbookLoad(
            @RequestBody WorkbookIngestionService.WorkbookLoadExecuteRequest request
    ) {
        return workbookIngestionService.executeValidatedLoad(request);
    }

    @GetMapping(path = "/migration/workbook/loads/latest", produces = MediaType.APPLICATION_JSON_VALUE)
    public WorkbookIngestionService.WorkbookLoadPostSummary latestLoadSummary() {
        return workbookIngestionService.getLatestLoadSummary();
    }

    @GetMapping(path = "/migration/workbook/loads/{executionId}/rejections", produces = MediaType.APPLICATION_JSON_VALUE)
    public java.util.List<WorkbookIngestionService.LoadRejectedRow> loadRejections(
            @PathVariable("executionId") String executionId
    ) {
        return workbookIngestionService.getRejectedRows(executionId);
    }

    @PostMapping(path = "/migration/workbook/loads/{executionId}/rollback", produces = MediaType.APPLICATION_JSON_VALUE)
    public WorkbookIngestionService.WorkbookLoadRollbackResult rollbackLoad(
            @PathVariable("executionId") String executionId
    ) {
        return workbookIngestionService.rollbackLoad(executionId);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleInvalidWorkbook(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(exception.getMessage());
    }
}
