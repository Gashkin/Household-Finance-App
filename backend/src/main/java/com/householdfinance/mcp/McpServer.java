package com.householdfinance.mcp;

import com.fasterxml.jackson.databind.*;
import com.householdfinance.access.AccessContext;
import com.householdfinance.workspace.WorkspaceFilter;
import com.sun.net.httpserver.*;
import jakarta.annotation.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Stateless Streamable HTTP MCP, deliberately on a different loopback port from the app. */
@Component
public class McpServer {
  private final McpAccess access;
  private final FinancialMonthService snapshots;
  private final WorkspaceFilter workspace;
  private final ObjectMapper json;
  private final int port;
  private HttpServer server;
  private ExecutorService executor;

  public McpServer(
      McpAccess access,
      FinancialMonthService snapshots,
      WorkspaceFilter workspace,
      ObjectMapper json,
      @Value("${finance.mcp.port:8787}") int port) {
    this.access = access;
    this.snapshots = snapshots;
    this.workspace = workspace;
    this.json =
        json.copy()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    this.port = port;
  }

  @PostConstruct
  public void start() throws IOException {
    if (!access.configured()) return;
    try {
      // JDK HTTP server request/header/read deadlines bound slow clients independently of the
      // worker
      // pool.
      System.setProperty("sun.net.httpserver.maxReqTime", "10");
      System.setProperty("sun.net.httpserver.maxRspTime", "10");
      System.setProperty("sun.net.httpserver.maxIdleConnections", "10");
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 8);
      executor =
          new ThreadPoolExecutor(
              2,
              2,
              0,
              TimeUnit.SECONDS,
              new ArrayBlockingQueue<>(8),
              r -> {
                var t = new Thread(r, "finance-mcp");
                t.setDaemon(true);
                return t;
              },
              new ThreadPoolExecutor.AbortPolicy());
      server.setExecutor(executor);
      server.createContext("/", this::handle);
      server.start();
    } catch (IOException | IllegalArgumentException e) {
      stop();
      access.transportFailure();
      org.slf4j.LoggerFactory.getLogger(McpServer.class)
          .warn(
              "MCP listener unavailable; local Finance App remains available. Check the dedicated"
                  + " loopback port configuration.");
    }
  }

  @PreDestroy
  public void stop() {
    if (server != null) server.stop(0);
    if (executor != null) executor.shutdownNow();
  }

  private static Map<String, Object> failure(String code) {
    return Map.of(
        "isError",
        true,
        "content",
        List.of(Map.of("type", "text", "text", code)),
        "structuredContent",
        Map.of("error", code));
  }

  private void send(HttpExchange x, int status, Object body) throws IOException {
    byte[] bytes = json.writeValueAsBytes(body);
    if (bytes.length > 1_048_576) {
      status = 503;
      bytes = "{\"error\":\"RESPONSE_LIMIT\"}".getBytes(StandardCharsets.UTF_8);
    }
    x.getResponseHeaders().set("Content-Type", "application/json");
    x.getResponseHeaders().set("Cache-Control", "no-store");
    x.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
    x.sendResponseHeaders(status, bytes.length);
    x.getResponseBody().write(bytes);
  }

  private void reject(HttpExchange x, int status, String code, String requestId)
      throws IOException {
    access.audit("anonymous", code, requestId);
    send(x, status, Map.of("error", code));
  }

  private Map<String, Object> tool() {
    return Map.of(
        "name",
        "get_current_financial_month",
        "description",
        "Read the current saved financial period, minimal balances and dated cash-flow events. Each"
            + " read requires owner approval: call without approvalId to request local approval,"
            + " then ask the owner to approve that specific request in Finance App > ChatGPT"
            + " connection. Retry once with its approvalId. Never reuse approvals. No write"
            + " operations.",
        "inputSchema",
        Map.of(
            "type",
            "object",
            "properties",
            Map.of("approvalId", Map.of("type", "string", "format", "uuid")),
            "additionalProperties",
            false),
        "annotations",
        Map.of(
            "readOnlyHint",
            true,
            "destructiveHint",
            false,
            "idempotentHint",
            false,
            "openWorldHint",
            false),
        "securitySchemes",
        List.of(Map.of("type", "oauth2", "scopes", List.of(McpAccess.SCOPE))));
  }

  private boolean fields(JsonNode node, Set<String> allowed) {
    if (!node.isObject()) return false;
    var names = node.fieldNames();
    while (names.hasNext()) if (!allowed.contains(names.next())) return false;
    return true;
  }

  void handle(HttpExchange x) throws IOException {
    String requestId = UUID.randomUUID().toString(), caller = "anonymous";
    JsonNode id = null;
    try {
      if (!access.admit()) {
        reject(x, 429, "RATE_LIMIT", requestId);
        return;
      }
      String host = x.getRequestHeaders().getFirst("Host");
      String path = x.getRequestURI().getRawPath();
      if (!Set.of("127.0.0.1:" + port, "localhost:" + port).contains(Objects.toString(host, ""))
          || x.getRequestHeaders().containsKey("Origin")
          || x.getRequestHeaders().containsKey("Cookie")
          || x.getRequestURI().getRawQuery() != null) {
        reject(x, 403, "ORIGIN_OR_HOST_DENIED", requestId);
        return;
      }
      if (path.equals("/.well-known/oauth-protected-resource")
          && x.getRequestMethod().equals("GET")) {
        send(
            x,
            200,
            Map.of(
                "resource",
                access.audience(),
                "authorization_servers",
                List.of(access.issuer()),
                "scopes_supported",
                List.of(McpAccess.SCOPE),
                "bearer_methods_supported",
                List.of("header")));
        return;
      }
      if (!path.equals("/mcp")) {
        reject(x, 404, "NOT_FOUND", requestId);
        return;
      }
      if (!x.getRequestMethod().equals("POST")) {
        reject(x, 405, "METHOD_NOT_ALLOWED", requestId);
        return;
      }
      if (!Objects.toString(x.getRequestHeaders().getFirst("Content-Type"), "")
          .toLowerCase(Locale.ROOT)
          .startsWith("application/json")) {
        reject(x, 415, "JSON_REQUIRED", requestId);
        return;
      }
      byte[] bytes = x.getRequestBody().readNBytes(4097);
      if (bytes.length > 4096) {
        reject(x, 413, "REQUEST_LIMIT", requestId);
        return;
      }
      JsonNode r;
      try {
        r = json.readTree(bytes);
      } catch (Exception e) {
        reject(x, 400, "INVALID_JSON", requestId);
        return;
      }
      if (r == null
          || !fields(r, Set.of("jsonrpc", "id", "method", "params"))
          || !r.path("jsonrpc").asText().equals("2.0")
          || !r.path("method").isTextual()) {
        reject(x, 400, "INVALID_REQUEST", requestId);
        return;
      }
      id = r.get("id");
      if (id != null
          && (!id.isTextual() && !id.isIntegralNumber() || id.toString().length() > 100)) {
        reject(x, 400, "INVALID_ID", requestId);
        return;
      }
      String method = r.path("method").asText();
      Object result;
      // Discovery contains no user data. Every financial tool invocation authenticates online.
      if (method.equals("initialize"))
        result =
            Map.of(
                "protocolVersion",
                "2025-03-26",
                "capabilities",
                Map.of("tools", Map.of()),
                "serverInfo",
                Map.of("name", "household-finance-current-month", "version", "1.0.0"));
      else if (method.equals("notifications/initialized") && id == null) {
        x.sendResponseHeaders(202, -1);
        return;
      } else if (method.equals("tools/list")) result = Map.of("tools", List.of(tool()));
      else if (method.equals("ping")) result = Map.of();
      else if (method.equals("tools/call")) {
        var p = r.path("params");
        var args = p.has("arguments") ? p.get("arguments") : json.createObjectNode();
        if (id == null
            || !fields(p, Set.of("name", "arguments", "_meta"))
            || !p.path("name").asText().equals("get_current_financial_month")
            || !fields(args, Set.of("approvalId"))) {
          reject(x, 400, "TOOL_OR_ARGUMENT_DENIED", requestId);
          return;
        }
        if (args.has("approvalId")
            && (!args.path("approvalId").isTextual()
                || !args.path("approvalId")
                    .textValue()
                    .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))) {
          reject(x, 400, "INVALID_APPROVAL", requestId);
          return;
        }
        var identity = access.authenticate(x.getRequestHeaders().getFirst("Authorization"));
        caller = identity.label();
        if (!args.has("approvalId")) {
          var approval = access.request(identity);
          result =
              Map.of(
                  "isError",
                  true,
                  "content",
                  List.of(
                      Map.of(
                          "type",
                          "text",
                          "text",
                          "OWNER_APPROVAL_REQUIRED: Ask owner to approve request "
                              + approval.id()
                              + " in the local Finance App. Retry only that approved request.")),
                  "structuredContent",
                  Map.of(
                      "error",
                      "OWNER_APPROVAL_REQUIRED",
                      "approvalId",
                      approval.id(),
                      "expiresAt",
                      approval.expiresAt().toString()));
          access.audit(caller, "APPROVAL_REQUESTED", requestId);
        } else {
          result =
              workspace.readSnapshot(
                  () -> {
                    access.consume(identity, args.path("approvalId").textValue());
                    AccessContext.enter(identity.actor());
                    try {
                      var snapshot = snapshots.current();
                      return Map.of(
                          "content",
                          List.of(
                              Map.of(
                                  "type",
                                  "text",
                                  "text",
                                  "Current financial period snapshot; use structuredContent."
                                      + " Partial coverage and illustrative projections must remain"
                                      + " labelled.")),
                          "structuredContent",
                          snapshot,
                          "isError",
                          false);
                    } finally {
                      AccessContext.clear();
                    }
                  });
          access.audit(caller, "READ", requestId);
        }
      } else {
        reject(x, 400, "METHOD_DENIED", requestId);
        return;
      }
      if (id == null) {
        reject(x, 400, "ID_REQUIRED", requestId);
        return;
      }
      send(x, 200, Map.of("jsonrpc", "2.0", "id", id, "result", result));
    } catch (SecurityException e) {
      String code =
          Set.of(
                      "AUTH_REQUIRED",
                      "AUTH_UNAVAILABLE",
                      "TOKEN_DENIED",
                      "ACCESS_REVOKED",
                      "DISABLED",
                      "APPROVAL_LIMIT",
                      "APPROVAL_REQUIRED")
                  .contains(e.getMessage())
              ? e.getMessage()
              : "ACCESS_DENIED";
      access.audit(caller, code, requestId);
      if (code.startsWith("AUTH") || code.equals("TOKEN_DENIED")) {
        x.getResponseHeaders()
            .set(
                "WWW-Authenticate",
                "Bearer resource_metadata=\""
                    + access.audience().replaceFirst("/mcp$", "")
                    + "/.well-known/oauth-protected-resource\"");
        send(x, 401, Map.of("error", code));
      } else
        send(
            x,
            200,
            Map.of(
                "jsonrpc",
                "2.0",
                "id",
                id == null ? json.nullNode() : id,
                "result",
                failure(code)));
    } catch (Exception e) {
      String code =
          Set.of("ACCOUNT_LIMIT", "EVENT_LIMIT", "SOURCE_LIMIT", "PERIOD_LIMIT")
                  .contains(Objects.toString(e.getMessage(), ""))
              ? e.getMessage()
              : "SNAPSHOT_UNAVAILABLE";
      access.audit(caller, code, requestId);
      send(
          x,
          200,
          Map.of(
              "jsonrpc", "2.0", "id", id == null ? json.nullNode() : id, "result", failure(code)));
    } finally {
      x.close();
    }
  }
}
