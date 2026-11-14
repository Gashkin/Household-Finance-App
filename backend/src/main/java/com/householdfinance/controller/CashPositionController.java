package com.householdfinance.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.householdfinance.service.CashPositionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cash-position")
public class CashPositionController {
    private final CashPositionService service;
    private final ObjectMapper mapper;
    public CashPositionController(CashPositionService service, ObjectMapper mapper) { this.service = service; this.mapper = mapper; }
    @PostMapping("/calculate")
    public CashPositionService.Result calculate(@RequestBody JsonNode json) {
        if (json == null || !json.isObject()) throw new IllegalArgumentException("A cash position object is required");
        validateAmounts(json.get("balances"), "balanceMinor");
        validateAmounts(json.get("commitments"), "amountMinor");
        try { return service.calculate(mapper.treeToValue(json, CashPositionService.Input.class)); }
        catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalArgumentException("Provide complete balances, commitments and ISO dates"); }
    }
    private void validateAmounts(JsonNode rows, String field) {
        if (rows == null || !rows.isArray()) throw new IllegalArgumentException("Balances and commitments must be arrays");
        for (var row : rows) {
            var value = row.get(field);
            if (value == null || !value.isIntegralNumber() || !value.canConvertToLong())
                throw new IllegalArgumentException(field + " must be an integer number of cents");
        }
    }
    @ExceptionHandler({IllegalArgumentException.class, ArithmeticException.class})
    public ResponseEntity<String> invalid(RuntimeException e) { return ResponseEntity.badRequest().body(e.getMessage()); }
}
