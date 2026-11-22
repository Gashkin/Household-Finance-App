package com.householdfinance.mcp;

import com.fasterxml.jackson.databind.*;
import com.householdfinance.access.*;
import java.io.ByteArrayOutputStream;
import java.net.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Online OAuth introspection plus a separate, one-use local approval per financial read. */
@Service
public class McpAccess {
  public static final String SCOPE = "finance.current-month.read";

  public record Caller(String binding, String label, AccessContext.Actor actor) {}

  public record Approval(String id, String caller, Instant expiresAt, String state) {}

  public record Audit(
      Instant timestamp,
      String caller,
      String scope,
      String tool,
      String outcome,
      String requestId) {}

  public record Status(
      boolean configured, boolean enabled, List<Approval> approvals, List<Audit> audit) {}

  private record Grant(String binding, long revision, Approval view) {}

  private final Map<String, Grant> grants = new LinkedHashMap<>();
  private final ArrayDeque<Audit> audit = new ArrayDeque<>();
  private final AccessService users;
  private final ObjectMapper json;
  private final HttpClient http;
  private final String endpoint, issuer, audience, clientId, secret, subject, userId;

  @org.springframework.beans.factory.annotation.Autowired(required = false)
  Clock clock = Clock.systemUTC();

  private boolean enabled;
  private volatile boolean transportFailed;
  private Instant window = Instant.EPOCH;
  private int requests;

  public McpAccess(
      AccessService users,
      ObjectMapper json,
      @Value("${finance.mcp.introspection:}") String endpoint,
      @Value("${finance.mcp.issuer:}") String issuer,
      @Value("${finance.mcp.audience:}") String audience,
      @Value("${finance.mcp.client-id:}") String clientId,
      @Value("${finance.mcp.client-secret:}") String secret,
      @Value("${finance.mcp.subject:}") String subject,
      @Value("${finance.mcp.user-id:owner}") String userId) {
    this.users = users;
    this.json = json;
    this.endpoint = endpoint;
    this.issuer = issuer;
    this.audience = audience;
    this.clientId = clientId;
    this.secret = secret;
    this.subject = subject;
    this.userId = userId;
    http =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
  }

  private boolean https(String value) {
    try {
      var u = URI.create(value);
      return u.getScheme().equals("https")
          && u.getHost() != null
          && u.getUserInfo() == null
          && u.getFragment() == null;
    } catch (RuntimeException e) {
      return false;
    }
  }

  public boolean configured() {
    return !transportFailed
        && https(endpoint)
        && https(issuer)
        && https(audience)
        && !clientId.isBlank()
        && !clientId.contains(":")
        && secret.length() >= 16
        && !subject.isBlank()
        && !userId.isBlank();
  }

  synchronized void transportFailure() {
    transportFailed = true;
    enabled = false;
    grants.clear();
  }

  private void owner() {
    AccessContext.requireCustodian();
  }

  private synchronized void prune() {
    grants.values().removeIf(g -> !clock.instant().isBefore(g.view().expiresAt()));
  }

  public synchronized Status status() {
    owner();
    prune();
    return new Status(
        configured(),
        enabled,
        grants.values().stream().map(Grant::view).toList(),
        List.copyOf(audit));
  }

  public synchronized Status enable(boolean value) {
    owner();
    if (value && !configured())
      throw new IllegalArgumentException("Configure the OAuth issuer before enabling MCP");
    enabled = value;
    grants.clear();
    return status();
  }

  public synchronized void decide(String id, boolean approve) {
    owner();
    prune();
    var g = grants.get(id);
    if (g == null || !g.view().state().equals("PENDING"))
      throw new IllegalArgumentException("Approval expired or already decided");
    grants.put(
        id,
        new Grant(
            g.binding(),
            g.revision(),
            new Approval(
                id, g.view().caller(), g.view().expiresAt(), approve ? "APPROVED" : "DENIED")));
  }

  /** Global bound applies before any network introspection, including unauthenticated abuse. */
  public synchronized boolean admit() {
    var now = clock.instant();
    if (!now.isBefore(window.plusSeconds(60))) {
      window = now;
      requests = 0;
    }
    return ++requests <= 30;
  }

  public synchronized boolean enabled() {
    return enabled && configured();
  }

  public static String digest(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  public Caller authenticate(String header) {
    if (!enabled() || header == null || !header.startsWith("Bearer ") || header.length() > 4096)
      throw new SecurityException("AUTH_REQUIRED");
    String token = header.substring(7);
    if (token.isBlank() || token.chars().anyMatch(Character::isWhitespace))
      throw new SecurityException("AUTH_REQUIRED");
    try {
      String basic =
          Base64.getEncoder()
              .encodeToString(
                  (URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                          + ":"
                          + URLEncoder.encode(secret, StandardCharsets.UTF_8))
                      .getBytes(StandardCharsets.US_ASCII));
      var request =
          HttpRequest.newBuilder(URI.create(endpoint))
              .timeout(Duration.ofSeconds(5))
              .header("Authorization", "Basic " + basic)
              .header("Content-Type", "application/x-www-form-urlencoded")
              .POST(
                  HttpRequest.BodyPublishers.ofString(
                      "token="
                          + URLEncoder.encode(token, StandardCharsets.UTF_8)
                          + "&token_type_hint=access_token"))
              .build();
      var pending = http.sendAsync(request, info -> new LimitedBody());
      HttpResponse<byte[]> response;
      try {
        response = pending.get(5, TimeUnit.SECONDS);
      } finally {
        pending.cancel(true);
      }
      if (response.statusCode() != 200) throw new SecurityException("AUTH_UNAVAILABLE");
      JsonNode data = json.readTree(response.body());
      return validate(data, token);
    } catch (SecurityException e) {
      throw e;
    } catch (Exception e) {
      if (e instanceof InterruptedException) Thread.currentThread().interrupt();
      throw new SecurityException("AUTH_UNAVAILABLE");
    }
  }

  /** Bound both the introspection body and the time to receive its final byte. */
  static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
    final CompletableFuture<byte[]> result = new CompletableFuture<>();
    final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    Flow.Subscription subscription;

    public CompletionStage<byte[]> getBody() {
      return result;
    }

    public void onSubscribe(Flow.Subscription s) {
      subscription = s;
      s.request(1);
    }

    public void onNext(List<ByteBuffer> chunks) {
      for (var b : chunks) {
        if (bytes.size() + b.remaining() > 32768) {
          subscription.cancel();
          result.completeExceptionally(new SecurityException("AUTH_UNAVAILABLE"));
          return;
        }
        byte[] chunk = new byte[b.remaining()];
        b.get(chunk);
        bytes.writeBytes(chunk);
      }
      subscription.request(1);
    }

    public void onError(Throwable t) {
      result.completeExceptionally(t);
    }

    public void onComplete() {
      result.complete(bytes.toByteArray());
    }
  }

  // Package-visible for token contract tests. No unsigned JWT decoding or cached token acceptance.
  Caller validate(JsonNode data, String token) {
    long now = clock.instant().getEpochSecond();
    var aud = data.path("aud");
    boolean audienceOk =
        aud.isTextual()
            ? aud.textValue().equals(audience)
            : aud.isArray()
                && java.util.stream.StreamSupport.stream(aud.spliterator(), false)
                    .anyMatch(n -> n.isTextual() && n.textValue().equals(audience));
    var scopes = new HashSet<>(Arrays.asList(data.path("scope").asText("").split(" ")));
    if (!data.path("active").isBoolean()
        || !data.path("active").booleanValue()
        || !data.path("exp").isIntegralNumber()
        || !data.path("exp").canConvertToLong()
        || !data.path("token_type").asText().equalsIgnoreCase("Bearer")
        || data.path("exp").asLong() <= now
        || data.path("exp").asLong() > now + 600
        || !data.path("iss").asText().equals(issuer)
        || !data.path("sub").asText().equals(subject)
        || !audienceOk
        || !scopes.contains(SCOPE)
        || !Set.of(SCOPE, "openid", "offline_access").containsAll(scopes)
        || data.has("nbf")
            && (!data.path("nbf").isIntegralNumber() || data.path("nbf").asLong() > now))
      throw new SecurityException("TOKEN_DENIED");
    var u = users.user(userId);
    if (u == null
        || !u.state().equals("ACTIVE")
        || !u.accepted()
        || !(u.roles().contains("READER") || u.roles().contains("EDITOR")))
      throw new SecurityException("ACCESS_REVOKED");
    // Deliberately strip editor/admin/custodian even when the local owner has them.
    return new Caller(
        digest(token),
        digest(issuer + "|" + subject).substring(0, 12),
        new AccessContext.Actor(u.id(), "MCP reader", Set.of("READER"), u.revision(), false));
  }

  public synchronized Approval request(Caller c) {
    prune();
    if (!enabled()) throw new SecurityException("DISABLED");
    if (grants.size() >= 20) throw new SecurityException("APPROVAL_LIMIT");
    String id = UUID.randomUUID().toString();
    var view = new Approval(id, c.label(), clock.instant().plusSeconds(120), "PENDING");
    grants.put(id, new Grant(c.binding(), c.actor().revision(), view));
    return view;
  }

  public synchronized void consume(Caller c, String id) {
    prune();
    if (!enabled()) throw new SecurityException("DISABLED");
    var g = grants.get(id);
    if (g == null
        || !g.binding().equals(c.binding())
        || g.revision() != c.actor().revision()
        || !g.view().state().equals("APPROVED")) throw new SecurityException("APPROVAL_REQUIRED");
    var u = users.active(c.actor().id(), c.actor().revision());
    if (u == null || !u.reads()) throw new SecurityException("ACCESS_REVOKED");
    grants.remove(id);
  }

  public synchronized void audit(String caller, String outcome, String requestId) {
    var event =
        new Audit(
            clock.instant(), caller, SCOPE, "get_current_financial_month", outcome, requestId);
    if (audit.size() == 100) audit.removeFirst();
    audit.addLast(event);
    org.slf4j.LoggerFactory.getLogger(McpAccess.class)
        .info(
            "MCP time={} caller={} scope={} tool={} outcome={} request={}",
            event.timestamp(),
            caller,
            SCOPE,
            event.tool(),
            outcome,
            requestId);
  }

  public String audience() {
    return audience;
  }

  public String issuer() {
    return issuer;
  }
}
