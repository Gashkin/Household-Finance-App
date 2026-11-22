package com.householdfinance.mcp;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.*;
import com.householdfinance.access.*;
import com.householdfinance.workspace.WorkspaceFilter;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;

/** Real HTTP protocol boundary; OAuth network and financial services are explicit test doubles. */
class McpServerTest {
  ObjectMapper json = new ObjectMapper().findAndRegisterModules();
  McpServer server;
  McpAccess access;
  FinancialMonthService snapshots;
  String base;
  HttpClient http = HttpClient.newHttpClient();
  McpAccess.Caller caller;

  @BeforeEach
  void start() throws Exception {
    var actor =
        new AccessContext.Actor("owner", "owner", Set.of("READER", "EDITOR", "ADMIN"), 0, true);
    AccessContext.enter(actor);
    var users = mock(AccessService.class);
    when(users.active("owner", 0)).thenReturn(actor);
    access =
        spy(
            new McpAccess(
                users,
                json,
                "https://issuer.example/introspect",
                "https://issuer.example",
                "https://finance.example/mcp",
                "client",
                "synthetic-secret-12345",
                "subject",
                "owner"));
    caller =
        new McpAccess.Caller(
            McpAccess.digest("synthetic-token"),
            "caller123",
            new AccessContext.Actor("owner", "MCP", Set.of("READER"), 0, false));
    doReturn(caller).when(access).authenticate("Bearer synthetic-token");
    access.enable(true);
    snapshots = mock(FinancialMonthService.class);
    when(snapshots.current())
        .thenReturn(
            new FinancialMonthService.Snapshot(
                "1.0",
                "PERIOD_UNAVAILABLE",
                Instant.now(),
                null,
                List.of(),
                List.of(),
                List.of(),
                new FinancialMonthService.Coverage(false, 0, 0, 0, List.of(), List.of()),
                List.of()));
    int port;
    try (var socket = new ServerSocket(0)) {
      port = socket.getLocalPort();
    }
    base = "http://127.0.0.1:" + port;
    server =
        new McpServer(
            access,
            snapshots,
            new WorkspaceFilter("synthetic-local-workspace-password", "http://127.0.0.1"),
            json,
            port);
    server.start();
  }

  @AfterEach
  void stop() {
    server.stop();
    AccessContext.clear();
  }

  String call(String args) {
    return "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"get_current_financial_month\",\"arguments\":"
        + args
        + "}}";
  }

  HttpResponse<String> send(String path, String body, boolean auth, String... headers)
      throws Exception {
    var b =
        HttpRequest.newBuilder(URI.create(base + path))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/json");
    if (auth) b.header("Authorization", "Bearer synthetic-token");
    for (int i = 0; i < headers.length; i += 2) b.header(headers[i], headers[i + 1]);
    return http.send(
        b.POST(HttpRequest.BodyPublishers.ofString(body)).build(),
        HttpResponse.BodyHandlers.ofString());
  }

  @Test
  void financialReadNeedsSeparateOwnerApprovalEveryTimeIncludingRepeatCallsAndDeniedPrompts()
      throws Exception {
    var first = send("/mcp", call("{}"), true);
    assertEquals(200, first.statusCode());
    var id = json.readTree(first.body()).at("/result/structuredContent/approvalId").asText();
    assertFalse(id.isBlank());
    verifyNoInteractions(snapshots);
    var pending = send("/mcp", call("{\"approvalId\":\"" + id + "\"}"), true);
    assertTrue(pending.body().contains("APPROVAL_REQUIRED"));
    verifyNoInteractions(snapshots);
    access.decide(id, true);
    assertTrue(
        send("/mcp", call("{\"approvalId\":\"" + id + "\"}"), true)
            .body()
            .contains("PERIOD_UNAVAILABLE"));
    verify(snapshots, times(1)).current();
    assertTrue(
        send("/mcp", call("{\"approvalId\":\"" + id + "\"}"), true)
            .body()
            .contains("APPROVAL_REQUIRED"));
    var other =
        json.readTree(send("/mcp", call("{}"), true).body())
            .at("/result/structuredContent/approvalId")
            .asText();
    access.decide(other, false);
    assertTrue(
        send("/mcp", call("{\"approvalId\":\"" + other + "\"}"), true)
            .body()
            .contains("APPROVAL_REQUIRED"));
    verify(snapshots, times(1)).current();
  }

  @Test
  void anonymousDisabledMalformedOversizedCrossOriginAndAlternateToolsNeverReachData()
      throws Exception {
    assertEquals(401, send("/mcp", call("{}"), false).statusCode());
    for (String path :
        List.of("/api/ledger", "/api/mcp/enable", "/mcp/../api/ledger", "/mcp%2fapi"))
      assertEquals(404, send(path, call("{}"), true).statusCode());
    assertEquals(
        403, send("/mcp", call("{}"), true, "Origin", "https://attacker.example").statusCode());
    assertEquals(
        403, send("/mcp", call("{}"), true, "Cookie", "workspace_session=fake").statusCode());
    assertEquals(413, send("/mcp", " ".repeat(4097), true).statusCode());
    assertEquals(400, send("/mcp", "[]", true).statusCode());
    assertEquals(400, send("/mcp", call("{\"household\":\"other\"}"), true).statusCode());
    assertEquals(
        400, send("/mcp", call("{\"url\":\"https://attacker.example\"}"), true).statusCode());
    assertEquals(
        400,
        send("/mcp", call("{}").replace("get_current_financial_month", "update_balance"), true)
            .statusCode());
    access.enable(false);
    assertTrue(send("/mcp", call("{}"), true).body().contains("DISABLED"));
    verifyNoInteractions(snapshots);
  }

  @Test
  void discoveryHasOnlyOneBoundedReadToolAndNoFinancialDataOrSecret() throws Exception {
    var r = send("/mcp", "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}", false);
    var t = json.readTree(r.body()).at("/result/tools");
    assertEquals(1, t.size());
    assertTrue(t.get(0).at("/annotations/readOnlyHint").booleanValue());
    assertFalse(t.get(0).at("/inputSchema/additionalProperties").booleanValue());
    assertFalse(r.body().contains("synthetic-secret"));
    verifyNoInteractions(snapshots);
  }

  @Test
  void occupiedConnectorPortDisablesOnlyTheConnector() throws Exception {
    var duplicate =
        new McpServer(
            access,
            snapshots,
            new WorkspaceFilter("synthetic-local-workspace-password", "http://127.0.0.1"),
            json,
            URI.create(base).getPort());
    assertDoesNotThrow(duplicate::start);
    assertFalse(access.enabled());
    assertFalse(access.configured());
    verifyNoInteractions(snapshots);
    duplicate.stop();
  }

  @Test
  void failureConsumesApprovalRedactsInternalErrorsAndDoesNotPermitAutomaticReplay()
      throws Exception {
    var id =
        json.readTree(send("/mcp", call("{}"), true).body())
            .at("/result/structuredContent/approvalId")
            .asText();
    access.decide(id, true);
    when(snapshots.current()).thenThrow(new IllegalArgumentException("PRIVATE-ACCOUNT-NUMBER-123"));
    var failed = send("/mcp", call("{\"approvalId\":\"" + id + "\"}"), true);
    assertTrue(failed.body().contains("SNAPSHOT_UNAVAILABLE"));
    assertFalse(failed.body().contains("PRIVATE-ACCOUNT"));
    assertTrue(
        send("/mcp", call("{\"approvalId\":\"" + id + "\"}"), true)
            .body()
            .contains("APPROVAL_REQUIRED"));
    verify(snapshots, times(1)).current();
  }
}
