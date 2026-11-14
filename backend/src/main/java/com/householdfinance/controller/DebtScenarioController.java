package com.householdfinance.controller;

import com.householdfinance.service.DebtScenarioService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/debt-scenarios")
public class DebtScenarioController {
    private final DebtScenarioService service;
    public DebtScenarioController(DebtScenarioService service) { this.service = service; }

    @PostMapping("/compare")
    public DebtScenarioService.Result compare(@RequestBody DebtScenarioService.Input input) {
        return service.compare(input);
    }

    @ExceptionHandler({IllegalArgumentException.class, ArithmeticException.class})
    public ResponseEntity<String> invalidInput(RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(exception.getMessage());
    }
}
