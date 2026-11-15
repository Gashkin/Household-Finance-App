package com.householdfinance.bank;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.*;
import com.householdfinance.ledger.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Production protocol with generated keys and a loopback-only test provider. No bank access. */
@SpringBootTest(properties={"workspace.password=synthetic-production-password", "spring.datasource.url=jdbc:h2:mem:production-protocol;DB_CLOSE_DELAY=-1"})
@EnabledOnOs(OS.LINUX)
class ProductionProtocolTest {
  @Autowired LedgerService ledger;
  @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
  @TempDir Path folder;
  SyntheticProvider provider;
  ProductionConfig config;
  SandboxGateway gateway;
  SandboxService service;
  LocalDate today=LocalDate.now(ZoneId.of("Europe/Dublin"));
  @BeforeEach void before() throws Exception {
    for(String table:List.of("LEDGER_ENTRIES","LEDGER_AUDIT","LEDGER_ACCOUNTS")) jdbc.update("DELETE FROM "+table);
    Files.setPosixFilePermissions(folder,PosixFilePermissions.fromString("rwx------"));
    provider=new SyntheticProvider("http://127.0.0.1:8080/bank/callback");
    provider.environment="PRODUCTION";
    byte[] key=new byte[32]; new java.security.SecureRandom().nextBytes(key);
    config=new ProductionConfig(true,provider.app,"unused-in-test",provider.callback,folder.resolve("connection.bin").toString(),Base64.getEncoder().encodeToString(key),true);
    gateway=new SandboxGateway(config,provider.base(),provider.key::getPrivate,new ObjectMapper());
    service=new SandboxService(gateway,ledger);
  }
  @AfterEach void after(){provider.close();}
  SandboxService.Status connect() {
    service.start(new SandboxService.Start("AIB","IE",true),"synthetic-binding");
    return service.complete(new SandboxService.Complete(provider.state,"synthetic-code"),"synthetic-binding");
  }
  SandboxService.Fetch input(SandboxService.Status status) {
    var target=ledger.create(new LedgerService.AccountInput("Synthetic AIB "+UUID.randomUUID(),"CASH",today,"1000","Synthetic booked statement"));
    return new SandboxService.Fetch(status.accounts().getFirst().handle(),target.id(),today,today,true);
  }
  @Test void realRuntimeGatewayCannotBeEnabledByDirectEnvironmentProvisioning() {
    assertThrows(IllegalArgumentException.class, () -> new SandboxGateway(
        false, "", "", "http://127.0.0.1:8080/bank/callback",
        new ObjectMapper(), config));
  }
  @Test void persistentProductionReferencesSurviveRestartWithoutPlaintextOrImplicitApproval() throws Exception {
    var status=connect(); var request=input(status);
    var fetched=service.fetch(request);
    assertEquals("PRODUCTION",status.environment());
    assertTrue(fetched.preview().proposals().stream().allMatch(p->p.disposition().equals("NEW")));
    assertFalse(provider.auth.toString().contains("payment"));
    assertTrue(Duration.between(Instant.now(),Instant.parse(provider.auth.path("access").path("valid_until").asText())).toDays()>=179);
    String disk=new String(Files.readAllBytes(Path.of(config.storePath())),java.nio.charset.StandardCharsets.ISO_8859_1);
    assertFalse(disk.contains(provider.session)); assertFalse(disk.contains(provider.account));
    service=new SandboxService(gateway,ledger);
    assertEquals("CONNECTED",service.status().state());
    assertEquals(status.accounts(),service.status().accounts());
    var preview=fetched.preview();
    ledger.approve(request.ledgerAccountId(),preview.id(),new LedgerService.Approval(preview.revision(),preview.proposals().stream().map(LedgerService.Proposal::row).toList(),true));
    var duplicate=service.fetch(request);
    assertTrue(duplicate.preview().proposals().stream().allMatch(p->p.disposition().equals("DUPLICATE")));
    service.disconnect();
    assertEquals("DISCONNECTED",new SandboxService(gateway,ledger).status().state());
  }
  @Test void failedRevocationIsDurablyBlockedAndRetryable() {
    var request=input(connect()); provider.mode="delete-fail";
    assertThrows(IllegalArgumentException.class,()->service.disconnect());
    service=new SandboxService(gateway,ledger);
    assertEquals("REVOCATION_PENDING",service.status().state());
    assertThrows(IllegalArgumentException.class,()->service.fetch(request));
    provider.mode="ok"; service.disconnect();
    assertEquals("DISCONNECTED",new SandboxService(gateway,ledger).status().state());
  }
  @Test void dailyLimitCannotBeResetByRestartOrReconnect() {
    var request=input(connect());
    for(int i=0;i<4;i++) service.fetch(request);
    service=new SandboxService(gateway,ledger);
    assertThrows(IllegalArgumentException.class,()->service.fetch(request));
    service.disconnect(); var renewed=connect();
    var next=new SandboxService.Fetch(renewed.accounts().getFirst().handle(),request.ledgerAccountId(),today,today,true);
    assertThrows(IllegalArgumentException.class,()->service.fetch(next));
  }
  @Test void staleConsentAndPartialPagesNeverChangeApprovedFacts() {
    var request=input(connect()); provider.mode="partial";
    assertThrows(IllegalArgumentException.class,()->service.fetch(request));
    assertNull(service.status().lastSuccessfulFetch());
    provider.mode="revoked";
    assertThrows(IllegalArgumentException.class,()->service.fetch(request));
    assertEquals("RECONNECT_REQUIRED",new SandboxService(gateway,ledger).status().state());
  }
  @Test void metadataRejectsWrongEnvironmentPaymentScopeAndUnsafeRedirects() {
    for(String mode:List.of("pis", "pis-only", "unknown-scope", "empty-scope", "inactive")) {
      provider.mode=mode; assertThrows(IllegalArgumentException.class,()->service.banks());
      assertThrows(IllegalArgumentException.class,()->service.start(new SandboxService.Start("AIB","IE",true),"synthetic-binding"));
      assertEquals("",provider.state); // Metadata rejection occurs before consent creation.
    }
    provider.mode="ok"; provider.environment="SANDBOX";
    assertThrows(IllegalArgumentException.class,()->service.banks());
    assertThrows(IllegalArgumentException.class,()->gateway.request("POST","/payments",Map.of()));
    for(String url:List.of("https://auth.enablebanking.com/pis/start","https://auth.enablebanking.com/ais/../pis/start","https://auth.enablebanking.com/ais/%2e%2e/pis/start","https://tilisy-sandbox.enablebanking.com/","https://auth.enablebanking.com:8443/ais/start","https://evil.example/ais/start"))
      assertThrows(IllegalArgumentException.class,()->gateway.safeRedirect(url));
  }
  @Test void expiredConsentAfterRestartCannotRefreshOrMutateReviewedRecords() {
    var request=input(connect());
    var before=ledger.position(today);
    provider.mode="expired";
    service=new SandboxService(gateway,ledger);
    assertThrows(IllegalArgumentException.class,()->service.fetch(request));
    assertEquals(before,ledger.position(today));
    assertNull(service.status().lastSuccessfulFetch());
  }
  @Test void malformedRemoteExpiryRequiresReconnectAfterRestart() {
    var request=input(connect());
    provider.mode="bad-expiry";
    assertThrows(IllegalArgumentException.class,()->service.fetch(request));
    assertEquals("RECONNECT_REQUIRED",service.status().state());
    service=new SandboxService(gateway,ledger);
    assertEquals("RECONNECT_REQUIRED",service.status().state());
    assertThrows(IllegalArgumentException.class,()->service.fetch(request));
  }
  @Test void tamperWrongKeyOrApplicationNeverSilentlyOverwriteConnection() throws Exception {
    connect(); Path path=Path.of(config.storePath()); byte[] original=Files.readAllBytes(path);
    byte[] wrong=new byte[32]; Arrays.fill(wrong,(byte)1);
    var wrongConfig=new ProductionConfig(true,provider.app,"",provider.callback,path.toString(),Base64.getEncoder().encodeToString(wrong),true);
    assertThrows(IllegalArgumentException.class,()->new ConnectionStore(wrongConfig).read(SandboxService.Saved.class));
    byte[] tampered=original.clone(); tampered[20]^=1; Files.write(path,tampered);
    assertThrows(IllegalArgumentException.class,()->new SandboxService(gateway,ledger));
    assertArrayEquals(tampered,Files.readAllBytes(path));
  }
  @Test void insecureStorageAndMissingReadinessFailBeforeAnyProviderAction() throws Exception {
    Files.setPosixFilePermissions(folder,PosixFilePermissions.fromString("rwxr-xr-x"));
    assertThrows(IllegalArgumentException.class,()->new ConnectionStore(config));
    Files.setPosixFilePermissions(folder,PosixFilePermissions.fromString("rwx------"));
    var unready=new ProductionConfig(true,provider.app,"",provider.callback,config.storePath(),config.storageKey(),false);
    assertThrows(IllegalArgumentException.class,()->new ConnectionStore(unready));
    assertEquals("",provider.authorization);
  }
  @Test void lostInitializedStateFailsClosedWithoutOverwritingOrCreatingAnotherSession() throws Exception {
    connect(); Path path=Path.of(config.storePath());
    Files.delete(path);
    assertThrows(IllegalArgumentException.class,()->new SandboxService(gateway,ledger));
    assertFalse(Files.exists(path)); assertEquals(0,provider.deletes);
  }
  @Test void malformedSessionReferenceRetainsDurableUnresolvedRevocationUntilOwnerConfirms() {
    for(String mode:List.of("malformed-session","session-bad-json","session-exchange-fail")) {
    provider.mode=mode;
    service.start(new SandboxService.Start("AIB","IE",true),"synthetic-binding");
    assertThrows(IllegalArgumentException.class,()->service.complete(new SandboxService.Complete(provider.state,"synthetic-code"),"synthetic-binding"));
    service=new SandboxService(gateway,ledger);
    assertEquals("REVOCATION_PENDING",service.status().state());
    assertThrows(IllegalArgumentException.class,()->service.forget());
    assertThrows(IllegalArgumentException.class,()->service.disconnect());
    assertThrows(IllegalArgumentException.class,()->service.start(new SandboxService.Start("AIB","IE",true),"synthetic-binding"));
    assertThrows(IllegalArgumentException.class,()->service.confirmExternalRevocation(false));
    assertEquals(0,provider.deletes); // No valid ID exists: never invent an unsafe DELETE path.
    assertEquals("DISCONNECTED",service.confirmExternalRevocation(true).state());
    assertEquals("DISCONNECTED",new SandboxService(gateway,ledger).status().state());
    }
  }
  @Test void forgettingCannotDiscardAFailedRevocationReferenceAcrossRestart() {
    connect(); provider.mode="delete-fail";
    assertThrows(IllegalArgumentException.class,()->service.disconnect());
    assertThrows(IllegalArgumentException.class,()->service.forget());
    service=new SandboxService(gateway,ledger);
    assertEquals("REVOCATION_PENDING",service.status().state());
    assertThrows(IllegalArgumentException.class,()->service.forget());
    provider.mode="ok";
    assertEquals("DISCONNECTED",service.disconnect().state());
    assertEquals(2,provider.deletes);
  }

}
