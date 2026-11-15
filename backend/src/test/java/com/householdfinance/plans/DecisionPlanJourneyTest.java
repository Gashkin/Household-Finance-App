package com.householdfinance.plans;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import com.householdfinance.workspace.*;
import jakarta.servlet.http.Cookie;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "workspace.password=synthetic-plans-workspace-password",
      "spring.datasource.url=jdbc:h2:mem:decision-journey;DB_CLOSE_DELAY=-1"
    })
@AutoConfigureMockMvc
class DecisionPlanJourneyTest {
  @Autowired DecisionPlanService service;
  @Autowired DecisionPlanRepository repository;
  @Autowired WorkspaceBackupService backups;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;
  @Autowired MockMvc mvc;
  final LocalDate today = DecisionPlanService.today();
  Cookie cookie;

  @BeforeEach
  void reset() throws Exception {
    clear();
    var r =
        mvc.perform(
                post("/api/workspace/unlock")
                    .header("X-Workspace-Request", "1")
                    .contentType("application/json")
                    .content("{\"password\":\"synthetic-plans-workspace-password\"}"))
            .andExpect(status().isNoContent())
            .andReturn()
            .getResponse();
    cookie =
        new Cookie(
            "workspace_session", r.getHeader("Set-Cookie").split(";", 2)[0].split("=", 2)[1]);
  }

  void clear() {
    var tables = new ArrayList<>(backups.counts().keySet());
    Collections.reverse(tables);
    tables.forEach(t -> jdbc.update("DELETE FROM " + t));
  }

  DecisionPlanService.Content content(String summary) {
    return new DecisionPlanService.Content(
        "Synthetic loan decision",
        "Reduce interest while keeping a buffer",
        List.of(
            new DecisionPlanService.Evidence(
                "statement", "Synthetic statement", today, summary, "Rate may change")),
        List.of(
            new DecisionPlanService.Option(
                "keep", "Keep cash", "Higher interest, larger buffer", "Rate may change"),
            new DecisionPlanService.Option(
                "repay", "Repay extra", "Lower interest, smaller buffer", "Check lender penalty")));
  }

  DecisionPlanService.Choose choice(long revision) {
    return new DecisionPlanService.Choose(
        revision,
        "repay",
        "Buffer remains sufficient",
        today.plusDays(7),
        List.of(
            new DecisionPlanService.ActionInput(
                "confirm", "Confirm lender terms", today.plusDays(1))),
        true);
  }

  @Test
  void completeGoalChoiceFollowUpAndRevisionPreserveHistory() {
    var p = service.create(content("Balance estimate"));
    assertEquals("PROPOSAL", p.status());
    var d = service.choose(p.id(), choice(p.revision()));
    assertEquals("DECIDED", d.status());
    var done =
        service.complete(
            p.id(),
            new DecisionPlanService.Complete(
                d.revision(), "confirm", "Synthetic lender confirmed no fee"));
    assertNotNull(done.data().actions().getFirst().completedAt());
    var revised =
        service.edit(
            p.id(),
            new DecisionPlanService.Edit(
                done.revision(), content("New statement changes cash assumptions")));
    assertEquals("PROPOSAL", revised.status());
    assertNull(revised.data().decision());
    assertTrue(revised.data().actions().isEmpty());
    assertEquals(
        "Synthetic lender confirmed no fee",
        revised.data().history().get(2).actions().getFirst().outcome());
    assertEquals(
        "Balance estimate",
        revised.data().history().get(1).content().evidence().getFirst().summary());
    var again = service.choose(p.id(), choice(revised.revision()));
    assertEquals(5, again.data().history().size());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.complete(
                p.id(),
                new DecisionPlanService.Complete(done.revision(), "confirm", "Stale action")));
  }

  @Test
  void decisionsRequireEvidenceOptionsConfirmationAndDates() {
    var p = service.create(content("Estimate"));
    var c = choice(p.revision());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.choose(
                p.id(),
                new DecisionPlanService.Choose(
                    c.revision(), c.optionId(), c.rationale(), c.reviewOn(), c.actions(), false)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.choose(
                p.id(),
                new DecisionPlanService.Choose(
                    c.revision(), "missing", c.rationale(), c.reviewOn(), c.actions(), true)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.choose(
                p.id(),
                new DecisionPlanService.Choose(
                    c.revision(),
                    c.optionId(),
                    c.rationale(),
                    today.minusDays(1),
                    c.actions(),
                    true)));
    var noEvidence =
        service.create(
            new DecisionPlanService.Content(
                "Unverified", "Synthetic goal", List.of(), content("x").options()));
    assertThrows(
        IllegalArgumentException.class,
        () -> service.choose(noEvidence.id(), choice(noEvidence.revision())));
    var d =
        service.choose(
            p.id(),
            new DecisionPlanService.Choose(
                c.revision(), c.optionId(), c.rationale(), today, c.actions(), true));
    assertEquals("REVIEW_DUE", d.status());
    assertThrows(
        IllegalArgumentException.class, () -> service.choose(p.id(), choice(d.revision())));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.complete(
                p.id(),
                new DecisionPlanService.Complete(d.revision(), "unknown", "Synthetic outcome")));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.complete(
                p.id(), new DecisionPlanService.Complete(d.revision(), "confirm", " ")));
    assertEquals(2, service.get(p.id()).data().history().size());
  }

  @Test
  void duplicateFutureAndMissingEvidenceAreRejectedWithoutMutation() {
    var c = content("Evidence");
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.create(
                new DecisionPlanService.Content(
                    c.title(),
                    c.goal(),
                    List.of(c.evidence().getFirst(), c.evidence().getFirst()),
                    c.options())));
    var future =
        new DecisionPlanService.Evidence(
            "x", "Synthetic", today.plusDays(1), "Future statement", "Uncertain");
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.create(
                new DecisionPlanService.Content(
                    c.title(), c.goal(), List.of(future), c.options())));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.create(
                new DecisionPlanService.Content(c.title(), c.goal(), null, c.options())));
    assertTrue(service.list().isEmpty());
  }

  JsonNode debtInput() throws Exception {
    return json.readTree(
        "{\"balanceMinor\":1000000,\"annualRatePercent\":\"8\",\"monthlyPaymentMinor\":30000,\"extraPaymentMinor\":200000,\"liquidCashMinor\":800000,\"minimumReserveMinor\":400000}");
  }

  @Test
  void debtPlanUsesAuthoritativeFrozenReportNeverMutatesMoney() throws Exception {
    var before = backups.counts();
    var p = service.fromDebt(debtInput());
    assertNotNull(p.data().report());
    assertEquals("debt-amortisation-v1", p.data().report().calculationVersion());
    assertTrue(p.data().content().options().get(1).impact().contains("cash remaining EUR 6000.00"));
    var r =
        service.edit(
            p.id(), new DecisionPlanService.Edit(p.revision(), content("Corrected assumption")));
    assertEquals(p.data().report(), r.data().report());
    assertNull(r.data().decision());
    backups
        .counts()
        .forEach(
            (table, count) -> {
              if (!table.equals("DECISION_PLANS")) assertEquals(before.get(table), count);
            });
    var bad = (com.fasterxml.jackson.databind.node.ObjectNode) debtInput();
    bad.put("balanceMinor", 1.5);
    assertThrows(IllegalArgumentException.class, () -> service.fromDebt(bad));
  }

  @Test
  void apiAuthenticationStrictTypesNullsAndBounds() throws Exception {
    mvc.perform(get("/api/plans")).andExpect(status().isUnauthorized());
    mvc.perform(post("/api/plans").cookie(cookie).content("{}")).andExpect(status().isForbidden());
    var p = service.create(content("Synthetic"));
    String good = json.writeValueAsString(choice(p.revision()));
    for (String bad :
        List.of(
            "null",
            "{}",
            good.replace("\"revision\":1", "\"revision\":1.5"),
            good.replace("\"revision\":1", "\"revision\":\"1\""),
            good.replace("\"confirmed\":true", "\"confirmed\":\"true\""),
            good.replace("\"confirmed\":true", "\"confirmed\":1"),
            good.replace("\"optionId\":\"repay\"", "\"optionId\":5"),
            good.replace("\"confirmed\":true", "\"confirmed\":true,\"confirmed\":true"),
            "x".repeat(262145))) {
      mvc.perform(
              post("/api/plans/" + p.id() + "/decision")
                  .cookie(cookie)
                  .header("X-Workspace-Request", "1")
                  .contentType("application/json")
                  .content(bad))
          .andExpect(status().isBadRequest());
    }
    mvc.perform(
            post("/api/plans/" + p.id() + "/decision")
                .cookie(cookie)
                .header("X-Workspace-Request", "1")
                .contentType("application/json")
                .content(good))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.revision").value(2));
    assertEquals(2, service.get(p.id()).data().history().size());
  }

  @Test
  void encryptedV4RestoresFullHistoryAndPriorV3RemainsReadable() throws Exception {
    var p = service.create(content("Synthetic"));
    p = service.choose(p.id(), choice(p.revision()));
    p =
        service.complete(
            p.id(), new DecisionPlanService.Complete(p.revision(), "confirm", "Synthetic outcome"));
    var expected = p.data();
    String password = "separate-synthetic-backup-passphrase";
    byte[] backup = backups.exportBackup(password);
    var archive =
        json.readValue(
            BackupCrypto.decrypt(backup, password), WorkspaceBackupService.Archive.class);
    assertEquals(4, archive.version());
    clear();
    backups.restore(backup, password, "RESTORE INTO EMPTY WORKSPACE");
    assertEquals(expected, service.get(p.id()).data());
    var tables = new LinkedHashMap<>(archive.tables());
    tables.remove("DECISION_PLANS");
    clear();
    backups.restore(
        BackupCrypto.encrypt(
            json.writeValueAsBytes(
                new WorkspaceBackupService.Archive(3, archive.createdAt(), tables)),
            password),
        password,
        "RESTORE INTO EMPTY WORKSPACE");
    assertTrue(service.list().isEmpty());
  }

  @Test
  void concurrentCorrectionsCannotLoseARecordedDecision() throws Exception {
    var p = service.create(content("Synthetic"));
    String body = json.writeValueAsString(choice(p.revision()));
    var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
    var barrier = new java.util.concurrent.CyclicBarrier(2);
    try {
      java.util.concurrent.Callable<Integer> call =
          () -> {
            barrier.await();
            return mvc.perform(
                    post("/api/plans/" + p.id() + "/decision")
                        .cookie(cookie)
                        .header("X-Workspace-Request", "1")
                        .contentType("application/json")
                        .content(body))
                .andReturn()
                .getResponse()
                .getStatus();
          };
      var a = executor.submit(call);
      var b = executor.submit(call);
      var results = new ArrayList<>(List.of(a.get(), b.get()));
      Collections.sort(results);
      assertEquals(200, results.getFirst());
      assertTrue(results.getLast() == 400 || results.getLast() == 409);
      assertEquals(2, service.get(p.id()).data().history().size());
    } finally {
      executor.shutdownNow();
    }
  }
}
