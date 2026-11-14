package com.householdfinance.intake;

import com.fasterxml.jackson.databind.JsonNode;
import com.householdfinance.service.CashPositionService;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController @RequestMapping("/api/evidence-intake")
public class EvidenceIntakeController {
    private final IntakeAccessFilter access; private final EvidenceModelGateway model; private final EvidenceIntakeService service;
    public EvidenceIntakeController(IntakeAccessFilter access, EvidenceModelGateway model, EvidenceIntakeService service) { this.access = access; this.model = model; this.service = service; }
    @GetMapping("/capabilities") public Map<String, Object> capabilities() { return Map.of("enabled", access.enabled(), "modelConfigured", model.configured(), "maxCharacters", 4000, "draftLifetimeMinutes", 10, "modelDailyLimit", model.dailyLimit(), "maxOutputTokens", 1200); }
    @GetMapping("/session") public ResponseEntity<Void> unlock() { return ResponseEntity.noContent().build(); }
    @PostMapping("/drafts") public EvidenceIntakeService.View create(@RequestBody JsonNode body) {
        object(body); return service.create(text(body, "source"), text(body, "reference"), bool(body, "allowModel"));
    }
    @DeleteMapping("/drafts/{id}") public ResponseEntity<Void> discard(@PathVariable("id") String id) { service.discard(id); return ResponseEntity.noContent().build(); }
    @PostMapping("/drafts/{id}/approve") public CashPositionService.Snapshot approve(@PathVariable("id") String id, @RequestBody JsonNode body) {
        object(body); JsonNode entries = body.get("rows");
        if (entries == null || !entries.isArray() || entries.size() > 30) throw new IllegalArgumentException("Reviewed rows must be an array of at most 30 facts");
        var rows = new ArrayList<EvidenceIntakeService.Row>();
        for (var row : entries) {
            object(row); JsonNode line = row.get("lineNumber");
            if (line == null || !line.isIntegralNumber() || !line.canConvertToInt()) throw new IllegalArgumentException("Source line must be an integer");
            rows.add(new EvidenceIntakeService.Row(line.intValue(), text(row, "kind"), text(row, "label").strip(), EvidenceIntakeService.minor(text(row, "amountEuro"))));
        }
        return service.approve(id, new EvidenceIntakeService.Approval(LocalDate.parse(text(body, "asOfDate")),
            EvidenceIntakeService.minor(text(body, "minimumReserveEuro")), bool(body, "acknowledged"), List.copyOf(rows)));
    }
    private void object(JsonNode node) { if (node == null || !node.isObject()) throw new IllegalArgumentException("A complete intake object is required"); }
    private String text(JsonNode node, String key) { var value = node.get(key); if (value == null || !value.isTextual()) throw new IllegalArgumentException(key + " must be text"); return value.textValue(); }
    private boolean bool(JsonNode node, String key) { var value = node.get(key); if (value == null || !value.isBoolean()) throw new IllegalArgumentException(key + " must be explicitly true or false"); return value.booleanValue(); }
    @ExceptionHandler({IllegalArgumentException.class, ArithmeticException.class, java.time.format.DateTimeParseException.class})
    public ResponseEntity<String> invalid(RuntimeException error) { return ResponseEntity.badRequest().body("Invalid intake data: check source length, cited lines, dates, amounts and review acknowledgement"); }
    @ExceptionHandler(ResponseStatusException.class) public ResponseEntity<String> status(ResponseStatusException error) { return ResponseEntity.status(error.getStatusCode()).body(error.getReason()); }
}
