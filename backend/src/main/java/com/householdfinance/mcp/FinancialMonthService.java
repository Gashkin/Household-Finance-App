package com.householdfinance.mcp;

import com.householdfinance.access.AccessContext;
import com.householdfinance.ledger.*;
import com.householdfinance.service.CashPositionService;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The only financial read surface available to the MCP dispatcher. No domain writes. */
@Service
public class FinancialMonthService {
  public static final ZoneId ZONE = ZoneId.of("Europe/Dublin");
  public static final int MAX_EVENTS = 1000, MAX_ACCOUNTS = 50;

  @org.springframework.beans.factory.annotation.Autowired(required = false)
  Clock clock = Clock.systemUTC();

  public record Period(
      LocalDate periodStart,
      LocalDate periodEnd,
      String endBoundary,
      String basis,
      String timeZone) {}

  public record Account(
      String accountId,
      String displayName,
      String kind,
      String currency,
      Long balanceMinor,
      LocalDate balanceAsOf,
      LocalDate evidenceAsOf,
      String provenance,
      String state,
      boolean reconciled,
      Long openingBalanceMinor,
      LocalDate openingBalanceAsOf,
      long pendingDebitMinor,
      int unresolved) {}

  public record Event(
      String id,
      String accountId,
      String destinationAccountId,
      String allocation,
      String direction,
      String movement,
      String currency,
      long amountMinor,
      LocalDate date,
      String dateBasis,
      LocalDate dueDate,
      LocalDate paymentDate,
      String status,
      String dateCertainty,
      String amountCertainty,
      String provenance,
      Instant lastReviewed,
      boolean incorporatedInAnchor,
      boolean outstanding,
      String relatedEventId,
      String cashImpact,
      String expectationCertainty,
      String baselineTreatment) {}

  public record Undated(
      String id, String kind, Long amountMinor, String currency, String reason, boolean cashFlow) {}

  public record Coverage(
      boolean comprehensive,
      int unresolvedActivity,
      int staleAccounts,
      int missingBalances,
      List<String> exclusions,
      List<String> warnings) {}

  public record Forecast(
      String accountId,
      LocalDate baseline,
      long anchorMinor,
      String anchorBasis,
      boolean verified,
      List<CommitmentService.Point> days) {}

  public record Snapshot(
      String schemaVersion,
      String state,
      Instant asOf,
      Period period,
      List<Account> accounts,
      List<Event> events,
      List<Undated> undated,
      Coverage coverage,
      List<Forecast> forecasts) {}

  private final BudgetService budgets;
  private final LedgerService ledger;
  private final CommitmentService commitments;
  private final McpOptionalReads optional;
  private final LedgerEntryRepository entries;
  private final LedgerAccountRepository accountRecords;

  private final CashPositionService publications;

  public FinancialMonthService(
      BudgetService b,
      LedgerService l,
      CommitmentService c,
      McpOptionalReads d,
      LedgerEntryRepository e,
      LedgerAccountRepository a,
      CashPositionService s) {
    budgets = b;
    ledger = l;
    commitments = c;
    optional = d;
    entries = e;
    accountRecords = a;

    publications = s;
  }

  private boolean in(LocalDate d, BudgetService.Period p) {
    return d != null && !d.isBefore(p.start()) && d.isBefore(p.end());
  }

  private static final List<String> EXCLUSIONS =
      List.of(
          "LIVE_BANK_FEEDS",
          "LEGACY_WORKBOOK_RECORDS_UNSUPPORTED",
          "OTHER_USERS_AND_SHARED_ACCOUNTS",
          "RAW_TRANSACTIONS_AND_NARRATIVES",
          "GOAL_ALLOCATIONS_ARE_NOT_CASH_MOVEMENTS",
          "DEBT_PRINCIPAL_INTEREST_SPLIT_UNAVAILABLE_IN_CASH_FLOW_SERVICE");

  @Transactional(readOnly = true)
  public Snapshot current() {
    AccessContext.requireRead();
    var now = clock.instant();
    var today = now.atZone(ZONE).toLocalDate();
    var ps = budgets.periods().stream().filter(p -> in(today, p)).toList();
    if (ps.size() != 1)
      return new Snapshot(
          "1.0",
          "PERIOD_UNAVAILABLE",
          now,
          null,
          List.of(),
          List.of(),
          List.of(),
          new Coverage(
              false, 0, 0, 0, EXCLUSIONS, List.of("CREATE_OR_REVIEW_CURRENT_BUDGET_PERIOD")),
          List.of());
    var p = ps.getFirst();
    if (ChronoUnit.DAYS.between(p.start(), p.end()) > 35)
      throw new IllegalArgumentException("PERIOD_LIMIT");
    // A template can change after instantiation. The persisted boundaries remain authoritative.
    var period =
        new Period(p.start(), p.end(), "EXCLUSIVE", "SAVED_BUDGET_PERIOD", "Europe/Dublin");
    var owned =
        accountRecords.findAll().stream().filter(a -> AccessContext.owns(a.ownerId)).toList();
    if (owned.size() > MAX_ACCOUNTS) throw new IllegalArgumentException("ACCOUNT_LIMIT");
    long sourceRows = 0;
    for (var a : owned) {
      sourceRows += entries.countByAccountId(a.id);
      if (sourceRows > 20000) throw new IllegalArgumentException("SOURCE_LIMIT");
    }
    var positions =
        ledger.ownedPosition(today).accounts().stream()
            .sorted(Comparator.comparing(LedgerService.AccountView::id))
            .toList();
    if (positions.size() > MAX_ACCOUNTS) throw new IllegalArgumentException("ACCOUNT_LIMIT");
    var ids = new HashSet<String>();
    positions.forEach(a -> ids.add(a.id()));
    var opening = new HashMap<String, LedgerService.AccountView>();
    ledger.ownedPosition(p.start().minusDays(1)).accounts().forEach(a -> opening.put(a.id(), a));
    var actuals = new ArrayList<LedgerEntry>();
    for (var a : positions)
      actuals.addAll(entries.findByAccountIdOrderByDateAscReferenceAsc(a.id()));
    var byId = new HashMap<String, LedgerEntry>();
    actuals.forEach(e -> byId.put(e.id, e));
    int unresolved = 0, stale = 0, missing = 0;
    var accounts = new ArrayList<Account>();
    for (var a : positions) {
      int bad =
          (int)
              actuals.stream()
                  .filter(
                      e ->
                          e.accountId.equals(a.id())
                              && !e.date.isAfter(today)
                              && !e.status.equals("VOID")
                              && (!e.reviewState.equals("CONFIRMED") || e.status.equals("PENDING")))
                  .count();
      unresolved += bad;
      boolean old = a.reconciledDate() == null || a.reconciledDate().isBefore(today);
      if (old) stale++;
      if (bad > 0) missing++;
      var o = opening.get(a.id());
      accounts.add(
          new Account(
              a.id(),
              (a.kind().equals("CASH") ? "Cash" : "Debt") + " account " + (accounts.size() + 1),
              a.kind(),
              "EUR",
              bad == 0 ? a.bookedMinor() : null,
              today,
              a.reconciledDate(),
              "LEDGER_DERIVED_NOT_LIVE_BANK",
              bad > 0 ? "UNAVAILABLE" : old ? "STALE" : a.reconciled() ? "REVIEWED" : "ESTIMATED",
              a.reconciled(),
              o != null && o.reconciled() ? o.bookedMinor() : null,
              p.start().minusDays(1),
              a.pendingDebitMinor(),
              bad));
    }
    var events = new ArrayList<Event>();
    var warnings = new LinkedHashSet<String>();
    if (accounts.isEmpty()) warnings.add("NO_OWNED_ACCOUNTS");
    if (accounts.stream().anyMatch(a -> a.openingBalanceMinor() == null))
      warnings.add("OPENING_BALANCE_UNAVAILABLE");
    var undated = new ArrayList<Undated>();
    var cashIds = new HashSet<String>();
    positions.stream().filter(a -> a.kind().equals("CASH")).forEach(a -> cashIds.add(a.id()));
    var debtKinds = new HashMap<String, String>();
    try {
      optional
          .debts()
          .state()
          .debts()
          .forEach(d -> debtKinds.put(d.terms().accountId(), d.terms().kind()));
    } catch (IllegalArgumentException ex) {
      warnings.add("DEBT_KIND_UNAVAILABLE");
    }
    List<CommitmentService.DatedOccurrence> cs = commitments.financialPeriod(p.start(), p.end());
    // Posted activity and linked commitments share one event, never a second debit.
    var matched = new HashSet<String>();
    for (var d : cs) {
      var o = d.occurrence();
      if (!ids.contains(o.accountId())) {
        warnings.add("MISSING_COMMITMENT_ACCOUNT");
        continue;
      }
      boolean paid = o.state().equals("MATCHED"), cancelled = o.state().equals("SKIPPED");
      var e = paid ? byId.get(o.entryId()) : null;
      if (paid && e == null) {
        warnings.add("MISSING_SETTLEMENT");
        continue;
      }
      if (paid) {
        matched.add(e.id);
        if (LedgerService.isTransferRelationship(e) && e.linkedEntryId != null)
          matched.add(e.linkedEntryId);
      }
      var date = paid ? d.paymentDate() : d.dueDate();
      boolean incorporated = paid && !date.isAfter(today);
      boolean reviewNeeded = o.state().equals("MATCH_NEEDS_REVIEW");
      String status =
          paid
              ? "PAID"
              : cancelled
                  ? "CANCELLED"
                  : reviewNeeded
                      ? "NEEDS_REVIEW"
                      : o.state().equals("DATA_UNCONFIRMED")
                          ? "UNCONFIRMED"
                          : o.state().equals("OVERDUE")
                              ? "OVERDUE"
                              : d.dueDate().equals(today) ? "DUE" : "PLANNED";
      if (o.state().equals("MATCH_NEEDS_REVIEW") || o.state().equals("DATA_UNCONFIRMED"))
        warnings.add("COMMITMENT_REQUIRES_REVIEW");
      String target = ids.contains(o.counterpartId()) ? o.counterpartId() : null;
      String treatment = commitments.baselineTreatment(o);
      events.add(
          new Event(
              o.id(),
              o.accountId(),
              target,
              target == null && o.direction().equals("TRANSFER") ? "UNKNOWN_DESTINATION" : "KNOWN",
              o.direction(),
              o.direction().equals("TRANSFER")
                  ? "INTERNAL_TRANSFER"
                  : o.direction().equals("INCOME") ? "INCOME" : "EXPENSE",
              "EUR",
              paid ? o.actualMinor() : o.amountMinor(),
              date,
              paid ? "SETTLEMENT_DATE" : "EXPECTED_DUE_DATE",
              d.dueDate(),
              d.paymentDate(),
              status,
              paid ? "CONFIRMED" : "ESTIMATED",
              paid || o.amountType().equals("FIXED") ? "CONFIRMED" : "ESTIMATED",
              "RECURRING_COMMITMENT",
              null,
              incorporated,
              treatment.equals("OUTSTANDING"),
              e == null ? null : e.id,
              o.direction().equals("TRANSFER") && cashIds.contains(target)
                  ? "BETWEEN_CASH_ACCOUNTS"
                  : "SOURCE_CASH",
              o.certainty(),
              treatment));
    }
    for (var e : actuals) {
      if (!in(e.date, p)
          || e.date.isAfter(today)
          || !e.status.equals("BOOKED")
          || !e.reviewState.equals("CONFIRMED")
          || matched.contains(e.id)) continue;
      boolean transfer = LedgerService.isTransferRelationship(e) || e.kind.equals("TRANSFER");
      var peer = byId.get(e.linkedEntryId);
      boolean paired =
          transfer
              && peer != null
              && LedgerService.isTransferRelationship(e)
              && LedgerService.isTransferRelationship(peer)
              && Objects.equals(e.linkType, peer.linkType)
              && peer.reviewState.equals("CONFIRMED")
              && peer.status.equals("BOOKED")
              && Objects.equals(peer.linkedEntryId, e.id)
              && peer.date.equals(e.date)
              && peer.amountMinor == -e.amountMinor;
      if (paired && e.amountMinor > 0) continue;
      boolean sourceCash = cashIds.contains(e.accountId),
          destinationCash = paired && cashIds.contains(peer.accountId);
      String movement =
          transfer
              ? (paired && "CARD".equals(debtKinds.get(peer.accountId))
                  ? "CREDIT_CARD_REPAYMENT"
                  : paired
                          && positions.stream()
                              .anyMatch(
                                  a -> a.id().equals(peer.accountId) && a.kind().equals("DEBT"))
                      ? "DEBT_REPAYMENT"
                      : "INTERNAL_TRANSFER")
              : e.kind.equals("ADJUSTMENT")
                  ? "ADJUSTMENT"
                  : !sourceCash
                      ? ("CARD".equals(debtKinds.get(e.accountId))
                          ? "CREDIT_CARD_CHARGE_OR_REFUND"
                          : "DEBT_MOVEMENT_UNCLASSIFIED")
                      : e.amountMinor > 0 ? "INCOME" : "EXPENSE";
      if (transfer && !paired) warnings.add("TRANSFER_PAIR_UNVERIFIED");
      events.add(
          new Event(
              "posted:" + e.id,
              transfer && !paired && e.amountMinor > 0 ? null : e.accountId,
              paired ? peer.accountId : transfer && e.amountMinor > 0 ? e.accountId : null,
              transfer && !paired
                  ? e.amountMinor > 0 ? "UNKNOWN_SOURCE" : "UNKNOWN_DESTINATION"
                  : "KNOWN",
              transfer
                  ? "TRANSFER"
                  : !sourceCash ? "DEBT_CHANGE" : e.amountMinor >= 0 ? "INCOME" : "EXPENSE",
              movement,
              "EUR",
              Math.abs(e.amountMinor),
              e.date,
              "POSTED_ACTIVITY_DATE",
              null,
              e.date,
              "PAID",
              "CONFIRMED",
              "CONFIRMED",
              "REVIEWED_LEDGER_ACTIVITY",
              null,
              true,
              false,
              paired ? peer.id : null,
              sourceCash
                  ? !paired && transfer && e.amountMinor > 0
                      ? "DESTINATION_CASH"
                      : destinationCash ? "BETWEEN_CASH_ACCOUNTS" : "SOURCE_CASH"
                  : destinationCash ? "DESTINATION_CASH" : "NO_CASH",
              "NOT_APPLICABLE",
              "INCORPORATED"));
    }
    try {
      for (var o : optional.debtObligations(p.start(), p.end().minusDays(1), today))
        if (ids.contains(o.accountId()))
          events.add(
              new Event(
                  o.id(),
                  o.accountId(),
                  null,
                  "UNKNOWN_DESTINATION",
                  "TRANSFER",
                  "DEBT_REPAYMENT",
                  "EUR",
                  o.amountMinor(),
                  o.date(),
                  "EXPECTED_DUE_DATE",
                  o.date(),
                  null,
                  "PLANNED",
                  "ESTIMATED",
                  "ESTIMATED",
                  "DEBT_STATEMENT_EXPECTATION",
                  null,
                  false,
                  true,
                  null,
                  "SOURCE_CASH",
                  o.certainty(),
                  "OUTSTANDING"));
    } catch (IllegalArgumentException ex) {
      warnings.add("DEBT_EXPECTATIONS_UNAVAILABLE");
    }
    // The retained manual plan has no funding-account or settlement matching.
    // Show its current-period events as unverified alternatives, never extra obligations.
    try {
      var plan = optional.savedPlan().plan();
      if (plan != null)
        for (var e : plan.inputs().events())
          if (in(e.date(), p)) {
            events.add(
                new Event(
                    "saved-plan:" + e.id(),
                    null,
                    null,
                    "UNKNOWN_ACCOUNT",
                    e.direction(),
                    e.direction().equals("INCOME") ? "INCOME" : "EXPENSE",
                    "EUR",
                    e.amountMinor(),
                    e.date(),
                    "SAVED_PLAN_EXPECTATION",
                    e.date(),
                    null,
                    "UNRECONCILED_PROJECTION",
                    "ESTIMATED",
                    "ESTIMATED",
                    "SAVED_DATED_PLAN",
                    plan.updatedAt(),
                    false,
                    false,
                    null,
                    "UNVERIFIED_ALTERNATIVE",
                    e.certainty(),
                    "UNVERIFIED_ALTERNATIVE"));
            warnings.add("SAVED_PLAN_MAY_OVERLAP_COMMITMENTS_OR_ACTUALS");
          }
    } catch (IllegalArgumentException ex) {
      warnings.add("SAVED_PLAN_UNAVAILABLE");
    }
    // Published reserves have no dates or matching identifiers. Expose as
    // exceptions only; an amount must never acquire an invented payment day.
    publications
        .latest()
        .filter(s -> in(s.asOfDate(), p))
        .ifPresent(
            s -> {
              for (int i = 0; i < s.upcomingCommitments().size(); i++)
                undated.add(
                    new Undated(
                        "publication:" + s.id() + ":" + i,
                        "PUBLISHED_UNDATED_RESERVE",
                        s.upcomingCommitments().get(i).amountMinor(),
                        "EUR",
                        "UNKNOWN_DATE_AND_MATCHING_MAY_OVERLAP",
                        false));
            });
    if (!undated.isEmpty()) warnings.add("UNDATED_RESERVES_REQUIRE_REVIEW");
    if (events.size() > MAX_EVENTS) throw new IllegalArgumentException("EVENT_LIMIT");
    events.sort(Comparator.comparing(Event::date).thenComparing(Event::id));
    // Allocations are useful uncertainty context, explicitly not additional commitments.
    if (p.incomeMinor() > 0)
      undated.add(
          new Undated(
              "budget-income:" + p.id(),
              "BUDGET_INCOME",
              p.incomeMinor(),
              "EUR",
              "ALLOCATION_NOT_A_DATED_COMMITMENT",
              false));
    for (var l : p.lines())
      if (l.dueDate() == null)
        undated.add(
            new Undated(
                "budget-allocation:" + l.categoryId(),
                "BUDGET_ALLOCATION",
                l.amountMinor(),
                "EUR",
                "ALLOCATION_NOT_A_DATED_COMMITMENT",
                false));
    var forecasts = new ArrayList<Forecast>();
    try {
      if (p.end().minusDays(1).isAfter(today)) {
        var f = optional.outlook(today, p.end().minusDays(1));
        for (var a : f.accounts())
          if (ids.contains(a.id()))
            forecasts.add(
                new Forecast(
                    a.id(),
                    f.baseline(),
                    a.startingMinor(),
                    "BOOKED_LESS_RESERVED_PENDING_DEBITS",
                    f.currentEvidence() && unresolved == 0,
                    a.days()));
        if (!f.currentEvidence()) warnings.add("BACKEND_FORECAST_ILLUSTRATIVE");
      }
    } catch (IllegalArgumentException ex) {
      warnings.add("BACKEND_FORECAST_UNAVAILABLE");
    }
    warnings.add("REVIEW_TIMESTAMPS_NOT_RECORDED_FOR_LEDGER_OR_RECURRING_ROWS");
    warnings.add("NO_LIVE_BANK_VERIFICATION");
    return new Snapshot(
        "1.0",
        "PARTIAL",
        now,
        period,
        List.copyOf(accounts),
        List.copyOf(events),
        List.copyOf(undated),
        new Coverage(false, unresolved, stale, missing, EXCLUSIONS, List.copyOf(warnings)),
        List.copyOf(forecasts));
  }
}
