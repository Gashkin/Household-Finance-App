package com.householdfinance.forecast;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.cfg.*;
import com.fasterxml.jackson.databind.type.LogicalType;
import jakarta.servlet.http.HttpServletRequest;
import java.io.*;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/forecast")
public class ForecastController {
  private final ForecastService service;
  private final ObjectMapper json;

  public ForecastController(ForecastService service, ObjectMapper mapper) {
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
      if (bytes.length > 262144) throw new IllegalArgumentException("Forecast input too large");
      T value = json.readValue(bytes, type);
      if (value == null) throw new IllegalArgumentException("Complete forecast input required");
      return value;
    } catch (IOException e) {
      throw new IllegalArgumentException(
          "Invalid forecast input; use exact EUR text, whole revisions and explicit choices");
    } finally {
      Arrays.fill(bytes, (byte) 0);
    }
  }

  @GetMapping
  public ForecastService.Workspace get() {
    return service.get();
  }

  @PostMapping
  public ForecastService.Workspace save(HttpServletRequest request) throws IOException {
    return service.save(body(request, ForecastService.Input.class));
  }

  @PostMapping("/compare")
  public ForecastService.Comparison compare(HttpServletRequest request) throws IOException {
    return service.compare(body(request, ForecastService.WhatIf.class));
  }

  @ExceptionHandler({IllegalArgumentException.class, java.time.DateTimeException.class})
  public ResponseEntity<Map<String, String>> invalid(Exception e) {
    return ResponseEntity.badRequest()
        .body(
            Map.of(
                "error",
                e instanceof java.time.DateTimeException
                    ? "Invalid forecast date"
                    : Objects.toString(e.getMessage(), "Invalid forecast input")));
  }

  @ExceptionHandler(org.springframework.dao.OptimisticLockingFailureException.class)
  public ResponseEntity<Map<String, String>> conflict() {
    return ResponseEntity.status(409)
        .body(Map.of("error", "Plan changed; reload before saving corrections"));
  }
}
