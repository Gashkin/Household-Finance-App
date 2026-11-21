package com.householdfinance.ledger;

import static org.junit.jupiter.api.Assertions.*;

import com.householdfinance.access.*;
import com.householdfinance.service.DebtScenarioService;
import com.householdfinance.workspace.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(
    properties = {
      "workspace.password=synthetic-debt-password",
      "spring.datasource.url=jdbc:h2:mem:debt;DB_CLOSE_DELAY=-1"
    })
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@org.junit.jupiter.api.extension.ExtendWith(OwnerFixture.class)
class DebtTest {
  @Autowired DebtService debts;
  @Autowired LedgerService ledger;
  @Autowired ActivityReviewService review;
  @Autowired CommitmentService commitments;

  @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
  WorkspaceBackupService backups;

  @Autowired JdbcTemplate jdbc;
  @Autowired org.springframework.test.web.servlet.MockMvc mvc;
  @Autowired com.fasterxml.jackson.databind.ObjectMapper json;
  LocalDate day = LocalDate.now(ZoneId.of("Europe/Dublin"));
  String cash, debt, id;

  @BeforeEach
  void reset() {
    var tables = new ArrayList<>(backups.counts().keySet());
    Collections.reverse(tables);
    tables.forEach(t -> jdbc.update("DELETE FROM " + t));
    cash =
        ledger
            .create(
                new LedgerService.AccountInput(
                    "Synthetic repayment cash",
                    "CASH",
                    day.minusMonths(3),
                    "2000.00",
                    "Synthetic statement"))
            .id();
    debt =
        ledger
            .create(
                new LedgerService.AccountInput(
                    "Synthetic lender",
                    "DEBT",
                    day.minusMonths(3),
                    "-1000.00",
                    "Synthetic statement"))
            .id();
    id = UUID.randomUUID().toString();
  }

  DebtService.Terms terms(String kind, String rate) {
    return new DebtService.Terms(
        debt,
        "Synthetic " + kind,
        kind,
        cash,
        "5000.00",
        "100.00",
        "0.00",
        day.plusYears(10),
        "1000.00",
        "0.00",
        "Synthetic lender permits stated immediate extra; monthly model differs from daily accrual",
        List.of(new DebtService.Rate(day.minusMonths(3), rate)));
  }

  DebtService.Statement statement(
      boolean complete,
      String opening,
      String borrowing,
      String interest,
      String fees,
      String paid,
      String balance,
      String minimum) {
    return new DebtService.Statement(
        UUID.randomUUID().toString(),
        day.minusMonths(1),
        day.minusDays(1),
        day.plusDays(10),
        "Synthetic lender statement",
        complete,
        opening,
        borrowing,
        interest,
        fees,
        paid,
        balance,
        minimum,
        null);
  }

  DebtService.Change change(long rev, DebtService.Terms t, DebtService.Statement s) {
    return new DebtService.Change(
        rev, UUID.randomUUID().toString(), "Synthetic reviewed lender evidence", id, t, s);
  }

  DebtService.Statement standard() {
    return statement(true, "1000.00", "0.00", "0.00", "0.00", "0.00", "1000.00", "25.00");
  }

  long revision(String account) {
    return ledger.position(day).accounts().stream()
        .filter(a -> a.id().equals(account))
        .findFirst()
        .orElseThrow()
        .revision();
  }

  LedgerEntry manual(String account, String amount, String status) {
    String reference = UUID.randomUUID().toString();
    ledger.manual(
        account,
        new LedgerService.Manual(
            revision(account),
            new LedgerService.Row(reference, day, "Synthetic activity", amount, status, "ACTIVITY"),
            "Synthetic statement",
            true));
    return ledger.activity(account).stream()
        .filter(e -> e.reference.equals(reference))
        .findFirst()
        .orElseThrow();
  }

  void settlement(String amount) {
    var a = manual(cash, "-" + amount, "BOOKED");
    var b = manual(debt, amount, "BOOKED");
    review.link(
        a.id,
        new ActivityReviewService.Link(
            revision(cash),
            b.id,
            revision(debt),
            "CARD_SETTLEMENT",
            "Synthetic paired settlement"));
  }

  @Test
  void completeStatementReconcilesAgainstHistoricalLedgerWithoutMovingMoney() {
    var before = ledger.position(day);
    ledger.reconcile(
        debt,
        day.minusDays(1),
        new LedgerService.Reconcile(
            0L, "-1000.00", "Synthetic reviewed closing evidence", true, false));
    var input = change(0, terms("CARD", "18.5"), standard());
    var saved = debts.change(input);
    assertEquals(saved, debts.change(input));
    assertEquals(1, debts.history().size());
    var row = debts.get().rows().getFirst();
    assertTrue(row.reconciled());
    assertEquals(0, row.statementDifferenceMinor());
    assertEquals(100000, row.outstandingMinor());
    assertEquals(100000, row.remainingPaymentMinor());
    assertEquals(before.cashMinor(), ledger.position(day).cashMinor());
    assertTrue(ledger.activity(debt).isEmpty());
    assertTrue(ledger.activity(cash).isEmpty());
    assertTrue(debts.operation(input.operationId()).get("completed"));
  }

  @Test
  void rejectsBrokenComponentsUnknownAccountsAndStaleWritesWithoutPartialAudit() {
    var initial = debts.get();
    long count = jdbc.queryForObject("SELECT COUNT(*) FROM LEDGER_AUDIT", Long.class);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            debts.change(
                change(
                    0,
                    terms("LOAN", "6"),
                    statement(true, "1000", "0", "0.01", "0", "0", "1000", "25"))));
    assertEquals(initial, debts.get());
    assertEquals(count, jdbc.queryForObject("SELECT COUNT(*) FROM LEDGER_AUDIT", Long.class));
    var input = change(0, terms("LOAN", "6"), standard());
    debts.change(input);
    var saved = debts.get();
    assertThrows(
        IllegalArgumentException.class, () -> debts.change(change(0, terms("LOAN", "7"), null)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            debts.change(
                new DebtService.Change(
                    1L, input.operationId(), "Changed retry", id, terms("LOAN", "6"), null)));
    assertEquals(saved, debts.get());
    assertEquals(1, debts.history().size());
  }

  @Test
  void incompleteStatementCanBeCorrectedWithHistoryAndNoFabricatedReconciliation() {
    var incomplete = statement(false, null, null, null, null, null, "900.00", null);
    debts.change(change(0, terms("CARD", "18"), incomplete));
    var row = debts.get().rows().getFirst();
    assertFalse(row.reconciled());
    assertEquals(-10000, row.statementDifferenceMinor());
    assertTrue(row.warnings().stream().anyMatch(n -> n.contains("Incomplete")));
    var corrected =
        new DebtService.Statement(
            UUID.randomUUID().toString(),
            incomplete.start(),
            incomplete.end(),
            incomplete.due(),
            "Synthetic corrected statement",
            true,
            "1000",
            "0",
            "0",
            "0",
            "0",
            "1000",
            "25",
            incomplete.id());
    debts.change(change(1, terms("CARD", "18"), corrected));
    assertEquals(2, debts.get().state().debts().getFirst().statements().size());
    assertEquals(0, debts.get().rows().getFirst().statementDifferenceMinor());
    assertFalse(debts.get().rows().getFirst().reconciled());
    assertEquals(-100000, ledger.position(day).debtSignedMinor());
    assertThrows(
        IllegalArgumentException.class,
        () -> debts.change(change(2, terms("CARD", "18"), standard())));
    assertEquals(2, debts.history().size());
  }

  @Test
  void creditBalancesAndOverdraftsNeverIncreaseAvailableCash() {
    String credit =
        ledger
            .create(
                new LedgerService.AccountInput(
                    "Synthetic lender credit",
                    "DEBT",
                    day.minusMonths(3),
                    "25.00",
                    "Synthetic statement"))
            .id();
    debt = credit;
    var t = terms("CARD", "0");
    debts.change(change(0, t, statement(true, "-25", "0", "0", "0", "0", "-25", "0")));
    assertEquals(200000, ledger.position(day).cashMinor());
    assertEquals(-2500, debts.get().rows().getFirst().outstandingMinor());
    assertEquals(0, debts.get().rows().getFirst().remainingPaymentMinor());
    assertTrue(debts.obligations(day, day.plusMonths(3), day).isEmpty());
    manual(cash, "-2100.00", "BOOKED");
    assertEquals(-10000, debts.get().rows().getFirst().paymentCashMinor());
    assertEquals(-10000, ledger.position(day).cashMinor());
  }

  @Test
  void pairedSettlementsAndPurchasesAffectSpendingAndCashOnlyOnceAndUnlinkRestoresReservation() {
    debts.change(change(0, terms("CARD", "18"), standard()));
    var purchase = manual(debt, "-50.00", "BOOKED");
    assertEquals(5000, ledger.position(day).spendingMinor());
    assertEquals(
        100000,
        commitments.outlook(day, day.plusMonths(1), false, true).household().closingMinor());
    settlement("200.00");
    assertEquals(5000, ledger.position(day).spendingMinor());
    assertEquals(180000, ledger.position(day).cashMinor());
    assertEquals(85000, debts.get().rows().getFirst().outstandingMinor());
    assertEquals(80000, debts.get().rows().getFirst().remainingPaymentMinor());
    assertEquals(
        100000,
        commitments.outlook(day, day.plusMonths(1), false, true).household().closingMinor());
    var credit =
        ledger.activity(debt).stream().filter(e -> e.amountMinor > 0).findFirst().orElseThrow();
    review.unlink(
        credit.id,
        new ActivityReviewService.Link(
            revision(debt),
            credit.linkedEntryId,
            revision(cash),
            "CARD_SETTLEMENT",
            "Synthetic review correction"));
    assertEquals(85000, debts.get().rows().getFirst().remainingPaymentMinor());
    assertEquals(
        95000, commitments.outlook(day, day.plusMonths(1), false, true).household().closingMinor());
    assertNotNull(purchase);
  }

  @Test
  void pendingOrUnlinkedPaymentsDoNotSilentlySettleStatementAndPendingCashReservedOnce() {
    debts.change(change(0, terms("CARD", "0"), standard()));
    manual(cash, "-50.00", "PENDING");
    manual(debt, "50.00", "PENDING");
    assertEquals(100000, debts.get().rows().getFirst().remainingPaymentMinor());
    assertEquals(195000, debts.get().rows().getFirst().paymentCashMinor());
    assertEquals(
        95000, commitments.outlook(day, day.plusMonths(1), false, true).household().closingMinor());
  }

  @Test
  void fixedComparisonReusesEngineAndRoundsInterestAtHalfCents() {
    debts.change(change(0, terms("LOAN", "6.006"), standard()));
    var c = debts.compare(new DebtService.Compare(1L, id, "100.00", "1950.00"));
    assertNotNull(c.fixedRateComparison());
    assertEquals(501, c.baseline().payments().getFirst().interestMinor());
    assertEquals(c.fixedRateComparison().baseline().interestMinor(), c.baseline().interestMinor());
    assertEquals(
        c.fixedRateComparison().withExtraPayment().interestMinor(), c.extra().interestMinor());
    assertEquals(5000, c.reserveShortfallMinor());
    assertEquals(190000, c.remainingCashMinor());
    assertTrue(ledger.activity(cash).isEmpty());
    assertEquals(200000, ledger.position(day).cashMinor());
    assertEquals(1, debts.history().size());
    assertEquals(1, DebtScenarioService.monthlyInterest(100, new BigDecimal("6")));
  }

  @Test
  void changedRatesFeesMaturityAndLenderConstraintsRemainExplicit() {
    var t = terms("MORTGAGE", "0");
    debts.change(change(0, t, standard()));
    var rates = new ArrayList<>(t.rates());
    rates.add(new DebtService.Rate(day.plusDays(20), "12"));
    var updated =
        new DebtService.Terms(
            debt,
            t.name(),
            t.kind(),
            cash,
            "0",
            "100",
            "1",
            day.plusYears(10),
            "150",
            "5",
            "Synthetic capped extra plus fixed fee",
            rates);
    debts.change(change(1, updated, null));
    var c = debts.compare(new DebtService.Compare(2L, id, "100", "0"));
    assertNull(c.fixedRateComparison());
    assertEquals(0, c.baseline().payments().getFirst().interestMinor());
    assertTrue(c.baseline().payments().get(1).interestMinor() > 0);
    assertEquals(100, c.baseline().payments().getFirst().feesMinor());
    assertEquals(189500, c.remainingCashMinor());
    assertThrows(
        IllegalArgumentException.class,
        () -> debts.compare(new DebtService.Compare(2L, id, "151", "0")));
    assertEquals(2, debts.history().size());
    assertEquals(2, debts.get().state().debts().getFirst().terms().rates().size());
    assertThrows(
        IllegalArgumentException.class,
        () -> debts.change(change(2, terms("MORTGAGE", "2"), null)));
    assertEquals(2, debts.history().size());
  }

  @Test
  void payoffAndBalloonAreDistinctAndComparisonsNeverWrite() {
    var t = terms("LOAN", "6");
    var shortTerm =
        new DebtService.Terms(
            debt,
            t.name(),
            t.kind(),
            cash,
            "0",
            "100",
            "0",
            day.plusDays(15),
            "1000",
            "0",
            t.lenderAssumptions(),
            t.rates());
    debts.change(change(0, shortTerm, standard()));
    var c = debts.compare(new DebtService.Compare(1L, id, "1000", "0"));
    assertFalse(c.baseline().paidOff());
    assertTrue(c.extra().paidOff());
    assertTrue(c.extra().payments().isEmpty());
    assertEquals(90500, c.baseline().remainingMinor());
    assertTrue(c.warnings().stream().anyMatch(n -> n.contains("balloon")));
    assertEquals(100000, c.remainingCashMinor());
    assertEquals(200000, ledger.position(day).cashMinor());
    assertEquals(
        99500, commitments.outlook(day, day.plusMonths(1), false, true).household().closingMinor());
  }

  @Test
  void foreignOwnerAndReadOnlyCannotChangeOrLeakDebts() {
    debts.change(change(0, terms("CARD", "0"), standard()));
    var owner = AccessContext.actor();
    try {
      AccessContext.enter(
          new AccessContext.Actor("other", "other", Set.of("EDITOR", "READER"), 0, false));
      assertTrue(debts.get().state().debts().isEmpty());
      assertThrows(
          IllegalArgumentException.class,
          () -> debts.change(change(0, terms("CARD", "0"), standard())));
      assertTrue(debts.history().isEmpty());
      AccessContext.enter(new AccessContext.Actor("owner", "owner", Set.of("READER"), 0, false));
      assertThrows(
          org.springframework.web.server.ResponseStatusException.class,
          () -> debts.change(change(1, terms("CARD", "0"), null)));
    } finally {
      AccessContext.enter(owner);
    }
    assertEquals(1, debts.history().size());
  }

  @Test
  void concurrentEditsHaveOneWinnerAndOneAudit() throws Exception {
    var a = change(0, terms("LOAN", "6"), standard());
    var b = change(0, terms("LOAN", "6"), standard());
    var pool = Executors.newFixedThreadPool(2);
    try {
      var futures =
          List.of(
              pool.submit(
                  () ->
                      OwnerFixture.call(
                          () -> {
                            try {
                              debts.change(a);
                              return true;
                            } catch (IllegalArgumentException e) {
                              return false;
                            }
                          })),
              pool.submit(
                  () ->
                      OwnerFixture.call(
                          () -> {
                            try {
                              debts.change(b);
                              return true;
                            } catch (IllegalArgumentException e) {
                              return false;
                            }
                          })));
      int wins = 0;
      for (var f : futures) if (f.get()) wins++;
      assertEquals(1, wins);
      assertEquals(1, debts.history().size());
      assertEquals(1, debts.get().state().revision());
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void httpRejectsUnauthenticatedMutationAndStrictFieldTypes() throws Exception {
    var post =
        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
            "/api/ledger/debts");
    mvc.perform(
            post.header("X-Workspace-Request", "1")
                .contentType("application/json")
                .content(json.writeValueAsString(change(0, terms("CARD", "0"), standard()))))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                .isUnauthorized());
    assertTrue(debts.get().state().debts().isEmpty());
  }

  @Test
  void failedBackupAdmissionRollsBackDebtEvidenceOperationAndAudit() {
    var before = debts.get();
    long audits = jdbc.queryForObject("SELECT COUNT(*) FROM LEDGER_AUDIT", Long.class);
    var input = change(0, terms("LOAN", "6"), standard());
    WorkspaceBackupService target =
        org.springframework.test.util.AopTestUtils.getUltimateTargetObject(backups);
    org.mockito.Mockito.doThrow(new IllegalArgumentException("Synthetic backup capacity rejection"))
        .when(target)
        .assertMutationRecoverable();
    try {
      assertThrows(IllegalArgumentException.class, () -> debts.change(input));
    } finally {
      org.mockito.Mockito.reset(target);
    }
    assertEquals(before, debts.get());
    assertFalse(debts.operation(input.operationId()).get("completed"));
    assertEquals(audits, jdbc.queryForObject("SELECT COUNT(*) FROM LEDGER_AUDIT", Long.class));
    assertEquals(200000, ledger.position(day).cashMinor());
    assertTrue(ledger.activity(debt).isEmpty());
  }

  @Test
  void delayedIncomeWhatIfRetainsDebtObligationsWithoutTreatingThemAsEditableBills() {
    debts.change(change(0, terms("CARD", "0"), standard()));
    String
        category =
            review
                .category(
                    null,
                    new ActivityReviewService.CategoryInput(
                        null, "Synthetic income", null, false, "0", null, "Synthetic category"))
                .id(),
        schedule = UUID.randomUUID().toString();
    commitments.batch(
        new CommitmentService.Batch(
            0L,
            UUID.randomUUID().toString(),
            "Synthetic expected income",
            List.of(
                new CommitmentService.Edit(
                    schedule,
                    0L,
                    day,
                    false,
                    new CommitmentService.Rule(
                        "Synthetic salary",
                        cash,
                        category,
                        "INCOME",
                        "EXPECTED",
                        "FIXED",
                        "500",
                        "ONCE",
                        day.plusDays(5),
                        null,
                        null,
                        0,
                        null)))));
    var c =
        commitments.compare(
            new CommitmentService.ComparisonInput(
                1L,
                day,
                day.plusMonths(1),
                false,
                false,
                null,
                null,
                null,
                null,
                schedule + "/" + day.plusDays(5),
                day.plusDays(15)));
    assertEquals(150000, c.base().household().closingMinor());
    assertEquals(c.base().household().closingMinor(), c.scenario().household().closingMinor());
    assertTrue(
        c.scenario().occurrences().stream().anyMatch(o -> o.scheduleId().startsWith("debt:")));
    assertEquals(1, debts.history().size());
  }

  @Test
  void overdraftComparisonRetainsNegativeCashAndRejectedMaturityCannotHideObligation() {
    var t = terms("LOAN", "0");
    debts.change(change(0, t, standard()));
    manual(cash, "-2100", "BOOKED");
    var c = debts.compare(new DebtService.Compare(1L, id, "0", "0"));
    assertEquals(-10000, c.remainingCashMinor());
    assertEquals(10000, c.reserveShortfallMinor());
    assertThrows(
        IllegalArgumentException.class,
        () -> debts.compare(new DebtService.Compare(1L, id, "1", "0")));
    var invalid =
        new DebtService.Terms(
            debt,
            t.name(),
            t.kind(),
            cash,
            "0",
            "100",
            "0",
            day,
            "1000",
            "0",
            t.lenderAssumptions(),
            t.rates());
    assertThrows(IllegalArgumentException.class, () -> debts.change(change(1, invalid, null)));
    assertEquals(1, debts.history().size());
    assertEquals(10000, debts.obligations(day, day.plusDays(15), day).getFirst().amountMinor());
  }

  @Test
  void authenticatedHttpRejectsUnknownFieldsCoercionAndMissingCsrfWithoutWrites() throws Exception {
    var actor = AccessContext.actor();
    var input = change(0, terms("CARD", "0"), standard());
    try {
      var unlocked =
          mvc.perform(
                  org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                          "/api/workspace/unlock")
                      .header("X-Workspace-Request", "1")
                      .contentType("application/json")
                      .content("{\"password\":\"synthetic-debt-password\"}"))
              .andExpect(
                  org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                      .isNoContent())
              .andReturn();
      String cookie =
          unlocked.getResponse().getHeader("Set-Cookie").split(";", 2)[0].split("=", 2)[1];
      var body = json.valueToTree(input);
      ((com.fasterxml.jackson.databind.node.ObjectNode) body).put("unknownField", true);
      mvc.perform(
              org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                      "/api/ledger/debts")
                  .cookie(new jakarta.servlet.http.Cookie("workspace_session", cookie))
                  .header("X-Workspace-Request", "1")
                  .contentType("application/json")
                  .content(json.writeValueAsString(body)))
          .andExpect(
              org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                  .isBadRequest());
      body = json.valueToTree(input);
      ((com.fasterxml.jackson.databind.node.ObjectNode) body.get("statement"))
          .put("complete", "true");
      mvc.perform(
              org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                      "/api/ledger/debts")
                  .cookie(new jakarta.servlet.http.Cookie("workspace_session", cookie))
                  .header("X-Workspace-Request", "1")
                  .contentType("application/json")
                  .content(json.writeValueAsString(body)))
          .andExpect(
              org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                  .isBadRequest());
      mvc.perform(
              org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                      "/api/ledger/debts")
                  .cookie(new jakarta.servlet.http.Cookie("workspace_session", cookie))
                  .contentType("application/json")
                  .content(json.writeValueAsString(input)))
          .andExpect(
              org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                  .isForbidden());
    } finally {
      AccessContext.enter(actor);
    }
    assertTrue(debts.get().state().debts().isEmpty());
    assertTrue(debts.history().isEmpty());
    assertFalse(debts.operation(input.operationId()).get("completed"));
    assertEquals(200000, ledger.position(day).cashMinor());
  }

  @Test
  void statementPrincipalAndChargesAreReportedSeparatelyWithoutInventingLedgerActivity() {
    debts.change(
        change(
            0,
            terms("LOAN", "6"),
            statement(true, "1000", "0", "5.01", "1.00", "200.01", "806.00", "10")));
    var report = debts.report(null);
    var row = report.statementEvidence().rows().getFirst();
    assertEquals(19400, row.statementPrincipalMinor());
    assertEquals("5.01", row.debt().statements().getLast().interestEuro());
    assertFalse(row.reconciled());
    assertEquals(200000, ledger.position(day).cashMinor());
    assertTrue(ledger.activity(debt).isEmpty());
  }

  @Test
  void lenderCreditFromLaterRefundDoesNotCreateAFictitiousOverdueMinimum() {
    var s =
        new DebtService.Statement(
            UUID.randomUUID().toString(),
            day.minusMonths(1),
            day.minusDays(2),
            day.minusDays(1),
            "Synthetic prior card statement",
            true,
            "1000",
            "0",
            "0",
            "0",
            "0",
            "1000",
            "25",
            null);
    debts.change(change(0, terms("CARD", "18"), s));
    manual(debt, "1050", "BOOKED");
    var row = debts.get().rows().getFirst();
    assertEquals(-5000, row.outstandingMinor());
    assertEquals(0, row.remainingPaymentMinor());
    assertTrue(row.warnings().stream().noneMatch(w -> w.contains("Minimum payment overdue")));
    assertEquals(200000, ledger.position(day).cashMinor());
  }

  @Test
  void settlementFromAnotherOwnedCashAccountStillReducesStatementDespiteLaterPurchases() {
    debts.change(change(0, terms("CARD", "18"), standard()));
    String planned = cash;
    cash =
        ledger
            .create(
                new LedgerService.AccountInput(
                    "Synthetic alternate repayment cash",
                    "CASH",
                    day.minusMonths(3),
                    "500",
                    "Synthetic statement"))
            .id();
    settlement("200.00");
    cash = planned;
    manual(debt, "-500.00", "BOOKED");
    var row = debts.get().rows().getFirst();
    assertEquals(130000, row.outstandingMinor());
    assertEquals(80000, row.remainingPaymentMinor());
    assertEquals(200000, row.paymentCashMinor());
    assertEquals(230000, ledger.position(day).cashMinor());
    assertEquals(
        150000,
        commitments.outlook(day, day.plusMonths(1), false, true).household().closingMinor());
    assertEquals(50000, ledger.position(day).spendingMinor());
    assertEquals(1, debts.history().size());
  }

  @Test
  void finalLoanRepaymentIncludesInterestAndFeesInAccountShortfallRatherThanPrincipalOnly() {
    debt =
        ledger
            .create(
                new LedgerService.AccountInput(
                    "Synthetic small final loan",
                    "DEBT",
                    day.minusMonths(3),
                    "-10",
                    "Synthetic statement"))
            .id();
    cash =
        ledger
            .create(
                new LedgerService.AccountInput(
                    "Synthetic final payment cash",
                    "CASH",
                    day.minusMonths(3),
                    "12",
                    "Synthetic statement"))
            .id();
    var t = terms("LOAN", "6");
    var fees =
        new DebtService.Terms(
            debt,
            t.name(),
            t.kind(),
            cash,
            "0",
            "100",
            "5",
            t.maturity(),
            "10",
            "0",
            t.lenderAssumptions(),
            t.rates());
    debts.change(change(0, fees, statement(true, "10", "0", "0", "0", "0", "10", "1")));
    var row = debts.get().rows().getFirst();
    assertEquals(1505, row.remainingPaymentMinor());
    assertTrue(row.warnings().stream().anyMatch(w -> w.contains("Repayment shortfall")));
    var outlook = commitments.outlook(day, day.plusDays(15), false, true);
    assertEquals(
        -305,
        outlook.accounts().stream()
            .filter(a -> a.id().equals(cash))
            .findFirst()
            .orElseThrow()
            .closingMinor());
    assertEquals(1200, row.paymentCashMinor());
  }
}
