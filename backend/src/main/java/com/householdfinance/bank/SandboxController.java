package com.householdfinance.bank;

import com.fasterxml.jackson.databind.*;
import com.householdfinance.workspace.WorkspaceFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.io.*;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/bank-sandbox", "/api/banking"})
public class SandboxController {
  private final SandboxService service;
  private final WorkspaceFilter workspace;
  private final ObjectMapper json;

  public SandboxController(SandboxService service, WorkspaceFilter workspace, ObjectMapper mapper) {
    this.service = service;
    this.workspace = workspace;
    this.json =
        mapper
            .copy()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
    json.coercionConfigFor(com.fasterxml.jackson.databind.type.LogicalType.Boolean)
        .setCoercion(
            com.fasterxml.jackson.databind.cfg.CoercionInputShape.String,
            com.fasterxml.jackson.databind.cfg.CoercionAction.Fail);
  }

  @ModelAttribute
  public void environmentGuard(HttpServletRequest request) {
    if (request.getRequestURI().startsWith("/api/bank-sandbox") && service.status().environment().equals("PRODUCTION"))
      throw new IllegalArgumentException("Legacy sandbox routes are disabled in production");
  }

  private <T> T body(HttpServletRequest request, Class<T> type) throws IOException {
    byte[] bytes = request.getInputStream().readNBytes(8193);
    if (bytes.length > 8192) throw new IllegalArgumentException("Sandbox request too large");
    try {
      T value = json.readValue(bytes, type);
      if (value == null) throw new IllegalArgumentException("Complete sandbox request required");
      return value;
    } catch (IOException e) {
      throw new IllegalArgumentException("Invalid sandbox request");
    } finally {
      Arrays.fill(bytes, (byte) 0);
    }
  }

  @GetMapping("/status")
  public SandboxService.Status status() {
    return service.status();
  }

  @PostMapping("/banks")
  public List<SandboxService.Bank> banks() {
    return service.banks();
  }

  @PostMapping("/start")
  public Map<String, String> start(HttpServletRequest request) throws IOException {
    return service.start(
        body(request, SandboxService.Start.class), workspace.sessionBinding(request));
  }

  @PostMapping("/complete")
  public SandboxService.Status complete(HttpServletRequest request) throws IOException {
    return service.complete(
        body(request, SandboxService.Complete.class), workspace.sessionBinding(request));
  }

  @PostMapping("/preview")
  public SandboxService.Fetched preview(HttpServletRequest request) throws IOException {
    return service.fetch(body(request, SandboxService.Fetch.class));
  }

  @PostMapping("/disconnect")
  public SandboxService.Status disconnect() {
    return service.disconnect();
  }

  record Forget(Boolean acknowledged) {}

  @PostMapping("/forget")
  public SandboxService.Status forget(HttpServletRequest request) throws IOException {
    if (!Boolean.TRUE.equals(body(request, Forget.class).acknowledged()))
      throw new IllegalArgumentException(
          "Acknowledge that local forgetting does not revoke provider consent");
    return service.forget();
  }

  @PostMapping("/external-revocation")
  public SandboxService.Status externalRevocation(HttpServletRequest request) throws IOException {
    return service.confirmExternalRevocation(body(request, Forget.class).acknowledged());
  }

  @ExceptionHandler({IllegalArgumentException.class, java.time.DateTimeException.class})
  public ResponseEntity<Map<String, String>> invalid(Exception e) {
    return ResponseEntity.badRequest()
        .body(
            Map.of(
                "error",
                e instanceof java.time.DateTimeException
                    ? "Invalid sandbox date"
                    : Objects.toString(e.getMessage(), "Sandbox request failed")));
  }
}
