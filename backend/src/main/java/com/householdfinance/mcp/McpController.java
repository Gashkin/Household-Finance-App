package com.householdfinance.mcp;

import java.util.Map;
import org.springframework.web.bind.annotation.*;

/** Local cookie-authenticated controls. Never registered on the MCP listener. */
@RestController
@RequestMapping("/api/mcp")
public class McpController {
  private final McpAccess access;

  public McpController(McpAccess access) {
    this.access = access;
  }

  @GetMapping
  public McpAccess.Status status() {
    return access.status();
  }

  @PostMapping("/enable")
  public McpAccess.Status enable() {
    return access.enable(true);
  }

  @PostMapping("/disable")
  public McpAccess.Status disable() {
    return access.enable(false);
  }

  @PostMapping("/approvals/{id}/approve")
  public Map<String, Boolean> approve(@PathVariable("id") String id) {
    access.decide(id, true);
    return Map.of("decided", true);
  }

  @PostMapping("/approvals/{id}/deny")
  public Map<String, Boolean> deny(@PathVariable("id") String id) {
    access.decide(id, false);
    return Map.of("decided", true);
  }
}
