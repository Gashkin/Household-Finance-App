package com.householdfinance.bank;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import com.householdfinance.ledger.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "workspace.password=synthetic-bank-password",
      "spring.datasource.url=jdbc:h2:mem:bank-protocol;DB_CLOSE_DELAY=-1"
    })
@AutoConfigureMockMvc
class SandboxProtocolTest {
  @Autowired LedgerService ledger;
  @Autowired MockMvc mvc;
  SyntheticProvider provider;
  SandboxService service;
  LocalDate today = LocalDate.now(ZoneId.of("Europe/Dublin"));

  @BeforeEach
  void before() throws Exception {
    provider = new SyntheticProvider("http://127.0.0.1:4173/bank/callback");
    service = new SandboxService(provider.gateway(), ledger);
  }

  @AfterEach
  void after() {
    provider.close();
  }

  SandboxService.Status connect() {
    service.start(new SandboxService.Start("Mock ASPSP", "FI", true), "workspace-a");
    return service.complete(
        new SandboxService.Complete(provider.state, "synthetic-code"), "workspace-a");
  }

  SandboxService.Fetch input(SandboxService.Status status) {
    var a =
        ledger.create(
            new LedgerService.AccountInput(
                "Synthetic " + UUID.randomUUID(), "CASH", today, "1000", "Synthetic statement"));
    return new SandboxService.Fetch(status.accounts().get(0).handle(), a.id(), today, today, true);
  }

  @Test
  void completePaginatedJourneySignsJwtAndNeverChangesLedgerBeforeReview() throws Exception {
    var status = connect();
    assertEquals("CONNECTED", status.state());
    assertTrue(provider.auth.path("access").path("balances").asBoolean());
    assertTrue(provider.auth.path("access").path("transactions").asBoolean());
    assertFalse(provider.auth.path("access").path("transactions_extended_history").asBoolean());
    assertFalse(provider.auth.toString().contains("payment"));
    var request = input(status);
    var fetched = service.fetch(request);
    assertEquals(2, provider.transactionPages);
    assertEquals(1, fetched.balances().size());
    assertEquals("990.00", fetched.balances().get(0).amountEuro());
    assertEquals(2, fetched.preview().proposals().size());
    assertNotNull(service.status().lastSuccessfulFetch());
    assertEquals(
        100000,
        ledger.position(today).accounts().stream()
            .filter(a -> a.id().equals(request.ledgerAccountId()))
            .findFirst()
            .orElseThrow()
            .bookedMinor());
    String token = provider.authorization.substring(7);
    String[] parts = token.split("\\.");
    var json = new ObjectMapper().findAndRegisterModules();
    var claims = json.readTree(Base64.getUrlDecoder().decode(parts[1]));
    assertEquals("api.enablebanking.com", claims.path("aud").asText());
    assertEquals(300, claims.path("exp").asLong() - claims.path("iat").asLong());
    var signature = Signature.getInstance("SHA256withRSA");
    signature.initVerify(provider.key.getPublic());
    signature.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
    assertTrue(signature.verify(Base64.getUrlDecoder().decode(parts[2])));
    var preview = fetched.preview();
    ledger.approve(
        request.ledgerAccountId(),
        preview.id(),
        new LedgerService.Approval(
            preview.revision(),
            preview.proposals().stream().map(LedgerService.Proposal::row).toList(),
            true));
    assertEquals(
        99000,
        ledger.position(today).accounts().stream()
            .filter(a -> a.id().equals(request.ledgerAccountId()))
            .findFirst()
            .orElseThrow()
            .bookedMinor());
    service.disconnect();
    var renewed = connect();
    var duplicate =
        service.fetch(
            new SandboxService.Fetch(
                renewed.accounts().get(0).handle(), request.ledgerAccountId(), today, today, true));
    assertTrue(
        duplicate.preview().proposals().stream()
            .allMatch(p -> p.disposition().equals("DUPLICATE")));
    String publicStatus = json.writeValueAsString(status);
    assertFalse(publicStatus.contains(provider.account));
    assertFalse(publicStatus.contains(provider.session));
    assertEquals("DISCONNECTED", service.disconnect().state());
    assertEquals(2, provider.deletes);
  }

  @Test
  void rejectsProductionAndUntrustedRedirect() {
    provider.environment = "PRODUCTION";
    assertThrows(IllegalArgumentException.class, () -> service.banks());
    provider.environment = "SANDBOX";
    provider.mode = "redirect";
    assertThrows(
        IllegalArgumentException.class,
        () -> service.start(new SandboxService.Start("Mock ASPSP", "FI", true), "a"));
  }

  @Test
  void callbackStateIsBoundToOriginalWorkspaceAndSingleUse() {
    service.start(new SandboxService.Start("Mock ASPSP", "FI", true), "a");
    var callback = new SandboxService.Complete(provider.state, "code");
    assertThrows(IllegalArgumentException.class, () -> service.complete(callback, "b"));
    assertThrows(
        IllegalArgumentException.class,
        () -> service.complete(new SandboxService.Complete("wrong", "code"), "a"));
    service.complete(callback, "a");
    assertThrows(IllegalArgumentException.class, () -> service.complete(callback, "a"));
  }

  @Test
  void unsupportedAccountsAreRevokedAndNeverExposed() {
    provider.mode = "currency";
    assertThrows(IllegalArgumentException.class, () -> connect());
    assertEquals(1, provider.deletes);
    assertTrue(service.status().accounts().isEmpty());
  }

  @Test
  void failedPaginationMissingIdentityAndOversizeNeverProducePartialImports() {
    for (String mode : List.of("partial", "repeat", "missing-id", "oversize")) {
      provider.mode = "ok";
      var status = connect();
      var request = input(status);
      provider.mode = mode;
      assertThrows(IllegalArgumentException.class, () -> service.fetch(request), mode);
      assertNull(service.status().lastSuccessfulFetch());
      assertFalse(service.status().lastError().isBlank());
      assertEquals(
          100000,
          ledger.position(today).accounts().stream()
              .filter(a -> a.id().equals(request.ledgerAccountId()))
              .findFirst()
              .orElseThrow()
              .bookedMinor());
      service.forget();
    }
  }

  @Test
  void revokedConsentAndFailedDisconnectBlockRefresh() {
    var request = input(connect());
    provider.mode = "revoked";
    assertThrows(IllegalArgumentException.class, () -> service.fetch(request));
    provider.mode = "delete-fail";
    assertThrows(IllegalArgumentException.class, () -> service.disconnect());
    assertEquals("REVOCATION_PENDING", service.status().state());
    assertThrows(IllegalArgumentException.class, () -> service.fetch(request));
    assertEquals("DISCONNECTED", service.forget().state());
  }

  @Test
  void expiredStateAndConsentAreRejected() throws Exception {
    service.start(new SandboxService.Start("Mock ASPSP", "FI", true), "a");
    var field = SandboxService.class.getDeclaredField("pending");
    field.setAccessible(true);
    Object pending = field.get(service);
    var constructor = pending.getClass().getDeclaredConstructors()[0];
    constructor.setAccessible(true);
    field.set(
        service,
        constructor.newInstance(
            provider.state, "a", Instant.EPOCH, Instant.now().plusSeconds(300)));
    assertThrows(
        IllegalArgumentException.class,
        () -> service.complete(new SandboxService.Complete(provider.state, "code"), "a"));
    service.forget();
    var request = input(connect());
    var expiry = SandboxService.class.getDeclaredField("expires");
    expiry.setAccessible(true);
    expiry.set(service, Instant.EPOCH);
    assertEquals("EXPIRED", service.status().state());
    assertThrows(IllegalArgumentException.class, () -> service.fetch(request));
  }

  @Test
  void privateKeyFileMustBeOwnerOnlyAndOutsideRepository() throws Exception {
    var file = java.nio.file.Files.createTempFile("synthetic-signing-", ".pem");
    try {
      String pem =
          "-----BEGIN PRIVATE KEY-----\n"
              + Base64.getMimeEncoder(64, new byte[] {10})
                  .encodeToString(provider.key.getPrivate().getEncoded())
              + "\n-----END PRIVATE KEY-----\n";
      java.nio.file.Files.writeString(file, pem);
      var load = SandboxGateway.class.getDeclaredMethod("loadPrivateKey", String.class);
      load.setAccessible(true);
      java.nio.file.Files.setPosixFilePermissions(
          file, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
      assertInstanceOf(PrivateKey.class, load.invoke(null, file.toString()));
      java.nio.file.Files.setPosixFilePermissions(
          file, java.nio.file.attribute.PosixFilePermissions.fromString("rw-r--r--"));
      assertThrows(
          java.lang.reflect.InvocationTargetException.class,
          () -> load.invoke(null, file.toString()));
    } finally {
      java.nio.file.Files.deleteIfExists(file);
    }
  }

  @Test
  void gatewayRejectsPaymentsAndUnsafeCallbacks() {
    assertThrows(
        IllegalArgumentException.class,
        () -> provider.gateway().request("POST", "/payments", Map.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SandboxGateway(
                true,
                provider.app,
                "https://evil.example/bank/callback",
                provider.base(),
                provider.key::getPrivate,
                new ObjectMapper()));
    assertThrows(
        IllegalArgumentException.class,
        () -> provider.gateway().safeRedirect("https://auth.enablebanking.com.evil.example/"));
  }

  @Test
  void unauthenticatedHttpAndMissingCsrfCannotOperateConnector() throws Exception {
    mvc.perform(get("/api/bank-sandbox/status")).andExpect(status().isUnauthorized());
    mvc.perform(post("/api/bank-sandbox/start").contentType("application/json").content("{}"))
        .andExpect(status().isForbidden());
  }
}
