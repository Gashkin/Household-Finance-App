package com.householdfinance.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.householdfinance.service.DebtScenarioService;
import java.math.BigDecimal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/debt-scenarios")
public class DebtScenarioController {
    private final DebtScenarioService service;
    public DebtScenarioController(DebtScenarioService service) { this.service = service; }

    @PostMapping("/compare")
    public DebtScenarioService.Result compare(@RequestBody JsonNode request) {
        if (request == null || !request.isObject())
            throw new IllegalArgumentException("A complete scenario object is required");
        var input = new DebtScenarioService.Input(
            minor(request, "balanceMinor"), decimal(request, "annualRatePercent"),
            minor(request, "monthlyPaymentMinor"), minor(request, "extraPaymentMinor"),
            minor(request, "liquidCashMinor"), minor(request, "minimumReserveMinor"));
        return service.compare(input);
    }

    private static JsonNode required(JsonNode request, String field) {
        JsonNode node = request.get(field);
        if (node == null || node.isNull() || !node.isNumber())
            throw new IllegalArgumentException(field + " must be provided as a number");
        return node;
    }

    private static long minor(JsonNode request, String field) {
        JsonNode node = required(request, field);
        if (!node.isIntegralNumber() || !node.canConvertToLong())
            throw new IllegalArgumentException(field + " must be an integer number of minor currency units");
        return node.longValue();
    }

    private static BigDecimal decimal(JsonNode request, String field) {
        return required(request, field).decimalValue();
    }

    @ExceptionHandler({IllegalArgumentException.class, ArithmeticException.class})
    public ResponseEntity<String> invalidInput(RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(exception.getMessage());
    }
}
