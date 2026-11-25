package com.householdfinance.contracts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.householdfinance.access.AccessContext;
import com.householdfinance.ledger.ActivityReviewService;
import com.householdfinance.ledger.CommitmentService;
import com.householdfinance.ledger.LedgerService;
import com.householdfinance.workspace.WorkspaceFilter;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static com.householdfinance.contracts.ContractReview.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "workspace.password=synthetic-contract-owner-password",
    "workspace.time-zone=Europe/Dublin",
    "spring.datasource.url=jdbc:h2:mem:contract-journey;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
class ContractsJourneyTest {
  private static final String OWNER = "synthetic-contract-owner-password";
  private static final String MEMBER = "synthetic-contract-member-password";
  private static final String API = "/api/contracts";
  private static final LocalDate JULY = LocalDate.of(2026, 7, 1);
  private static final DatedEvidence UNKNOWN = new DatedEvidence(Certainty.UNKNOWN, null, null, null);

  static class MutableClock extends Clock {
    volatile Instant now = Instant.parse("2026-06-30T23:30:00Z");
    @Override public ZoneId getZone() { return ZoneOffset.UTC; }
    @Override public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
    @Override public Instant instant() { return now; }
  }

  @TestConfiguration
  static class TestClock {
    @Bean MutableClock testClock() { return new MutableClock(); }
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate jdbc;
  @Autowired WorkspaceFilter sessions;
  @Autowired MutableClock clock;
  @Autowired ContractAlerts scheduler;
  @Autowired CommitmentService commitments;
  @Autowired LedgerService ledger;
  @Autowired ActivityReviewService review;
  Cookie owner;

  @BeforeEach
  void start() throws Exception {
    clock.now = Instant.parse("2026-06-30T23:30:00Z");
    sessions.revokeAll();
    for (String table : List.of("CONTRACT_REVIEW_ALERTS", "HOUSEHOLD_CONTRACTS", "LEDGER_REVIEW_RECORDS",
        "LEDGER_AUDIT", "LEDGER_ENTRIES", "LEDGER_ACCOUNTS", "ACCESS_AUDIT", "HOUSEHOLD_USERS")) {
      jdbc.update("DELETE FROM " + table);
    }
    owner = login("owner", OWNER);
  }

  @Test
  void verifiedCutoffAndLocalMidnightDriveDeduplicatedDurableSnoozeAndDismissal() throws Exception {
    clock.now = Instant.parse("2026-06-30T22:59:00Z");
    var input = createInput("Synthetic broadband", terms("Synthetic ISP", JULY.minusMonths(2),
        new DatedEvidence(Certainty.ESTIMATED, JULY.plusDays(2), "Synthetic order", null), verified(JULY.plusDays(30)),
        null, true, null));
    var created = call("POST", API, owner, input, 201);
    call("POST", API, owner, input, 409);
    String id = id(created);
    String term = term(created);
    String alert = API + "/alerts/" + id + "/" + term;
    var earlier = notification(owner);
    assertEquals("UPCOMING", earlier.path("stage").asText());
    assertFalse(earlier.path("unread").asBoolean());
    assertEquals(JULY.toString(), earlier.path("target").asText());
    assertEquals(JULY.plusDays(30).toString(), earlier.path("cutoff").asText());

    clock.now = Instant.parse("2026-06-30T23:00:00Z");
    scheduler.evaluate();
    scheduler.evaluate();
    var due = notification(owner);
    assertEquals("DUE", due.path("stage").asText());
    assertEquals(JULY.toString(), call("GET", API + "/alerts", owner, null, 200).path("today").asText());
    assertTrue(due.path("unread").asBoolean());
    assertEquals(1, countAlerts());
    String timing = due.path("timing").asText();
    call("POST", alert, owner, new ContractAlerts.Change(ContractAlerts.Action.MARK_READ, null, timing), 204);
    scheduler.evaluate();
    assertFalse(notification(owner).path("unread").asBoolean());
    call("POST", alert, owner, new ContractAlerts.Change(ContractAlerts.Action.SNOOZE, JULY, timing), 400);
    call("POST", alert, owner, new ContractAlerts.Change(ContractAlerts.Action.SNOOZE, JULY.plusDays(2), timing), 204);

    clock.now = Instant.parse("2026-07-01T23:05:00Z");
    assertEquals(JULY.plusDays(2).toString(), notification(login("owner", OWNER)).path("snoozedUntil").asText());
    clock.now = Instant.parse("2026-07-02T23:05:00Z");
    owner = login("owner", OWNER);
    scheduler.evaluate();
    assertEquals("OVERDUE", notification(owner).path("stage").asText());
    assertTrue(notification(owner).path("unread").asBoolean());
    call("POST", alert, owner, new ContractAlerts.Change(ContractAlerts.Action.DISMISS, null, timing), 204);
    scheduler.evaluate();
    assertTrue(notification(owner).path("dismissed").asBoolean());
    assertEquals(1, countAlerts());

    var revised = terms("Synthetic ISP", JULY.minusMonths(2), UNKNOWN, verified(JULY.plusDays(35)),
        JULY, true, null);
    call("PUT", API + "/" + id + "/terms/" + term, owner,
        new ContractsService.Edit(1L, "Synthetic broadband", null, revised), 200);
    assertTrue(notification(owner).path("unread").asBoolean());
    assertFalse(notification(owner).path("dismissed").asBoolean());
    call("POST", alert, owner, new ContractAlerts.Change(ContractAlerts.Action.MARK_READ, null, timing), 400);
    assertEquals(1, countAlerts());
    assertEquals(1, call("GET", API, owner, null, 200).size());
  }

  @Test
  void rollingCancellationIntentAndFutureOutcomesPreserveHistoryAndCashFlow() throws Exception {
    var bill = seedBill();
    var created = call("POST", API, owner, createInput("Synthetic streaming", terms("Synthetic Video",
        JULY.minusMonths(1), UNKNOWN, UNKNOWN, JULY.minusDays(1), true, bill)), 201);
    String id = id(created);
    String root = API + "/" + id;
    String path = root + "/terms/" + term(created);
    call("POST", path + "/attempt", owner, new ContractsService.AttemptInput(1L, Decision.CANCEL, JULY, "Synthetic email"), 400);
    var decision = new ContractsService.DecisionInput(1L, Decision.CANCEL, JULY, "Synthetic low usage");
    var accepted = call("POST", path + "/decision", owner, decision, 200);
    assertEquals("ACTIVE", accepted.at("/summary/status").asText());
    assertTrue(accepted.at("/terms/0/term/confirmation").isNull());
    assertEquals("OVERDUE", notification(owner).path("stage").asText());
    call("POST", path + "/decision", owner, decision, 409);
    call("POST", path + "/attempt", owner, new ContractsService.AttemptInput(2L, Decision.CANCEL, JULY, "Synthetic portal request"), 200);
    var nextStart = JULY.plusDays(1);
    var replacement = terms("Synthetic Replacement", nextStart, UNKNOWN, UNKNOWN, nextStart, true, bill);
    var invalid = new ContractsService.ConfirmInput(3L, Outcome.RENEWED, JULY, nextStart, "Synthetic evidence", replacement);
    call("POST", path + "/confirmation", owner, invalid, 400);
    assertEquals(3, call("GET", root, owner, null, 200).path("revision").asLong());

    var switched = call("POST", path + "/confirmation", owner,
        new ContractsService.ConfirmInput(3L, Outcome.SWITCHED, JULY, nextStart, "Synthetic provider letter", replacement), 200);
    assertEquals("Synthetic Video", switched.at("/summary/provider").asText());
    assertEquals("SWITCHED", switched.at("/terms/0/term/confirmation/outcome").asText());
    assertEquals(2, switched.path("terms").size());
    String nextPath = root + "/terms/" + switched.path("latestTermId").asText();

    clock.now = Instant.parse("2026-07-01T23:10:00Z");
    owner = login("owner", OWNER);
    var now = call("GET", root, owner, null, 200);
    assertEquals("Synthetic Replacement", now.at("/summary/provider").asText());
    assertEquals("DUE", notification(owner).path("stage").asText());
    call("POST", nextPath + "/confirmation", owner,
        new ContractsService.ConfirmInput(4L, Outcome.CANCELLED, nextStart, nextStart.plusDays(1), "Synthetic written cancellation", null), 200);
    assertEquals("ACTIVE", call("GET", root, owner, null, 200).at("/summary/status").asText());

    clock.now = Instant.parse("2026-07-02T23:10:00Z");
    owner = login("owner", OWNER);
    assertEquals("CLOSED", call("GET", root, owner, null, 200).at("/summary/status").asText());
    var reactivated = call("POST", root + "/reactivate", owner, new ContractsService.Reactivate(5L,
        terms("Synthetic Replacement", JULY.plusDays(2), UNKNOWN, UNKNOWN, null, false, null)), 200);
    assertEquals("ACTIVE", reactivated.at("/summary/status").asText());
    assertEquals(3, reactivated.path("terms").size());
    assertEquals(1, reactivated.at("/terms/0/term/decisions").size());
    assertEquals(1, reactivated.at("/terms/0/term/attempts").size());
    assertEquals(2, call("GET", API + "/alerts", owner, null, 200).path("notifications").size());
    assertEquals(1, call("GET", "/api/forecast/commitments", owner, null, 200).at("/state/revision").asLong());
    assertEquals(1, call("GET", "/api/forecast/commitments", owner, null, 200).at("/state/schedules").size());
    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM LEDGER_ENTRIES", Integer.class));
  }

  @Test
  void privateAndSharedContractsEnforceOwnershipAcrossDeepLinksAlertsExportAndReferences() throws Exception {
    var reader = enrol("syntheticreader", "ADMIN", "READER");
    var editor = enrol("syntheticeditor", "EDITOR");
    var bill = seedBill();
    var created = call("POST", API, owner, createInput("Synthetic private subscription",
        terms("Synthetic service", JULY, UNKNOWN, UNKNOWN, JULY, true, bill)), 201);
    String path = API + "/" + id(created);
    String alertPath = API + "/alerts/" + id(created) + "/" + term(created);
    for (Cookie member : List.of(reader, editor)) {
      assertTrue(call("GET", API, member, null, 200).isEmpty());
      assertTrue(call("GET", API + "/export", member, null, 200).isEmpty());
      assertTrue(call("GET", API + "/alerts", member, null, 200).path("notifications").isEmpty());
      call("GET", path, member, null, 404);
      call("POST", alertPath, member, new ContractAlerts.Change(ContractAlerts.Action.MARK_READ, null, "synthetic"), 404);
    }
    call("GET", API + "/links", reader, null, 403);
    call("GET", API + "/links/" + bill.accountId() + "/transactions", editor, null, 400);

    call("POST", path + "/sharing", owner, new ContractsService.Sharing(1L, true), 200);
    assertEquals(3, countAlerts());
    assertTrue(call("GET", path, reader, null, 200).at("/terms/0/term/terms/references").isNull());
    var exported = call("GET", API + "/export", reader, null, 200);
    assertEquals(1, exported.size());
    assertFalse(exported.toString().contains(bill.transactionId()));
    assertFalse(exported.toString().contains(bill.billId()));
    assertEquals(bill.billId(), call("GET", path, owner, null, 200).at("/terms/0/term/terms/references/billId").asText());
    call("PUT", path + "/terms/" + term(created), editor, new ContractsService.Edit(2L, "Synthetic takeover", null, null), 403);
    String timing = notification(reader).path("timing").asText();
    call("POST", alertPath, reader, new ContractAlerts.Change(ContractAlerts.Action.DISMISS, null, timing), 204);
    assertTrue(notification(reader).path("dismissed").asBoolean());
    assertTrue(notification(owner).path("unread").asBoolean());

    call("POST", path + "/sharing", owner, new ContractsService.Sharing(2L, false), 200);
    assertEquals(1, countAlerts());
    call("GET", path, reader, null, 401);
    var signedInAgain = login("syntheticreader", MEMBER);
    call("GET", path, signedInAgain, null, 404);
    assertTrue(call("GET", API + "/export", signedInAgain, null, 200).isEmpty());
    assertTrue(call("GET", API + "/alerts", signedInAgain, null, 200).path("notifications").isEmpty());
  }

  private JsonNode notification(Cookie cookie) throws Exception {
    return call("GET", API + "/alerts", cookie, null, 200).at("/notifications/0");
  }

  private int countAlerts() {
    return jdbc.queryForObject("SELECT COUNT(*) FROM CONTRACT_REVIEW_ALERTS", Integer.class);
  }

  private References seedBill() {
    AccessContext.enter(new AccessContext.Actor("owner", "owner", Set.of("EDITOR", "READER", "ADMIN"), 0, true));
    try {
      var date = LocalDate.now(ZoneId.of("Europe/Dublin"));
      var account = ledger.create(new LedgerService.AccountInput("Synthetic bill cash", "CASH", date, "100.00", "Synthetic statement"));
      var category = review.category(null, new ActivityReviewService.CategoryInput(null, "Synthetic home",
          null, false, "0.00", null, "Synthetic category"));
      var bill = UUID.randomUUID().toString();
      var rule = new CommitmentService.Rule("Synthetic recurring plan", account.id(), category.id(), "EXPENSE", "EXPECTED",
          "FIXED", "10.00", "MONTHLY", date, null, null, 0, null);
      commitments.batch(new CommitmentService.Batch(0L, UUID.randomUUID().toString(), "Synthetic bills",
          List.of(new CommitmentService.Edit(bill, 0L, date, false, rule))));
      ledger.manual(account.id(), new LedgerService.Manual(0L,
          new LedgerService.Row("synthetic-contract-paid", date, "Synthetic payment", "-10.00", "BOOKED", "ACTIVITY"),
          "Synthetic statement", true));
      return new References(bill, account.id(), ledger.activity(account.id()).getFirst().id);
    } finally {
      AccessContext.clear();
    }
  }

  private static DatedEvidence verified(LocalDate date) {
    return new DatedEvidence(Certainty.VERIFIED, date, "Synthetic signed agreement", null);
  }

  private static Terms terms(String provider, LocalDate start, DatedEvidence end, DatedEvidence notice,
      LocalDate review, boolean enabled, References references) {
    return new Terms(provider, "Synthetic plan", TermType.ROLLING, start, enabled, end, notice, UNKNOWN,
        UNKNOWN, UNKNOWN, review, null, null, Frequency.MONTHLY, null, new Price("10.00", "EUR", start),
        null, "Synthetic notice rules", references);
  }

  private static ContractsService.Create createInput(String name, Terms terms) {
    return new ContractsService.Create(UUID.randomUUID().toString().replace("-", ""), name, Kind.SUBSCRIPTION,
        new Assessment(Usage.LOW, Closing.CONSIDER_CANCELLING, "Synthetic review"), terms);
  }

  private static String id(JsonNode detail) { return detail.at("/summary/id").asText(); }
  private static String term(JsonNode detail) { return detail.path("latestTermId").asText(); }

  private Cookie enrol(String username, String... roles) throws Exception {
    var invite = call("POST", "/api/access/invitations", owner, Map.of("username", username), 200);
    call("POST", "/api/access/accept", null, Map.of("code", invite.path("code").asText(), "password", MEMBER, "accepted", true), 200);
    var user = java.util.stream.StreamSupport.stream(call("GET", "/api/access/users", owner, null, 200).spliterator(), false)
        .filter(value -> username.equals(value.path("username").asText())).findFirst().orElseThrow();
    call("PUT", "/api/access/users/" + user.path("id").asText(), owner,
        Map.of("revision", user.path("revision").asLong(), "roles", roles, "state", "ACTIVE"), 200);
    return login(username, MEMBER);
  }

  private Cookie login(String username, String password) throws Exception {
    var result = mvc.perform(MockMvcRequestBuilders.post("/api/workspace/unlock").header("X-Workspace-Request", "1")
        .contentType("application/json").content(json.writeValueAsBytes(Map.of("username", username, "password", password))))
        .andExpect(status().isNoContent()).andReturn().getResponse();
    return new Cookie("workspace_session", result.getHeader("Set-Cookie").split(";", 2)[0].split("=", 2)[1]);
  }

  private JsonNode call(String method, String path, Cookie cookie, Object body, int expected) throws Exception {
    var request = MockMvcRequestBuilders.request(org.springframework.http.HttpMethod.valueOf(method), path)
        .header("X-Workspace-Request", "1");
    if (cookie != null) request.cookie(cookie);
    if (body != null) request.contentType("application/json").content(json.writeValueAsBytes(body));
    var response = mvc.perform(request).andExpect(status().is(expected)).andReturn().getResponse();
    if (response.getContentAsByteArray().length == 0) return json.nullNode();
    if (response.getContentType() != null && response.getContentType().startsWith("text/plain")) {
      return json.getNodeFactory().textNode(response.getContentAsString());
    }
    return json.readTree(response.getContentAsByteArray());
  }
}
