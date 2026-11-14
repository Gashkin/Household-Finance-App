package com.householdfinance.ledger;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.cfg.*;
import com.fasterxml.jackson.databind.type.LogicalType;
import jakarta.servlet.http.HttpServletRequest;
import java.io.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ledger")
public class LedgerController {
  private final LedgerService service;
  private final ObjectMapper json;

  public LedgerController(LedgerService service, ObjectMapper mapper) {
    this.service = service;
    json =
        mapper
            .copy()
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .enable(
                DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);
    json.coercionConfigFor(LogicalType.Integer)
        .setCoercion(CoercionInputShape.String, CoercionAction.Fail);
    json.coercionConfigFor(LogicalType.Boolean)
        .setCoercion(CoercionInputShape.String, CoercionAction.Fail)
        .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail);
    json.coercionConfigFor(LogicalType.Textual)
        .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
        .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
        .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
  }

  private <T> T body(HttpServletRequest request, Class<T> type) throws IOException {
    int limit = 1024 * 1024;
    if (request.getContentLengthLong() > limit)
      throw new IllegalArgumentException("Ledger request too large");
    byte[] bytes = request.getInputStream().readNBytes(limit + 1);
    if (bytes.length > limit) throw new IllegalArgumentException("Ledger request too large");
    try {
      T value = json.readValue(bytes, type);
      if (value == null) throw new IllegalArgumentException("Complete ledger request required");
      return value;
    } catch (IOException e) {
      throw new IllegalArgumentException("Invalid ledger fields or types");
    } finally {
      Arrays.fill(bytes, (byte) 0);
    }
  }

  @GetMapping
  public LedgerService.Position position(@RequestParam("asOf") String date) {
    return service.position(LocalDate.parse(date));
  }

  @PostMapping("/accounts")
  public LedgerService.AccountView create(HttpServletRequest r) throws IOException {
    return service.create(body(r, LedgerService.AccountInput.class));
  }

  @GetMapping("/accounts/{id}/activity")
  public List<LedgerEntry> activity(@PathVariable("id") String id) {
    return service.activity(id);
  }

  @GetMapping("/accounts/{id}/history")
  public List<LedgerAudit> history(@PathVariable("id") String id) {
    return service.history(id);
  }

  @GetMapping("/history")
  public List<LedgerAudit> publications() {
    return service.history("HOUSEHOLD");
  }

  @PostMapping("/accounts/{id}/preview")
  public LedgerService.Preview preview(@PathVariable("id") String id, HttpServletRequest r)
      throws IOException {
    return service.preview(id, body(r, LedgerService.PreviewInput.class));
  }

  @PostMapping("/accounts/{id}/previews/{key}/approve")
  public LedgerService.Imported approve(
      @PathVariable("id") String id, @PathVariable("key") String key, HttpServletRequest r)
      throws IOException {
    return service.approve(id, key, body(r, LedgerService.Approval.class));
  }

  @PostMapping("/accounts/{id}/activity")
  public LedgerService.AccountView manual(@PathVariable("id") String id, HttpServletRequest r)
      throws IOException {
    return service.manual(id, body(r, LedgerService.Manual.class));
  }

  @PutMapping("/accounts/{id}/activity/{entry}")
  public LedgerService.AccountView correct(
      @PathVariable("id") String id, @PathVariable("entry") String entry, HttpServletRequest r)
      throws IOException {
    return service.correct(id, entry, body(r, LedgerService.Correction.class));
  }

  @PostMapping("/accounts/{id}/reconcile")
  public LedgerService.Reconciliation reconcile(
      @PathVariable("id") String id, @RequestParam("asOf") String asOf, HttpServletRequest r)
      throws IOException {
    return service.reconcile(id, LocalDate.parse(asOf), body(r, LedgerService.Reconcile.class));
  }

  @PostMapping("/publish")
  public com.householdfinance.service.CashPositionService.Snapshot publish(HttpServletRequest r)
      throws IOException {
    return service.publish(body(r, LedgerService.Publish.class));
  }

  @ExceptionHandler({
    IllegalArgumentException.class,
    ArithmeticException.class,
    java.time.DateTimeException.class
  })
  public ResponseEntity<Map<String, String>> invalid(Exception e) {
    return ResponseEntity.badRequest()
        .body(
            Map.of(
                "error",
                e instanceof java.time.DateTimeException
                    ? "Invalid ledger date; use YYYY-MM-DD"
                    : Objects.toString(e.getMessage(), "Invalid ledger request")));
  }
}
