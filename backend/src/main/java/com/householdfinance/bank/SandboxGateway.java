package com.householdfinance.bank;

import com.fasterxml.jackson.databind.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.security.*;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.*;
import org.springframework.stereotype.Component;

/** Closed AIS allowlist; sandbox and explicitly provisioned production are mutually exclusive. */
@Component
public class SandboxGateway {
  private final boolean enabled;
  private ProductionConfig production = ProductionConfig.disabled();
  private final String appId;
  private final URI callback, base;
  private final Supplier<PrivateKey> signingKey;
  private final ObjectMapper json;
  private final HttpClient client =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(4))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();

  @Autowired
  public SandboxGateway(
      @Value("${bank.sandbox.enabled:false}") boolean enabled,
      @Value("${bank.sandbox.app-id:}") String id,
      @Value("${bank.sandbox.key-path:}") String path,
      @Value("${bank.sandbox.callback:http://127.0.0.1:5173/bank/callback}") String callback,
      ObjectMapper json, ProductionConfig production) {
    this(
        production.enabled() || enabled,
        production.enabled() ? production.appId() : id,
        production.enabled() ? production.callback() : callback,
        URI.create("https://api.enablebanking.com"),
        () -> loadPrivateKey(production.enabled() ? production.keyPath() : path),
        json);
    if (enabled && production.enabled()) throw new IllegalArgumentException("Select one banking environment");
    this.production = production;
    if (production.enabled()) new ConnectionStore(production);
  }

  // Package-private test transport: not exposed as runtime configuration or included in the UI.
  SandboxGateway(
      boolean enabled,
      String id,
      String callback,
      URI base,
      Supplier<PrivateKey> key,
      ObjectMapper json) {
    this.enabled = enabled;
    this.appId = id;
    this.callback = URI.create(callback);
    this.base = base;
    this.signingKey = key;
    this.json = json;
    if (!Set.of("127.0.0.1", "localhost", "[::1]")
            .contains(Objects.toString(this.callback.getHost(), ""))
        || !Set.of("http", "https").contains(Objects.toString(this.callback.getScheme(), ""))
        || !"/bank/callback".equals(this.callback.getPath())
        || this.callback.getQuery() != null
        || this.callback.getFragment() != null
        || this.callback.getUserInfo() != null)
      throw new IllegalArgumentException(
          "Sandbox callback must be an exact loopback /bank/callback URL");
    if (!base.equals(URI.create("https://api.enablebanking.com"))
        && !("http".equals(base.getScheme()) && "127.0.0.1".equals(base.getHost())))
      throw new IllegalArgumentException("Unsupported test transport");
  }

  SandboxGateway(ProductionConfig config, URI base, Supplier<PrivateKey> key, ObjectMapper json) {
    this(true, config.appId(), config.callback(), base, key, json);
    this.production = config;
    new ConnectionStore(config);
  }

  ProductionConfig production() { return production; }
  boolean isProduction() { return production.enabled(); }
  String environment() { return isProduction() ? "PRODUCTION" : "SANDBOX"; }

  public boolean configured() {
    return enabled && !appId.isBlank();
  }

  public String callback() {
    return callback.toString();
  }

  private static PrivateKey loadPrivateKey(String path) {
    byte[] bytes = null, der = null;
    try {
      Path file = Path.of(path).toAbsolutePath().normalize();
      if (Files.isSymbolicLink(file)
          || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
          || Files.size(file) > 16384) throw new IOException();
      file = file.toRealPath();
      for (Path parent = Path.of("").toAbsolutePath(); parent != null; parent = parent.getParent())
        if (Files.exists(parent.resolve(".git")) && file.startsWith(parent))
          throw new IOException();
      PosixFileAttributeView posix = Files.getFileAttributeView(file, PosixFileAttributeView.class);
      if (posix != null) {
        if (!Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)
            .containsAll(posix.readAttributes().permissions())) throw new IOException();
      } else {
        AclFileAttributeView acl = Files.getFileAttributeView(file, AclFileAttributeView.class);
        if (acl == null) throw new IOException();
        if (!privateAcl(acl.getOwner().getName(), acl.getAcl())) throw new IOException();
      }
      bytes = Files.readAllBytes(file);
      String pem = new String(bytes, StandardCharsets.US_ASCII);
      if (!pem.startsWith("-----BEGIN PRIVATE KEY-----")
          || !pem.strip().endsWith("-----END PRIVATE KEY-----")) throw new IOException();
      der =
          Base64.getMimeDecoder()
              .decode(
                  pem.replace("-----BEGIN PRIVATE KEY-----", "")
                      .replace("-----END PRIVATE KEY-----", ""));
      PrivateKey key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
      if (((RSAPrivateKey) key).getModulus().bitLength() < 2048) throw new IOException();
      return key;
    } catch (Exception e) {
      throw new IllegalArgumentException(
          "Sandbox signing key unavailable or not privately stored; configure an owner-only PKCS#8"
              + " PEM file outside the repository");
    } finally {
      if (bytes != null) Arrays.fill(bytes, (byte) 0);
      if (der != null) Arrays.fill(der, (byte) 0);
    }
  }

  static boolean privateAcl(String owner, List<AclEntry> entries) {
    for (AclEntry entry : entries) {
      if (entry.type() != AclEntryType.ALLOW || entry.permissions().isEmpty()) continue;
      String principal = entry.principal().getName();
      if (!principal.equalsIgnoreCase(owner)
          && !principal.equalsIgnoreCase("NT AUTHORITY\\SYSTEM")
          && !principal.equalsIgnoreCase("SYSTEM")
          && !principal.equalsIgnoreCase("BUILTIN\\Administrators")) return false;
    }
    return true;
  }

  private String jwt() {
    try {
      UUID.fromString(appId);
      long now = Instant.now().getEpochSecond();
      var encoder = Base64.getUrlEncoder().withoutPadding();
      String header =
          encoder.encodeToString(
              json.writeValueAsBytes(Map.of("typ", "JWT", "alg", "RS256", "kid", appId)));
      String payload =
          encoder.encodeToString(
              json.writeValueAsBytes(
                  Map.of(
                      "iss",
                      "enablebanking.com",
                      "aud",
                      "api.enablebanking.com",
                      "iat",
                      now,
                      "exp",
                      now + 300)));
      String body = header + "." + payload;
      Signature signature = Signature.getInstance("SHA256withRSA");
      signature.initSign(signingKey.get());
      signature.update(body.getBytes(StandardCharsets.US_ASCII));
      return body + "." + encoder.encodeToString(signature.sign());
    } catch (IllegalArgumentException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalArgumentException("Sandbox request signing unavailable");
    }
  }

  JsonNode request(String method, String path, Object body) {
    if (!configured())
      throw new IllegalArgumentException(
          "Enable the sandbox connector and configure its application ID/private key locally");
    String plain = path.split("\\?", 2)[0];
    boolean allowed =
        (method.equals("GET")
                && (plain.equals("/application")
                    || plain.equals("/aspsps")
                    || plain.matches("/sessions/[0-9a-f-]{36}")
                    || plain.matches("/accounts/[0-9a-f-]{36}/(?:balances|transactions)")))
            || (method.equals("POST") && Set.of("/auth", "/sessions").contains(plain))
            || (method.equals("DELETE") && plain.matches("/sessions/[0-9a-f-]{36}"));
    if (!allowed || path.contains("#") || path.contains(".."))
      throw new IllegalArgumentException("Only bank AIS operations are allowed");
    CompletableFuture<HttpResponse<byte[]>> future = null;
    try {
      var builder =
          HttpRequest.newBuilder(base.resolve(path))
              .timeout(Duration.ofSeconds(7))
              .header("Authorization", "Bearer " + jwt())
              .header("Accept", "application/json");
      if (body == null) builder.method(method, HttpRequest.BodyPublishers.noBody());
      else
        builder
            .header("Content-Type", "application/json")
            .method(method, HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(body)));
      future = client.sendAsync(builder.build(), info -> new BoundedBody());
      var response = future.get(8, TimeUnit.SECONDS);
      if (method.equals("DELETE") && Set.of(404, 410).contains(response.statusCode()))
        return json.createObjectNode();
      if (response.statusCode() < 200 || response.statusCode() >= 300)
        throw new IllegalArgumentException(
            "Bank provider request failed (HTTP "
                + response.statusCode()
                + "); no ledger activity was imported");
      if (response.body().length == 0) return json.createObjectNode();
      JsonNode result = json.readTree(response.body());
      if (result == null || !result.isObject()) throw new IOException();
      return result;
    } catch (IllegalArgumentException e) {
      throw e;
    } catch (Exception e) {
      if (e instanceof InterruptedException) Thread.currentThread().interrupt();
      if (future != null) future.cancel(true);
      throw new IllegalArgumentException(
          "Bank provider unavailable or response invalid/too large; no partial ledger import");
    }
  }

  void verifySandbox() {
    var app = request("GET", "/application", null);
    boolean ais = false;
    for (var service : app.path("services")) if (service.asText().equals("AIS")) ais = true;
    boolean redirect = false;
    for (var url : app.path("redirect_urls")) if (callback().equals(url.asText())) redirect = true;
    boolean onlyAis = app.path("services").isArray() && app.path("services").size() == 1 && ais;
    if (!app.path("environment").asText().equals(environment())
        || (isProduction() && (!production.readiness() || !onlyAis))
        || !app.path("active").asBoolean(false)
        || !ais
        || !redirect)
      throw new IllegalArgumentException(
          "An active " + environment() + " AIS application with this exact callback URL is required; production requires AIS-only registration and local readiness");
  }

  String safeRedirect(String value) {
    URI uri;
    try {
      uri = URI.create(value);
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid sandbox consent redirect");
    }
    boolean mock =
        !base.getHost().equals("api.enablebanking.com")
            && "http".equals(uri.getScheme())
            && "127.0.0.1".equals(uri.getHost())
            && uri.getPort() == base.getPort();
    if (uri.getUserInfo() != null
        || ("auth.enablebanking.com".equals(uri.getHost()) && !"/ais/start".equals(uri.getRawPath()))
        || uri.getFragment() != null
        || (!mock
            && !("https".equals(uri.getScheme())
                && (isProduction() ? Set.of("auth.enablebanking.com") : Set.of("auth.enablebanking.com", "tilisy-sandbox.enablebanking.com"))
                    .contains(Objects.toString(uri.getHost(), ""))
                && (uri.getPort() == -1 || uri.getPort() == 443))))
      throw new IllegalArgumentException("Untrusted sandbox consent redirect");
    return value;
  }

  static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
    private final CompletableFuture<byte[]> result = new CompletableFuture<>();
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private Flow.Subscription subscription;

    public CompletionStage<byte[]> getBody() {
      return result;
    }

    public void onSubscribe(Flow.Subscription s) {
      subscription = s;
      s.request(1);
    }

    public void onNext(List<ByteBuffer> buffers) {
      for (var b : buffers) {
        if (bytes.size() + b.remaining() > 262144) {
          subscription.cancel();
          result.completeExceptionally(new IOException("Response limit"));
          return;
        }
        byte[] data = new byte[b.remaining()];
        b.get(data);
        bytes.writeBytes(data);
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
}
