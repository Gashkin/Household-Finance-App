package com.householdfinance.contracts;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/contracts")
class ContractsController {
  private final ContractsService contracts;
  private final ContractAlerts alerts;
  private final ObjectMapper json;

  ContractsController(ContractsService contracts, ContractAlerts alerts, ObjectMapper json) {
    this.contracts = contracts;
    this.alerts = alerts;
    this.json = json.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES,
            DeserializationFeature.FAIL_ON_TRAILING_TOKENS, DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS);
  }

  @GetMapping
  Object list() { return contracts.list(); }

  @GetMapping("/{id}")
  Object detail(@PathVariable String id) { return contracts.detail(id); }

  @GetMapping("/export")
  ResponseEntity<?> export() {
    return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
        .header("Content-Disposition", "attachment; filename=household-contracts.json")
        .body(contracts.exportVisible());
  }

  @GetMapping("/links")
  Object links() { return contracts.linkOptions(); }

  @GetMapping("/links/{accountId}/transactions")
  Object transactions(@PathVariable String accountId) { return contracts.transactions(accountId); }

  @GetMapping("/alerts")
  Object inbox() { return alerts.inbox(); }

  @PostMapping("/alerts/{id}/{termId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void alert(@PathVariable String id, @PathVariable String termId, HttpServletRequest request) throws IOException {
    alerts.change(id, termId, body(request, ContractAlerts.Change.class));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  Object create(HttpServletRequest request) throws IOException {
    return contracts.create(body(request, ContractsService.Create.class));
  }

  @PutMapping("/{id}/terms/{termId}")
  Object edit(@PathVariable String id, @PathVariable String termId, HttpServletRequest request) throws IOException {
    return contracts.edit(id, termId, body(request, ContractsService.Edit.class));
  }

  @PostMapping("/{id}/sharing")
  Object sharing(@PathVariable String id, HttpServletRequest request) throws IOException {
    return contracts.sharing(id, body(request, ContractsService.Sharing.class));
  }

  @PostMapping("/{id}/terms/{termId}/decision")
  Object decision(@PathVariable String id, @PathVariable String termId, HttpServletRequest request) throws IOException {
    return contracts.decide(id, termId, body(request, ContractsService.DecisionInput.class));
  }

  @PostMapping("/{id}/terms/{termId}/attempt")
  Object attempt(@PathVariable String id, @PathVariable String termId, HttpServletRequest request) throws IOException {
    return contracts.attempt(id, termId, body(request, ContractsService.AttemptInput.class));
  }

  @PostMapping("/{id}/terms/{termId}/confirmation")
  Object confirmation(@PathVariable String id, @PathVariable String termId, HttpServletRequest request) throws IOException {
    return contracts.confirm(id, termId, body(request, ContractsService.ConfirmInput.class));
  }

  @PostMapping("/{id}/reactivate")
  Object reactivate(@PathVariable String id, HttpServletRequest request) throws IOException {
    return contracts.reactivate(id, body(request, ContractsService.Reactivate.class));
  }

  private <T> T body(HttpServletRequest request, Class<T> type) throws IOException {
    byte[] data = request.getInputStream().readNBytes(65_537);
    try {
      if (data.length > 65_536) throw new IllegalArgumentException("Contract request is too large");
      var parsed = json.readValue(data, type);
      if (parsed == null) throw new IllegalArgumentException("Contract request fields are required");
      return parsed;
    } catch (IOException e) {
      throw new IllegalArgumentException("Invalid contract request fields or types");
    } finally {
      Arrays.fill(data, (byte) 0);
    }
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<Map<String, String>> invalid(IllegalArgumentException e) {
    return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
  }

  @ExceptionHandler(ResponseStatusException.class)
  ResponseEntity<Map<String, String>> status(ResponseStatusException e) {
    return ResponseEntity.status(e.getStatusCode()).body(Map.of("error", e.getReason()));
  }
}
