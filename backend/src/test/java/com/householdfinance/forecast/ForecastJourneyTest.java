package com.householdfinance.forecast;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import com.householdfinance.ledger.*;
import com.householdfinance.workspace.*;
import jakarta.servlet.http.Cookie;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "workspace.password=synthetic-forecast-workspace-password",
      "spring.datasource.url=jdbc:h2:mem:forecast-journey;DB_CLOSE_DELAY=-1"
    })
@AutoConfigureMockMvc
@org.junit.jupiter.api.extension.ExtendWith(com.householdfinance.access.OwnerFixture.class)
class ForecastJourneyTest {
  @Autowired ForecastService service;
  @Autowired ForecastPlanRepository repository;
  @Autowired LedgerService ledger;
  @Autowired com.householdfinance.ledger.ActivityReviewService review;
  @Autowired WorkspaceBackupService backups;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;
  @Autowired MockMvc mvc;
  final LocalDate today = LocalDate.now(ZoneId.of("Europe/Dublin"));
  Cookie cookie;

  @BeforeEach
  void reset() throws Exception {
    clear();
    var response =
        mvc.perform(
                post("/api/workspace/unlock")
                    .header("X-Workspace-Request", "1")
                    .contentType("application/json")
                    .content("{\"password\":\"synthetic-forecast-workspace-password\"}"))
            .andExpect(status().isNoContent())
            .andReturn()
            .getResponse();
    cookie =
        new Cookie(
            "workspace_session",
            response.getHeader("Set-Cookie").split(";", 2)[0].split("=", 2)[1]);
  }

  void clear() {
    var tables = new ArrayList<>(backups.counts().keySet());
    Collections.reverse(tables);
    tables.forEach(t -> jdbc.update("DELETE FROM " + t));
  }

  ForecastService.BaselineInput manual(LocalDate date, String cash, String pending) {
    return new ForecastService.BaselineInput(
        "MANUAL", date, cash, pending, "Synthetic statement estimate");
  }

  ForecastService.EventInput event(
      LocalDate date, String direction, String certainty, String amount) {
    return new ForecastService.EventInput(
        UUID.randomUUID().toString(),
        date,
        "Synthetic " + direction,
        direction,
        certainty,
        amount,
        "Synthetic source");
  }

  ForecastService.Input input(
      long revision,
      ForecastService.BaselineInput baseline,
      List<ForecastService.EventInput> events) {
    return new ForecastService.Input(
        revision, "Synthetic dated plan", baseline, today.plusDays(10), "200", events, true);
  }

  @Test
  void datedBillsAndDelayedIncomeExplainShortfallWithoutChangingActualCash() {
    var salary = event(today.plusDays(3), "INCOME", "EXPECTED", "3000");
    var events =
        List.of(
            event(today.plusDays(1), "EXPENSE", "EXPECTED", "1500"),
            salary,
            event(today.plusDays(5), "EXPENSE", "EXPECTED", "200"),
            event(today.plusDays(6), "EXPENSE", "EXPECTED", "1000"),
            event(today.plusDays(2), "INCOME", "UNCERTAIN", "1000"));
    var plan = service.save(input(0, manual(today, "2000", "100"), events)).plan();
    var base = plan.projection();
    assertEquals(190000, base.startingMinor());
    assertEquals(20000, base.planningHeadroomMinor());
    assertEquals(0, base.cashOnlyHeadroomMinor());
    assertNull(base.shortfallDate());
    assertEquals(100000, base.uncertainIncomeMinor());
    assertEquals("ESTIMATED", plan.evidenceState());
    assertFalse(plan.currentDecisionReady());
    String saved = repository.findById("household").orElseThrow().payload;
    var comparison =
        service.compare(
            new ForecastService.WhatIf(
                plan.revision(), "500", today.plusDays(2), salary.id(), today.plusDays(8), false));
    assertEquals(today.plusDays(2), comparison.scenario().shortfallDate());
    assertEquals(0, comparison.scenario().planningHeadroomMinor());
    assertEquals(saved, repository.findById("household").orElseThrow().payload);
    assertEquals(0, ledger.position(today).cashMinor());
  }

  @Test
  void cashAccountOpenedAfterBaselineInvalidatesTheFrozenSourceSet() {
    var date = today.minusDays(1);
    var original =
        ledger.create(
            new LedgerService.AccountInput(
                "Original synthetic cash", "CASH", date, "1000", "Synthetic opening"));
    ledger.reconcile(
        original.id(),
        date,
        new LedgerService.Reconcile(
            original.revision(), "1000", "Synthetic dated closing", true, false));
    var plan =
        service
            .save(
                input(
                    0,
                    new ForecastService.BaselineInput("LEDGER", date, null, null, null),
                    List.of()))
            .plan();
    assertEquals("DATED", plan.evidenceState());
    assertFalse(plan.currentDecisionReady());
    ledger.create(
        new LedgerService.AccountInput(
            "Later synthetic cash", "CASH", today, "500", "New synthetic account"));
    // The historical position intentionally excludes this account. Freshness must not.
    assertEquals(1, ledger.position(date).accounts().size());
    var changed = service.get().plan();
    assertEquals("SOURCE_CHANGED", changed.evidenceState());
    assertFalse(changed.currentDecisionReady());
    assertEquals(plan.inputs().baseline(), changed.inputs().baseline());
    assertEquals(100000, changed.projection().startingMinor());
    var comparison =
        service.compare(
            new ForecastService.WhatIf(plan.revision(), "1", today.plusDays(1), null, null, false));
    assertEquals("SOURCE_CHANGED", comparison.checkedPlan().evidenceState());
    assertFalse(comparison.checkedPlan().currentDecisionReady());
  }

  @Test
  void sameDayOutflowsPrecedeIncome() {
    var i =
        input(
            0,
            manual(today, "100", "0"),
            List.of(
                event(today.plusDays(1), "INCOME", "EXPECTED", "1000"),
                event(today.plusDays(1), "EXPENSE", "EXPECTED", "200")));
    var p = service.save(i).plan().projection();
    assertEquals(-10000, p.lowestMinor());
    assertEquals(today.plusDays(1), p.shortfallDate());
    assertEquals(90000, p.closingMinor());
    assertEquals(0, p.planningHeadroomMinor());
  }

  @Test
  void uncertainIncomeIsUpsideOnlyAndNeverInflatesStartingCash() {
    var p =
        service
            .save(
                new ForecastService.Input(
                    0L,
                    "Upside",
                    manual(today, "0", "0"),
                    today.plusDays(5),
                    "0",
                    List.of(
                        event(today.plusDays(1), "INCOME", "UNCERTAIN", "1000"),
                        event(today.plusDays(2), "EXPENSE", "EXPECTED", "100")),
                    true))
            .plan();
    assertEquals(-10000, p.projection().closingMinor());
    var c = service.compare(new ForecastService.WhatIf(p.revision(), "0", null, null, null, true));
    assertEquals(90000, c.scenario().closingMinor());
    assertNull(c.scenario().shortfallDate());
    assertEquals(0, c.scenario().startingMinor());
    assertEquals(0, c.scenario().planningHeadroomMinor());
    assertFalse(c.currentDecisionReady());
  }

  @Test
  void changedLedgerNeverSilentlyRefreshesBaseline() {
    var a =
        ledger.create(
            new LedgerService.AccountInput(
                "Synthetic current", "CASH", today, "1000", "Synthetic opening"));
    ledger.manual(
        a.id(),
        new LedgerService.Manual(
            0L,
            new LedgerService.Row(
                "pending", today, "Pending synthetic card", "-50", "PENDING", "ACTIVITY"),
            "Synthetic authorization",
            true));
    var revision = ledger.position(today).accounts().get(0).revision();
    ledger.reconcile(
        a.id(),
        today,
        new LedgerService.Reconcile(revision, "1000", "Synthetic closing", true, false));
    var baseline = new ForecastService.BaselineInput("LEDGER", today, null, null, null);
    var p = service.save(input(0, baseline, List.of())).plan();
    assertEquals("REVIEWED", p.evidenceState());
    assertTrue(p.currentDecisionReady());
    assertEquals(95000, p.projection().startingMinor());
    review.reopen(a.id(),new com.householdfinance.ledger.ActivityReviewService.Reopen(revision,"Synthetic late receipt"));
    revision = ledger.position(today).accounts().get(0).revision();
    ledger.manual(
        a.id(),
        new LedgerService.Manual(
            revision,
            new LedgerService.Row("booked", today, "Synthetic debit", "-10", "BOOKED", "ACTIVITY"),
            "Synthetic receipt",
            true));
    var changed = service.get().plan();
    assertEquals("SOURCE_CHANGED", changed.evidenceState());
    assertFalse(changed.currentDecisionReady());
    var checked =
        service.compare(
            new ForecastService.WhatIf(
                changed.revision(), "1", today.plusDays(1), null, null, false));
    assertEquals("SOURCE_CHANGED", checked.checkedPlan().evidenceState());
    assertFalse(checked.checkedPlan().currentDecisionReady());

    assertEquals(100000, changed.inputs().baseline().cashMinor());
    assertThrows(
        IllegalArgumentException.class,
        () -> service.save(input(changed.revision(), baseline, List.of())));
    var current = ledger.position(today).accounts().get(0);
    ledger.reconcile(
        a.id(),
        today,
        new LedgerService.Reconcile(current.revision(), "990", "Corrected statement", true, false));
    var replaced = service.save(input(changed.revision(), baseline, List.of())).plan();
    assertEquals("REVIEWED", replaced.evidenceState());
    assertEquals(99000, replaced.inputs().baseline().cashMinor());
    ledger.create(
        new LedgerService.AccountInput(
            "Synthetic debt", "DEBT", today, "-5000", "Synthetic debt statement"));
    assertEquals("REVIEWED", service.get().plan().evidenceState());
  }

  @Test
  void overdueIncomeIsNotAssumedReceivedAndOldEvidenceIsFlagged() {
    var p =
        service
            .save(
                input(
                    0,
                    manual(today.minusDays(8), "1000", "0"),
                    List.of(
                        event(today.minusDays(2), "INCOME", "EXPECTED", "500"),
                        event(today.minusDays(1), "EXPENSE", "EXPECTED", "100"))))
            .plan();
    assertEquals("STALE", p.evidenceState());
    assertEquals(0, p.projection().appliedIncomeMinor());
    assertEquals(90000, p.projection().closingMinor());
    assertTrue(p.warnings().stream().anyMatch(s -> s.contains("overdue expectation")));
  }

  @Test
  void correctionsRequireCurrentRevisionAndPreserveFrozenEvidence() {
    var e = event(today.plusDays(1), "EXPENSE", "EXPECTED", "100");
    var p = service.save(input(0, manual(today, "1000", "0"), List.of(e))).plan();
    var corrected =
        new ForecastService.EventInput(
            e.id(), e.date(), e.label(), e.direction(), e.certainty(), "90", "Corrected invoice");
    var next = service.save(input(p.revision(), null, List.of(corrected))).plan();
    assertEquals(p.revision() + 1, next.revision());
    assertEquals(p.inputs().baseline(), next.inputs().baseline());
    assertEquals(9000, next.inputs().events().get(0).amountMinor());
    assertThrows(
        IllegalArgumentException.class, () -> service.save(input(p.revision(), null, List.of(e))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.compare(
                new ForecastService.WhatIf(
                    p.revision(), "1", today.plusDays(1), null, null, false)));
  }

  @Test
  void rejectsDuplicateOccurrencesInvalidDatesAmountsAndMissingChoices() {
    var e = event(today.plusDays(1), "EXPENSE", "EXPECTED", "100");
    assertThrows(
        IllegalArgumentException.class,
        () -> service.save(input(0, manual(today, "1000", "0"), List.of(e, e))));
    for (String amount : List.of("-1", "0", "1.001", "1e3", "10000000001", "NaN")) {
      var bad =
          new ForecastService.EventInput(
              e.id(), e.date(), e.label(), e.direction(), e.certainty(), amount, e.source());
      assertThrows(
          IllegalArgumentException.class,
          () -> service.save(input(0, manual(today, "1000", "0"), List.of(bad))),
          amount);
    }
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.save(
                input(
                    0,
                    manual(today, "1000", "0"),
                    List.of(event(today, "EXPENSE", "EXPECTED", "1")))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.save(
                new ForecastService.Input(
                    0L,
                    "Horizon",
                    manual(today, "1000", "0"),
                    today.plusDays(91),
                    "0",
                    List.of(),
                    true)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.save(
                new ForecastService.Input(
                    0L,
                    "Missing review",
                    manual(today, "1000", "0"),
                    today.plusDays(5),
                    "0",
                    List.of(),
                    null)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.save(
                input(
                    0,
                    new ForecastService.BaselineInput("LEDGER", today, "100", null, null),
                    List.of())));
  }

  @Test
  void httpIsAuthenticatedAndRejectsCoercionUnknownFieldsAndOversize() throws Exception {
    mvc.perform(get("/api/forecast")).andExpect(status().isUnauthorized());
    mvc.perform(post("/api/forecast").cookie(cookie).content("{}"))
        .andExpect(status().isForbidden());
    var valid = json.valueToTree(input(0, manual(today, "1000", "0"), List.of()));
    for (String bad :
        List.of(
            valid.toString().replace("\"revision\":0", "\"revision\":0.5"),
            valid.toString().replace("\"revision\":0", "\"revision\":\"0\""),
            valid.toString().replace("\"cashEuro\":\"1000\"", "\"cashEuro\":1000"),
            valid.toString().replace("\"acknowledged\":true", "\"acknowledged\":\"true\""),
            valid.toString().replace("\"acknowledged\":true", "\"acknowledged\":1"),
            "null",
            "{\"unknown\":true}",
            "x".repeat(262145))) {
      mvc.perform(
              post("/api/forecast")
                  .cookie(cookie)
                  .header("X-Workspace-Request", "1")
                  .contentType("application/json")
                  .content(bad))
          .andExpect(status().isBadRequest());
    }
    mvc.perform(
            post("/api/forecast")
                .cookie(cookie)
                .header("X-Workspace-Request", "1")
                .contentType("application/json")
                .content(valid.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.plan.revision").value(1));
  }

  @Test
  void encryptedV3RecoveryRetainsPlanAndV2V1RemainReadable() throws Exception {
    var p =
        service
            .save(
                input(
                    0,
                    manual(today, "1000", "20"),
                    List.of(event(today.plusDays(2), "INCOME", "EXPECTED", "500"))))
            .plan();
    String password = "separate-synthetic-backup-passphrase";
    byte[] backup = backups.exportBackup(password);
    byte[] plain = BackupCrypto.decrypt(backup, password);
    var archive = json.readValue(plain, WorkspaceBackupService.Archive.class);
    Arrays.fill(plain, (byte) 0);
    assertEquals(7, archive.version());
    assertTrue(archive.tables().containsKey("FORECAST_PLANS"));
    clear();
    backups.restore(backup, password, "RESTORE INTO EMPTY WORKSPACE");
    assertEquals(p.inputs(), service.get().plan().inputs());
    var v2 = new LinkedHashMap<>(com.householdfinance.workspace.LegacyArchiveFixture.tables(archive,2));
    v2.remove("FORECAST_PLANS");
    v2.remove("DECISION_PLANS");
    clear();
    backups.restore(
        BackupCrypto.encrypt(
            json.writeValueAsBytes(new WorkspaceBackupService.Archive(2, archive.createdAt(), v2)),
            password),
        password,
        "RESTORE INTO EMPTY WORKSPACE");
    assertNull(service.get().plan());
    var v1 = new LinkedHashMap<>(v2);
    v1.keySet().removeIf(t -> t.startsWith("LEDGER_"));
    clear();
    backups.restore(
        BackupCrypto.encrypt(
            json.writeValueAsBytes(new WorkspaceBackupService.Archive(1, archive.createdAt(), v1)),
            password),
        password,
        "RESTORE INTO EMPTY WORKSPACE");
    assertNull(service.get().plan());
  }

  @Test
  void concurrentHttpCorrectionsCannotOverwriteEachOther() throws Exception {
    var p = service.save(input(0, manual(today, "1000", "0"), List.of())).plan();
    String body =
        json.writeValueAsString(
            input(
                p.revision(),
                null,
                List.of(event(today.plusDays(1), "EXPENSE", "EXPECTED", "10"))));
    var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
    try {
      var barrier = new java.util.concurrent.CyclicBarrier(2);
      java.util.concurrent.Callable<Integer> request =
          () -> {
            barrier.await();
            return mvc.perform(
                    post("/api/forecast")
                        .cookie(cookie)
                        .header("X-Workspace-Request", "1")
                        .contentType("application/json")
                        .content(body))
                .andReturn()
                .getResponse()
                .getStatus();
          };
      var a = executor.submit(request);
      var b = executor.submit(request);
      var statuses = new ArrayList<>(List.of(a.get(), b.get()));
      Collections.sort(statuses);
      assertEquals(List.of(200, 400), statuses);
      assertEquals(p.revision() + 1, service.get().plan().revision());
    } finally {
      executor.shutdownNow();
    }
  }
}
