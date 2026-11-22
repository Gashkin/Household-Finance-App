package com.householdfinance.mcp;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.householdfinance.access.*;
import com.householdfinance.ledger.*;
import com.householdfinance.workspace.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(
    properties = {
      "workspace.password=synthetic-mcp-password",
      "spring.datasource.url=jdbc:h2:mem:mcp-month;DB_CLOSE_DELAY=-1"
    })
@org.junit.jupiter.api.extension.ExtendWith(OwnerFixture.class)
class FinancialMonthTest {
  @Autowired FinancialMonthService snapshot;
  @Autowired BudgetService budgets;
  @Autowired LedgerService ledger;
  @Autowired CommitmentService commitments;
  @Autowired ActivityReviewService review;
  @Autowired WorkspaceBackupService backups;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;
  @Autowired LedgerEntryRepository entries;
  @Autowired DebtService debts;
  @Autowired com.householdfinance.forecast.ForecastService plans;
  @Autowired com.householdfinance.service.CashPositionService publications;
  LocalDate today = LocalDate.now(FinancialMonthService.ZONE);
  String account, category;

  @BeforeEach
  void reset() {
    var tables = new ArrayList<>(backups.counts().keySet());
    Collections.reverse(tables);
    tables.forEach(t -> jdbc.update("DELETE FROM " + t));
    account =
        ledger
            .create(
                new LedgerService.AccountInput(
                    "PRIVATE-ACCOUNT-NUMBER-123456",
                    "CASH",
                    today.minusMonths(2),
                    "1000.00",
                    "PRIVATE-STATEMENT"))
            .id();
    category =
        review
            .category(
                null,
                new ActivityReviewService.CategoryInput(
                    null, "PRIVATE-CATEGORY", null, false, "0.00", null, "Synthetic category"))
            .id();
  }

  BudgetService.Period period() {
    var t =
        budgets.template(
            null,
            new BudgetService.TemplateInput(
                null,
                "PRIVATE-BUDGET",
                "1000.00",
                "PAY_DAY",
                20,
                List.of(new BudgetService.LineInput(category, "500.00", false, null)),
                "Synthetic period"));
    return budgets.instantiate(
        t.id(), new BudgetService.Instantiate(t.revision(), today, "Synthetic current period"));
  }

  long rev() {
    return ledger.position(today).accounts().stream()
        .filter(a -> a.id().equals(account))
        .findFirst()
        .orElseThrow()
        .revision();
  }

  CommitmentService.Occurrence commitment(
      LocalDate date, String amount, String direction, String target) {
    var s = commitments.get().state();
    String id = UUID.randomUUID().toString();
    commitments.batch(
        new CommitmentService.Batch(
            s.revision(),
            UUID.randomUUID().toString(),
            "Synthetic expectation",
            List.of(
                new CommitmentService.Edit(
                    id,
                    0L,
                    date,
                    false,
                    new CommitmentService.Rule(
                        "PRIVATE-PAYEE",
                        account,
                        category,
                        direction,
                        "EXPECTED",
                        "FIXED",
                        amount,
                        "ONCE",
                        date,
                        null,
                        null,
                        0,
                        target)))));
    return commitments.upcoming(date, date).stream()
        .filter(o -> o.scheduleId().equals(id))
        .findFirst()
        .orElseThrow();
  }

  LedgerEntry actual(String ref, LocalDate date, String amount, String status) {
    ledger.manual(
        account,
        new LedgerService.Manual(
            rev(),
            new LedgerService.Row(ref, date, "PRIVATE-NARRATIVE", amount, status, "ACTIVITY"),
            "PRIVATE-SOURCE",
            true));
    var e =
        ledger.activity(account).stream()
            .filter(x -> x.reference.equals(ref))
            .findFirst()
            .orElseThrow();
    review.classify(
        e.id,
        new ActivityReviewService.Classification(
            rev(), category, "CONFIRMED", "Synthetic review", false));
    return e;
  }

  @Test
  void missingPeriodNeverInventsCalendarMonthOrBalance() {
    var s = snapshot.current();
    assertEquals("PERIOD_UNAVAILABLE", s.state());
    assertNull(s.period());
    assertTrue(s.accounts().isEmpty());
  }

  @Test
  void dublinSummerMidnightUsesConfiguredLocalDayAndExclusivePeriodBoundaries() {
    var day = LocalDate.of(2026, 7, 2);
    var t =
        budgets.template(
            null,
            new BudgetService.TemplateInput(
                null,
                "Synthetic summer period",
                "1.00",
                "PAY_DAY",
                2,
                List.of(new BudgetService.LineInput(category, "1.00", false, null)),
                "Synthetic period"));
    var p =
        budgets.instantiate(
            t.id(), new BudgetService.Instantiate(t.revision(), day, "Synthetic period"));
    var field =
        org.springframework.test.util.AopTestUtils.<FinancialMonthService>getUltimateTargetObject(
            snapshot);
    var before = field.clock;
    try {
      field.clock = Clock.fixed(Instant.parse("2026-07-01T23:30:00Z"), ZoneOffset.UTC);
      assertEquals(p.start(), snapshot.current().period().periodStart());
      field.clock = Clock.fixed(Instant.parse("2026-08-01T23:00:00Z"), ZoneOffset.UTC);
      assertEquals("PERIOD_UNAVAILABLE", snapshot.current().state());
    } finally {
      field.clock = before;
    }
  }

  @Test
  void nonCalendarMonthStableOrderingMinimisationAndZeroFinancialWrites() throws Exception {
    var p = period();
    LocalDate day = p.end().minusDays(1);
    commitment(day, "5.00", "EXPENSE", null);
    commitment(day, "10.00", "INCOME", null);
    var before = backups.counts();
    long balance = ledger.position(today).cashMinor();
    var first = snapshot.current();
    var second = snapshot.current();
    assertEquals(p.start(), first.period().periodStart());
    assertEquals(p.end(), first.period().periodEnd());
    assertNotEquals(p.start().getMonth(), p.end().getMonth());
    assertEquals("Europe/Dublin", first.period().timeZone());
    assertEquals("EXCLUSIVE", first.period().endBoundary());
    assertEquals(first.events(), second.events());
    assertEquals(2, first.events().size());
    assertEquals(
        first.events().stream().map(FinancialMonthService.Event::id).sorted().toList(),
        first.events().stream().map(FinancialMonthService.Event::id).toList());
    assertFalse(first.coverage().comprehensive());
    assertFalse(first.undated().isEmpty());
    String encoded = json.writeValueAsString(first);
    for (String secret :
        List.of(
            "PRIVATE-ACCOUNT",
            "PRIVATE-STATEMENT",
            "PRIVATE-CATEGORY",
            "PRIVATE-BUDGET",
            "PRIVATE-PAYEE")) assertFalse(encoded.contains(secret), secret);
    assertEquals(before, backups.counts());
    assertEquals(balance, ledger.position(today).cashMinor());
  }

  @Test
  void matchedActualKeepsDueAndPaymentDatesAndIsNeverSubtractedTwice() {
    var p = period();
    LocalDate due = today.equals(p.end().minusDays(1)) ? today : today.plusDays(1);
    var o = commitment(due, "10.00", "EXPENSE", null);
    var e = actual("paid", today, "-9.50", "BOOKED");
    commitments.exception(
        new CommitmentService.ExceptionInput(
            commitments.get().state().revision(),
            UUID.randomUUID().toString(),
            "Synthetic match",
            o.id(),
            null,
            null,
            false,
            e.id,
            "MATCH",
            rev()));
    var s = snapshot.current();
    assertEquals(1, s.events().size());
    var paid = s.events().getFirst();
    assertEquals(due, paid.dueDate());
    assertEquals(today, paid.paymentDate());
    assertEquals(today, paid.date());
    assertEquals(950, paid.amountMinor());
    assertTrue(paid.incorporatedInAnchor());
    assertFalse(paid.outstanding());
    assertEquals("PAID", paid.status());
    assertEquals(99050, s.accounts().getFirst().balanceMinor());
  }

  @Test
  void staleMissingAndUnreviewedStatesDoNotMasqueradeAsVerifiedCash() {
    period();
    ledger.reconcile(
        account,
        today.minusDays(2),
        new LedgerService.Reconcile(rev(), "1000.00", "Synthetic old statement", true, false));
    assertEquals("STALE", snapshot.current().accounts().getFirst().state());
    ledger.manual(
        account,
        new LedgerService.Manual(
            rev(),
            new LedgerService.Row(
                "pending", today, "PRIVATE-NARRATIVE", "-5.00", "PENDING", "ACTIVITY"),
            "PRIVATE-SOURCE",
            true));
    var s = snapshot.current();
    assertNull(s.accounts().getFirst().balanceMinor());
    assertEquals("UNAVAILABLE", s.accounts().getFirst().state());
    assertEquals(1, s.coverage().missingBalances());
    assertEquals(1, s.coverage().unresolvedActivity());
    assertTrue(s.events().isEmpty());
  }

  @Test
  void duplicateStatementImportsAndSettlementsRemainSinglePostedEvents() {
    period();
    String csv =
        "reference,date,description,amount,status,kind\nrepeat,"
            + today
            + ",PRIVATE-NARRATIVE,-4.00,BOOKED,ACTIVITY\n";
    // Exercise the existing overlap importer through its actual approved-row API.
    var row =
        new LedgerService.Row("repeat", today, "PRIVATE-NARRATIVE", "-4.00", "BOOKED", "ACTIVITY");
    var a =
        ledger.preview(
            account, new LedgerService.PreviewInput(csv, "Synthetic duplicate statement"));
    ledger.approve(account, a.id(), new LedgerService.Approval(a.revision(), List.of(row), true));
    var b =
        ledger.preview(
            account, new LedgerService.PreviewInput(csv, "Synthetic overlapping statement"));
    ledger.approve(account, b.id(), new LedgerService.Approval(b.revision(), List.of(row), true));
    var e = ledger.activity(account).getFirst();
    review.classify(
        e.id,
        new ActivityReviewService.Classification(
            rev(), category, "CONFIRMED", "Synthetic review", false));
    assertEquals(1, snapshot.current().events().size());
    assertEquals(99600, snapshot.current().accounts().getFirst().balanceMinor());
  }

  @Test
  void ownedScopeExcludesSharedForeignAccountsAndOtherUsersPeriods() {
    period();
    var owner = AccessContext.actor();
    try {
      AccessContext.enter(new AccessContext.Actor("other", "other", Set.of("EDITOR"), 0, false));
      ledger.create(
          new LedgerService.AccountInput(
              "PRIVATE-OTHER", "CASH", today, "9999.00", "PRIVATE-OTHER-SOURCE"));
      assertEquals("PERIOD_UNAVAILABLE", snapshot.current().state());
    } finally {
      AccessContext.enter(owner);
    }
    assertEquals(1, snapshot.current().accounts().size());
  }

  @Test
  void transferredAndCardSettlementLegsProduceOneCashMovement() {
    period();
    var debt =
        ledger
            .create(
                new LedgerService.AccountInput(
                    "PRIVATE-CARD", "DEBT", today.minusMonths(1), "-100.00", "PRIVATE-CARD-SOURCE"))
            .id();
    var debit = actual("cash-leg", today, "-25.00", "BOOKED");
    var credit = new LedgerEntry();
    credit.id = UUID.randomUUID().toString();
    credit.accountId = debt;
    credit.reference = "card-leg";
    credit.date = today;
    credit.description = "PRIVATE-NARRATIVE";
    credit.amountMinor = 2500;
    credit.status = "BOOKED";
    credit.kind = "ACTIVITY";
    credit.source = "PRIVATE-SOURCE";
    credit.reviewState = "CONFIRMED";
    credit.linkType = "CARD_SETTLEMENT";
    credit.linkedEntryId = debit.id;
    entries.save(credit);
    debit.reviewState = "CONFIRMED";
    debit.linkType = "CARD_SETTLEMENT";
    debit.linkedEntryId = credit.id;
    entries.save(debit);
    var s = snapshot.current();
    assertEquals(1, s.events().size());
    assertEquals("DEBT_REPAYMENT", s.events().getFirst().movement());
    assertEquals(debt, s.events().getFirst().destinationAccountId());
    assertEquals(2500, s.events().getFirst().amountMinor());
    assertTrue(s.events().getFirst().incorporatedInAnchor());
  }

  @Test
  void financialReaderCannotMutateLedgerEvenWhenMappedToOwner() {
    period();
    var actor = AccessContext.actor();
    var before = backups.counts();
    try {
      AccessContext.enter(new AccessContext.Actor("owner", "MCP", Set.of("READER"), 0, false));
      assertNotNull(snapshot.current().period());
      assertThrows(
          RuntimeException.class,
          () ->
              ledger.create(
                  new LedgerService.AccountInput("Denied", "CASH", today, "1.00", "Denied")));
    } finally {
      AccessContext.enter(actor);
    }
    assertEquals(before, backups.counts());
  }

  @Test
  void changedMatchesNeverBecomeASecondOutstandingPayment() {
    period();
    var o = commitment(today, "10.00", "EXPENSE", null);
    var e = actual("changed-payment", today, "-10.00", "BOOKED");
    commitments.exception(
        new CommitmentService.ExceptionInput(
            commitments.get().state().revision(),
            UUID.randomUUID().toString(),
            "Synthetic match",
            o.id(),
            null,
            null,
            false,
            e.id,
            "MATCH",
            rev()));
    ledger.correct(
        account,
        e.id,
        new LedgerService.Correction(
            rev(),
            new LedgerService.Row(
                e.reference, today, "PRIVATE-NARRATIVE", "-11.00", "BOOKED", "ACTIVITY"),
            "Synthetic correction"));
    review.classify(
        e.id,
        new ActivityReviewService.Classification(
            rev(), category, "CONFIRMED", "Synthetic corrected review", false));
    var s = snapshot.current();
    assertEquals(1, s.events().stream().filter(x -> x.status().equals("NEEDS_REVIEW")).count());
    assertTrue(s.events().stream().noneMatch(FinancialMonthService.Event::outstanding));
    assertTrue(s.coverage().warnings().contains("BACKEND_FORECAST_UNAVAILABLE"));
  }

  @Test
  void savedPlanAndUndatedPublicationRemainVisibleWithoutInventedDatesOrDoubleCounting() {
    period();
    String eventId = UUID.randomUUID().toString();
    plans.save(
        new com.householdfinance.forecast.ForecastService.Input(
            0L,
            "PRIVATE-PLAN",
            new com.householdfinance.forecast.ForecastService.BaselineInput(
                "MANUAL", today, "1000.00", "0.00", "PRIVATE-SOURCE"),
            today.plusMonths(1),
            "0.00",
            List.of(
                new com.householdfinance.forecast.ForecastService.EventInput(
                    eventId,
                    today.plusDays(1),
                    "PRIVATE-PAYEE",
                    "EXPENSE",
                    "EXPECTED",
                    "5.00",
                    "PRIVATE-SOURCE")),
            true));
    publications.save(
        new com.householdfinance.service.CashPositionService.Input(
            today,
            "PRIVATE-PUBLICATION",
            "ESTIMATED",
            List.of(
                new com.householdfinance.service.CashPositionService.Line(
                    "PRIVATE-ACCOUNT", 100000)),
            List.of(
                new com.householdfinance.service.CashPositionService.Line("PRIVATE-PAYEE", 500)),
            0));
    var s = snapshot.current();
    var alternative =
        s.events().stream()
            .filter(e -> e.id().equals("saved-plan:" + eventId))
            .findFirst()
            .orElseThrow();
    assertNull(alternative.accountId());
    assertFalse(alternative.outstanding());
    assertEquals("UNVERIFIED_ALTERNATIVE", alternative.cashImpact());
    assertTrue(
        s.undated().stream()
            .anyMatch(
                e ->
                    e.kind().equals("PUBLISHED_UNDATED_RESERVE")
                        && !e.cashFlow()
                        && e.amountMinor() == 500));
  }

  @Test
  void accountSpecificDailyForecastsComeUnchangedFromTheAuthoritativeBackend() {
    var p = period();
    String target =
        ledger
            .create(
                new LedgerService.AccountInput(
                    "PRIVATE-TARGET", "CASH", today.minusDays(10), "5.00", "PRIVATE-SOURCE"))
            .id();
    commitment(today.plusDays(1), "2.00", "TRANSFER", target);
    var s = snapshot.current();
    var backend = commitments.outlook(today, p.end().minusDays(1), false, false);
    assertEquals(backend.accounts().size(), s.forecasts().size());
    for (var f : s.forecasts()) {
      var expected =
          backend.accounts().stream()
              .filter(a -> a.id().equals(f.accountId()))
              .findFirst()
              .orElseThrow();
      assertEquals(expected.startingMinor(), f.anchorMinor());
      assertEquals(expected.days(), f.days());
    }
  }

  @Test
  void cardChargesAndRepaymentHaveDistinctAccountAndCashSemantics() {
    period();
    String card =
        ledger
            .create(
                new LedgerService.AccountInput(
                    "PRIVATE-CARD", "DEBT", today.minusMonths(2), "-100.00", "PRIVATE-SOURCE"))
            .id();
    var terms =
        new DebtService.Terms(
            card,
            "PRIVATE-LENDER",
            "CARD",
            account,
            "5000.00",
            "25.00",
            "0.00",
            today.plusYears(1),
            "1000.00",
            "0.00",
            "Synthetic assumptions",
            List.of(new DebtService.Rate(today.minusMonths(2), "10.00")));
    var statement =
        new DebtService.Statement(
            UUID.randomUUID().toString(),
            today.minusMonths(1),
            today.minusDays(1),
            today.plusDays(5),
            "PRIVATE-SOURCE",
            true,
            "100.00",
            "0.00",
            "0.00",
            "0.00",
            "0.00",
            "100.00",
            "25.00",
            null);
    debts.change(
        new DebtService.Change(
            0L,
            UUID.randomUUID().toString(),
            "Synthetic card evidence",
            UUID.randomUUID().toString(),
            terms,
            statement));
    ledger.manual(
        card,
        new LedgerService.Manual(
            0L,
            new LedgerService.Row(
                "purchase", today, "PRIVATE-PAYEE", "-10.00", "BOOKED", "ACTIVITY"),
            "PRIVATE-SOURCE",
            true));
    var purchase = ledger.activity(card).getFirst();
    review.classify(
        purchase.id,
        new ActivityReviewService.Classification(
            1L, category, "CONFIRMED", "Synthetic card charge", false));
    var debit = actual("repayment", today, "-25.00", "BOOKED");
    long cardRevision =
        ledger.position(today).accounts().stream()
            .filter(a -> a.id().equals(card))
            .findFirst()
            .orElseThrow()
            .revision();
    ledger.manual(
        card,
        new LedgerService.Manual(
            cardRevision,
            new LedgerService.Row(
                "card-paid", today, "PRIVATE-PAYEE", "25.00", "BOOKED", "ACTIVITY"),
            "PRIVATE-SOURCE",
            true));
    var credit =
        ledger.activity(card).stream()
            .filter(e -> e.reference.equals("card-paid"))
            .findFirst()
            .orElseThrow();
    review.link(
        debit.id,
        new ActivityReviewService.Link(
            rev(), credit.id, cardRevision + 1, "CARD_SETTLEMENT", "Synthetic settlement"));
    var s = snapshot.current();
    var charge =
        s.events().stream()
            .filter(e -> e.id().equals("posted:" + purchase.id))
            .findFirst()
            .orElseThrow();
    var payment =
        s.events().stream()
            .filter(e -> e.id().equals("posted:" + debit.id))
            .findFirst()
            .orElseThrow();
    assertEquals("NO_CASH", charge.cashImpact());
    assertEquals("CREDIT_CARD_CHARGE_OR_REFUND", charge.movement());
    assertEquals("SOURCE_CASH", payment.cashImpact());
    assertEquals("CREDIT_CARD_REPAYMENT", payment.movement());
    assertEquals(card, payment.destinationAccountId());
    assertTrue(s.events().stream().noneMatch(e -> e.id().equals("posted:" + credit.id)));
  }

  @Test
  void oversizedCurrentPeriodOutputFailsClosedWithoutFinancialMutations() {
    period();
    var rows = new ArrayList<LedgerEntry>();
    for (int i = 0; i < 1001; i++) {
      var e = new LedgerEntry();
      e.id = UUID.randomUUID().toString();
      e.accountId = account;
      e.reference = "bounded-" + i;
      e.date = today;
      e.description = "PRIVATE-NARRATIVE";
      e.amountMinor = -1;
      e.status = "BOOKED";
      e.kind = "ACTIVITY";
      e.source = "PRIVATE-SOURCE";
      e.reviewState = "CONFIRMED";
      rows.add(e);
    }
    entries.saveAll(rows);
    var before = backups.counts();
    assertEquals(
        "EVENT_LIMIT",
        assertThrows(IllegalArgumentException.class, () -> snapshot.current()).getMessage());
    assertEquals(before, backups.counts());
  }
}
