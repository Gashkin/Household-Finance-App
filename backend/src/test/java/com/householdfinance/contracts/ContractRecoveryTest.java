package com.householdfinance.contracts;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.householdfinance.HouseholdFinanceApplication;
import com.householdfinance.access.AccessService;
import com.householdfinance.workspace.BackupCrypto;
import com.householdfinance.access.OwnerFixture;
import com.householdfinance.workspace.WorkspaceBackupService;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import static com.householdfinance.contracts.ContractReview.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(OwnerFixture.class)
class ContractRecoveryTest {
  @TempDir Path directory;
  private static final String PASSPHRASE = "synthetic-contract-archive-passphrase";
  private static final String CONFIRM = "RESTORE INTO EMPTY WORKSPACE";

  @Test
  void migratesVersionFourAndRestoresContractHistoryAndAlertDisposition() throws Exception {
    String source = url("source");
    Flyway.configure().dataSource(source, "sa", "").locations("classpath:db/migration").target("4").load().migrate();
    var baseline = new JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource(source, "sa", ""));
    baseline.update("INSERT INTO ACCOUNTS(ID,NAME,TYPE,BALANCE) VALUES('synthetic','Synthetic retained cash','Cash',12.34)");
    ContractsService.Detail snapshot;
    LocalDate snooze;
    byte[] currentBackup;
    byte[] legacyBackup;
    try (var app = start("source")) {
      var service = app.getBean(ContractsService.class);
      var alerts = app.getBean(ContractAlerts.class);
      var backup = app.getBean(WorkspaceBackupService.class);
      assertEquals(1L, backup.counts().get("ACCOUNTS"));
      assertEquals(0L, backup.counts().get("HOUSEHOLD_CONTRACTS"));
      var today = alerts.today();
      snooze = today.plusDays(10);
      var terms = new Terms("Synthetic insurer", "Synthetic renewal", TermType.FIXED, today.minusYears(1), true,
          new DatedEvidence(Certainty.VERIFIED, today.plusDays(29), "Synthetic policy", null),
          null, null, null, null, null, null, null, Frequency.YEARLY,
          null, new Price("124.00", "EUR", today.minusYears(1)), null, null, null);
      var stored = service.create(new ContractsService.Create(UUID.randomUUID().toString().replace("-", ""),
          "Synthetic annual contract", Kind.CONTRACT, null, terms));
      var id = stored.summary().id();
      var termId = stored.latestTermId();
      snapshot = service.decide(id, termId, new ContractsService.DecisionInput(1L, Decision.RENEW, today, "Synthetic comparison"));
      var row = alerts.inbox().notifications().getFirst();
      alerts.change(id, termId, new ContractAlerts.Change(ContractAlerts.Action.SNOOZE, snooze, row.timing()));

      currentBackup = backup.exportBackup(PASSPHRASE);
      var json = app.getBean(ObjectMapper.class);
      var parsed = json.readValue(BackupCrypto.decrypt(currentBackup, PASSPHRASE), WorkspaceBackupService.Archive.class);
      assertEquals(9, parsed.version());
      var legacy = new LinkedHashMap<>(com.householdfinance.workspace.LegacyArchiveFixture.tables(parsed,7));
      legacy.remove("HOUSEHOLD_CONTRACTS");
      legacy.remove("CONTRACT_REVIEW_ALERTS");
      legacyBackup = BackupCrypto.encrypt(json.writeValueAsBytes(new WorkspaceBackupService.Archive(7, parsed.createdAt(), legacy)), PASSPHRASE);
    }

    try (var app = start("source")) { assertRestored(app, snapshot, snooze); }
    try (var app = start("restored")) {
      app.getBean(WorkspaceBackupService.class).restore(currentBackup, PASSPHRASE, CONFIRM);
      assertRestored(app, snapshot, snooze);
    }
    try (var app = start("legacy")) {
      var counts = app.getBean(WorkspaceBackupService.class).restore(legacyBackup, PASSPHRASE, CONFIRM);
      assertEquals(1L, counts.get("ACCOUNTS"));
      assertEquals(0L, counts.get("HOUSEHOLD_CONTRACTS"));
      assertEquals(0L, counts.get("CONTRACT_REVIEW_ALERTS"));
    }
  }

  private void assertRestored(ConfigurableApplicationContext app, ContractsService.Detail expected, LocalDate snooze) {
    var id = expected.summary().id();
    var saved = app.getBean(ContractsService.class).detail(id);
    assertEquals(expected.summary(), saved.summary());
    assertEquals(expected.terms().getFirst().term(), saved.terms().getFirst().term());
    var inbox = app.getBean(ContractAlerts.class).inbox();
    assertEquals(1, inbox.notifications().size());
    assertEquals(snooze, inbox.notifications().getFirst().snoozedUntil());
    assertFalse(inbox.notifications().getFirst().unread());
  }

  private String url(String name) {
    return "jdbc:h2:file:" + directory.resolve(name) + ";DB_CLOSE_ON_EXIT=FALSE";
  }

  private ConfigurableApplicationContext start(String name) {
    var app = new SpringApplicationBuilder(HouseholdFinanceApplication.class).web(WebApplicationType.NONE)
        .run("--spring.datasource.url=" + url(name), "--workspace.password=synthetic-contract-recovery-password",
            "--spring.main.banner-mode=off", "--logging.level.root=ERROR");
    app.getBean(AccessService.class).ensureOwner();
    return app;
  }
}
