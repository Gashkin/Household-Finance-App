package com.householdfinance.workspace;

import static org.junit.jupiter.api.Assertions.*;

import com.householdfinance.HouseholdFinanceApplication;
import com.householdfinance.access.*;
import com.householdfinance.ledger.*;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

@org.junit.jupiter.api.extension.ExtendWith(OwnerFixture.class)
class DebtRecoveryTest {
  @TempDir Path directory;

  ConfigurableApplicationContext start(String name) {
    return new SpringApplicationBuilder(HouseholdFinanceApplication.class)
        .web(WebApplicationType.NONE)
        .run(
            "--spring.datasource.url=jdbc:h2:file:"
                + directory.resolve(name)
                + ";DB_CLOSE_ON_EXIT=FALSE",
            "--workspace.password=synthetic-debt-recovery-password",
            "--spring.main.banner-mode=off",
            "--logging.level.root=ERROR");
  }

  @Test
  void statementsDatedRatesReplaysReportsAndForecastSurviveRestartAndEncryptedRestore() {
    byte[] archive;
    DebtService.Report report;
    CommitmentService.Outlook outlook;
    DebtService.Change input;
    List<String> history;
    var day = LocalDate.now(ZoneId.of("Europe/Dublin"));
    try (var app = start("source")) {
      app.getBean(AccessService.class).ensureOwner();
      var ledger = app.getBean(LedgerService.class);
      var debts = app.getBean(DebtService.class);
      String
          cash =
              ledger
                  .create(
                      new LedgerService.AccountInput(
                          "Synthetic durable cash",
                          "CASH",
                          day.minusMonths(2),
                          "2000",
                          "Synthetic statement"))
                  .id(),
          debt =
              ledger
                  .create(
                      new LedgerService.AccountInput(
                          "Synthetic durable mortgage",
                          "DEBT",
                          day.minusMonths(2),
                          "-1000",
                          "Synthetic statement"))
                  .id();
      var terms =
          new DebtService.Terms(
              debt,
              "Synthetic mortgage",
              "MORTGAGE",
              cash,
              "0",
              "100",
              "1",
              day.plusYears(10),
              "1000",
              "5",
              "Synthetic repayment allowance and fee",
              List.of(
                  new DebtService.Rate(day.minusMonths(2), "6"),
                  new DebtService.Rate(day.plusMonths(2), "7")));
      var statement =
          new DebtService.Statement(
              UUID.randomUUID().toString(),
              day.minusMonths(1),
              day.minusDays(1),
              day.plusDays(10),
              "Synthetic complete statement",
              true,
              "1000",
              "0",
              "0",
              "0",
              "0",
              "1000",
              "10",
              null);
      input =
          new DebtService.Change(
              0L,
              UUID.randomUUID().toString(),
              "Synthetic durable debt evidence",
              UUID.randomUUID().toString(),
              terms,
              statement);
      debts.change(input);
      report = debts.report(new DebtService.Compare(1L, input.id(), "100", "100"));
      outlook = app.getBean(CommitmentService.class).outlook(day, day.plusMonths(6), false, true);
      history = debts.history().stream().map(a -> a.afterJson).toList();
      archive =
          app.getBean(WorkspaceBackupService.class)
              .exportBackup("synthetic-debt-archive-passphrase");
    }
    try (var app = start("source")) {
      assertSaved(app, report, outlook, input, history);
    }
    try (var app = start("restored")) {
      app.getBean(AccessService.class).ensureOwner();
      assertThrows(
          IllegalArgumentException.class,
          () ->
              app.getBean(WorkspaceBackupService.class)
                  .restore(archive, "incorrect-debt-passphrase", "RESTORE INTO EMPTY WORKSPACE"));
      assertTrue(app.getBean(DebtService.class).get().state().debts().isEmpty());
      app.getBean(WorkspaceBackupService.class)
          .restore(archive, "synthetic-debt-archive-passphrase", "RESTORE INTO EMPTY WORKSPACE");
      assertSaved(app, report, outlook, input, history);
    }
    try (var app = start("restored")) {
      assertSaved(app, report, outlook, input, history);
    }
  }

  void assertSaved(
      ConfigurableApplicationContext app,
      DebtService.Report expected,
      CommitmentService.Outlook outlook,
      DebtService.Change input,
      List<String> history) {
    var debts = app.getBean(DebtService.class);
    var actual = debts.report(new DebtService.Compare(1L, input.id(), "100", "100"));
    assertEquals(expected.statementEvidence(), actual.statementEvidence());
    assertEquals(expected.comparison(), actual.comparison());
    assertEquals(
        outlook,
        app.getBean(CommitmentService.class)
            .outlook(outlook.baseline(), outlook.end(), false, true));
    assertEquals(history, debts.history().stream().map(a -> a.afterJson).toList());
    assertEquals(expected.statementEvidence().state(), debts.change(input));
    assertEquals(1, debts.history().size());
    assertEquals(0, app.getBean(LedgerEntryRepository.class).count());
  }
}
