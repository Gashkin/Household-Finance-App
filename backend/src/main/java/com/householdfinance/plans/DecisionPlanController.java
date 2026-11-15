package com.householdfinance.plans;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.cfg.*;
import com.fasterxml.jackson.databind.type.LogicalType;
import jakarta.servlet.http.HttpServletRequest;
import java.io.*;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/plans")
public class DecisionPlanController {
  private final DecisionPlanService service;
  private final ObjectMapper json;

  public DecisionPlanController(DecisionPlanService service, ObjectMapper mapper) {
    this.service = service;
    json =
        mapper
            .copy()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
    for (var type : List.of(LogicalType.Integer, LogicalType.Boolean))
      json.coercionConfigFor(type).setCoercion(CoercionInputShape.String, CoercionAction.Fail);
    json.coercionConfigFor(LogicalType.Boolean)
        .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
        .setCoercion(CoercionInputShape.Float, CoercionAction.Fail);
    json.enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    json.coercionConfigFor(LogicalType.Textual)
        .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
        .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
        .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
  }

  private <T> T body(HttpServletRequest request, Class<T> type) throws IOException {
    byte[] bytes = request.getInputStream().readNBytes(262145);
    try {
      if (bytes.length > 262144) throw new IllegalArgumentException("Plan input too large");
      T value = json.readValue(bytes, type);
      if (value == null) throw new IllegalArgumentException("Complete plan input required");
      return value;
    } catch (IOException e) {
      throw new IllegalArgumentException(
          "Invalid plan input; use exact EUR text, whole revisions and explicit choices");
    } finally {
      Arrays.fill(bytes, (byte) 0);
    }
  }

  @GetMapping
  public List<DecisionPlanService.View> list() {
    return service.list();
  }

  @GetMapping("/{id}")
  public DecisionPlanService.View get(@PathVariable("id") String id) {
    return service.get(id);
  }

  @PostMapping
  public DecisionPlanService.View create(HttpServletRequest request) throws IOException {
    return service.create(body(request, DecisionPlanService.Content.class));
  }

  @PostMapping("/from-debt")
  public DecisionPlanService.View debt(HttpServletRequest request) throws IOException {
    return service.fromDebt(body(request, JsonNode.class));
  }

  @PutMapping("/{id}")
  public DecisionPlanService.View edit(@PathVariable("id") String id, HttpServletRequest request)
      throws IOException {
    return service.edit(id, body(request, DecisionPlanService.Edit.class));
  }

  @PostMapping("/{id}/decision")
  public DecisionPlanService.View choose(@PathVariable("id") String id, HttpServletRequest request)
      throws IOException {
    return service.choose(id, body(request, DecisionPlanService.Choose.class));
  }

  @PostMapping("/{id}/complete")
  public DecisionPlanService.View complete(
      @PathVariable("id") String id, HttpServletRequest request) throws IOException {
    return service.complete(id, body(request, DecisionPlanService.Complete.class));
  }

  @ExceptionHandler({IllegalArgumentException.class, java.time.DateTimeException.class})
  public ResponseEntity<Map<String, String>> invalid(Exception e) {
    return ResponseEntity.badRequest()
        .body(
            Map.of(
                "error",
                e instanceof java.time.DateTimeException
                    ? "Invalid plan date"
                    : Objects.toString(e.getMessage(), "Invalid plan input")));
  }

  @ExceptionHandler(org.springframework.dao.OptimisticLockingFailureException.class)
  public ResponseEntity<Map<String, String>> conflict() {
    return ResponseEntity.status(409)
        .body(Map.of("error", "Plan changed; reload before saving corrections"));
  }
}
