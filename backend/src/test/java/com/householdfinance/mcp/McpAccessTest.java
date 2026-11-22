package com.householdfinance.mcp;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.householdfinance.access.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;

class McpAccessTest {
  final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
  final AccessService users = mock(AccessService.class);
  McpAccess access;
  AccessContext.Actor owner =
      new AccessContext.Actor("owner", "owner", Set.of("EDITOR", "ADMIN", "READER"), 7, true);

  @BeforeEach
  void setup() {
    AccessContext.enter(owner);
    when(users.user("owner"))
        .thenReturn(
            new AccessService.User("owner", "owner", owner.roles(), "ACTIVE", 7, true, true));
    when(users.active("owner", 7)).thenReturn(owner);
    access =
        new McpAccess(
            users,
            json,
            "https://issuer.example/introspect",
            "https://issuer.example",
            "https://finance.example/mcp",
            "finance-client",
            "synthetic-secret-12345",
            "synthetic-subject",
            "owner");
    access.enable(true);
  }

  @AfterEach
  void cleanup() {
    AccessContext.clear();
  }

  ObjectNode token() {
    var t = json.createObjectNode();
    t.put("active", true);
    t.put("token_type", "Bearer");
    t.put("sub", "synthetic-subject");
    t.put("iss", "https://issuer.example");
    t.put("aud", "https://finance.example/mcp");
    t.put("scope", McpAccess.SCOPE);
    t.put("exp", Instant.now().plusSeconds(300).getEpochSecond());
    return t;
  }

  @Test
  void shortLivedScopedTokenStripsAllWriteAndAdminCapabilities() {
    var c = access.validate(token(), "synthetic-token");
    assertEquals(Set.of("READER"), c.actor().roles());
    assertFalse(c.actor().custodian());
    assertNotEquals("synthetic-token", c.binding());
  }

  @Test
  void expiredRevokedWrongHouseholdAudienceIssuerAndEscalatedScopesFailClosed() {
    for (String field : List.of("active", "sub", "iss", "aud", "scope", "exp", "nbf")) {
      var t = token();
      switch (field) {
        case "active" -> t.put(field, false);
        case "scope" -> t.put(field, McpAccess.SCOPE + " finance.write");
        case "exp" -> t.put(field, Instant.now().minusSeconds(1).getEpochSecond());
        case "nbf" -> t.put(field, Instant.now().plusSeconds(60).getEpochSecond());
        default -> t.put(field, "wrong");
      }
      assertThrows(SecurityException.class, () -> access.validate(t, "synthetic-token"), field);
    }
    var far = token();
    far.put("exp", Instant.now().plusSeconds(601).getEpochSecond());
    assertThrows(SecurityException.class, () -> access.validate(far, "synthetic-token"));
    when(users.user("owner"))
        .thenReturn(
            new AccessService.User("owner", "owner", owner.roles(), "DISABLED", 8, true, true));
    assertThrows(SecurityException.class, () -> access.validate(token(), "synthetic-token"));
  }

  @Test
  void pendingAndDeniedApprovalsDoNotReadAndStolenTokenCannotRedeemAnotherTokensApproval() {
    var c = access.validate(token(), "original");
    var stolen = access.validate(token(), "other-token");
    var p = access.request(c);
    assertThrows(SecurityException.class, () -> access.consume(c, p.id()));
    access.decide(p.id(), true);
    assertThrows(SecurityException.class, () -> access.consume(stolen, p.id()));
    access.consume(c, p.id());
    assertThrows(SecurityException.class, () -> access.consume(c, p.id()));
    var denied = access.request(c);
    access.decide(denied.id(), false);
    assertThrows(SecurityException.class, () -> access.consume(c, denied.id()));
    assertThrows(IllegalArgumentException.class, () -> access.decide(denied.id(), true));
  }

  @Test
  void concurrentRedeemsDeliverAtMostOnceAndDisableSurvivesReconnectWithoutStandingPermission()
      throws Exception {
    var c = access.validate(token(), "original");
    var p = access.request(c);
    access.decide(p.id(), true);
    var pool = Executors.newFixedThreadPool(4);
    var result = new ArrayList<Future<Boolean>>();
    for (int i = 0; i < 8; i++)
      result.add(
          pool.submit(
              () -> {
                try {
                  access.consume(c, p.id());
                  return true;
                } catch (SecurityException e) {
                  return false;
                }
              }));
    int successes = 0;
    for (var f : result) if (f.get()) successes++;
    pool.shutdownNow();
    assertEquals(1, successes);
    var next = access.request(c);
    access.decide(next.id(), true);
    access.enable(false);
    assertThrows(SecurityException.class, () -> access.consume(c, next.id()));
    access.enable(true);
    assertThrows(SecurityException.class, () -> access.consume(c, next.id()));
  }

  @Test
  void changedLocalPermissionsInvalidateAnAlreadyApprovedRead() {
    var c = access.validate(token(), "original");
    var p = access.request(c);
    access.decide(p.id(), true);
    when(users.active("owner", 7)).thenReturn(null);
    assertThrows(SecurityException.class, () -> access.consume(c, p.id()));
  }

  @Test
  void approvalExpiryAndRestartNeverCarryForwardPermission() {
    var c = access.validate(token(), "original");
    var p = access.request(c);
    access.decide(p.id(), true);
    access.clock = Clock.offset(access.clock, Duration.ofSeconds(121));
    assertThrows(SecurityException.class, () -> access.consume(c, p.id()));
    assertTrue(access.status().approvals().isEmpty());
    var restarted =
        new McpAccess(
            users,
            json,
            "https://issuer.example/introspect",
            "https://issuer.example",
            "https://finance.example/mcp",
            "finance-client",
            "synthetic-secret-12345",
            "synthetic-subject",
            "owner");
    assertFalse(restarted.enabled());
    assertThrows(SecurityException.class, () -> restarted.consume(c, p.id()));
  }

  @Test
  void readerAndOtherAdminCannotGrantApprovalOrEnableAccess() {
    var c = access.validate(token(), "token");
    AccessContext.enter(c.actor());
    assertThrows(RuntimeException.class, () -> access.enable(true));
    AccessContext.enter(
        new AccessContext.Actor("other", "admin", Set.of("ADMIN", "READER"), 0, false));
    assertThrows(RuntimeException.class, () -> access.status());
  }

  @Test
  void capsRequestsAndApprovalQueueAndAuditNeverContainsTokensOrFinancialPayloads()
      throws Exception {
    var c = access.validate(token(), "PRIVATE-TOKEN");
    for (int i = 0; i < 20; i++) access.request(c);
    assertThrows(SecurityException.class, () -> access.request(c));
    for (int i = 0; i < 30; i++) assertTrue(access.admit());
    assertFalse(access.admit());
    access.audit(c.label(), "READ", "request-123");
    String encoded = json.writeValueAsString(access.status());
    assertFalse(encoded.contains("PRIVATE-TOKEN"));
    assertFalse(encoded.contains("synthetic-subject"));
    assertTrue(encoded.contains("request-123"));
  }

  @Test
  void introspectionBodyIsBoundedAndCancelsUpstream() {
    var b = new McpAccess.LimitedBody();
    var sub = mock(Flow.Subscription.class);
    b.onSubscribe(sub);
    b.onNext(List.of(ByteBuffer.wrap(new byte[32769])));
    assertTrue(b.result.isCompletedExceptionally());
    verify(sub).cancel();
  }

  @Test
  void everyUseIntrospectsAgainAndAnIssuerOutageOrRevocationNeverUsesCachedIdentity()
      throws Exception {
    var client = mock(HttpClient.class);
    org.springframework.test.util.ReflectionTestUtils.setField(access, "http", client);
    @SuppressWarnings("unchecked")
    HttpResponse<byte[]> response = mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(200);
    when(response.body()).thenReturn(json.writeValueAsBytes(token()));
    when(client.sendAsync(
            any(HttpRequest.class),
            org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any()))
        .thenReturn(CompletableFuture.completedFuture(response));
    assertNotNull(access.authenticate("Bearer synthetic-token"));
    var revoked = token();
    revoked.put("active", false);
    when(response.body()).thenReturn(json.writeValueAsBytes(revoked));
    assertThrows(SecurityException.class, () -> access.authenticate("Bearer synthetic-token"));
    when(client.sendAsync(
            any(HttpRequest.class),
            org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any()))
        .thenReturn(
            CompletableFuture.failedFuture(new java.io.IOException("PRIVATE-UPSTREAM-DETAIL")));
    var error =
        assertThrows(SecurityException.class, () -> access.authenticate("Bearer synthetic-token"));
    assertEquals("AUTH_UNAVAILABLE", error.getMessage());
    verify(client, times(3))
        .sendAsync(
            any(HttpRequest.class),
            org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any());
  }

  @Test
  void missingOrNonTlsIssuerConfigurationAndAnonymousCallsFailClosed() {
    assertThrows(SecurityException.class, () -> access.authenticate(null));
    var unavailable =
        new McpAccess(
            users,
            json,
            "http://local/introspect",
            "https://issuer.example",
            "https://finance.example/mcp",
            "id",
            "synthetic-secret-12345",
            "subject",
            "owner");
    assertFalse(unavailable.configured());
    assertThrows(IllegalArgumentException.class, () -> unavailable.enable(true));
  }
}
