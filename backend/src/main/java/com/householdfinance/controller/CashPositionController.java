package com.householdfinance.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.householdfinance.service.CashPositionService;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cash-position")
public class CashPositionController {
    private final CashPositionService service;

    public CashPositionController(CashPositionService service) {
        this.service = service;
    }

    @GetMapping("/latest")
    public ResponseEntity<CashPositionService.Snapshot> latest() {
        return service.latest().map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/snapshots")
    public ResponseEntity<CashPositionService.Snapshot> save(@RequestBody JsonNode request) {
        if (request == null || !request.isObject())
            throw new IllegalArgumentException("A cash position object is required");
        LocalDate date;
        try {
            date = LocalDate.parse(requiredString(request, "asOfDate"));
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("asOfDate must be a valid ISO date (YYYY-MM-DD)");
        }
        var input = new CashPositionService.Input(date, requiredString(request, "sourceNote"),
                requiredString(request, "verification"), lines(request, "cashBalances"),
                lines(request, "upcomingCommitments"), minor(request, "minimumReserveMinor"));
        return ResponseEntity.status(HttpStatus.CREATED).body(service.save(input));
    }

    private static String requiredString(JsonNode parent, String field) {
        JsonNode node = parent.get(field);
        if (node == null || !node.isTextual())
            throw new IllegalArgumentException(field + " must be a string");
        return node.textValue();
    }

    private static long minor(JsonNode parent, String field) {
        JsonNode node = parent.get(field);
        if (node == null || !node.isIntegralNumber() || !node.canConvertToLong())
            throw new IllegalArgumentException(field + " must be an integer amount in cents");
        return node.longValue();
    }

    private static List<CashPositionService.Line> lines(JsonNode parent, String field) {
        JsonNode node = parent.get(field);
        if (node == null || !node.isArray() || node.size() > 30)
            throw new IllegalArgumentException(field + " must be an array of at most 30 entries");
        List<CashPositionService.Line> result = new ArrayList<>();
        for (JsonNode entry : node) {
            if (!entry.isObject()) throw new IllegalArgumentException(field + " entries must be objects");
            result.add(new CashPositionService.Line(
                    requiredString(entry, "label"), minor(entry, "amountMinor")));
        }
        return result;
    }

    @ExceptionHandler({IllegalArgumentException.class, ArithmeticException.class})
    public ResponseEntity<String> invalidInput(RuntimeException error) {
        return ResponseEntity.badRequest().body(error.getMessage());
    }
}
