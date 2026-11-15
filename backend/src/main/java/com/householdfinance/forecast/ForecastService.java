package com.householdfinance.forecast;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.householdfinance.ledger.LedgerService;
import java.math.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Dated expectations, never a mutation of actual cash or the reviewed ledger. */
@Service
public class ForecastService {
  public record BaselineInput(
      String mode, LocalDate date, String cashEuro, String pendingEuro, String source) {}

  public record EventInput(
      String id,
      LocalDate date,
      String label,
      String direction,
      String certainty,
      String amountEuro,
      String source) {}

  public record Input(
      Long revision,
      String name,
      BaselineInput baseline,
      LocalDate endDate,
      String reserveEuro,
      List<EventInput> events,
      Boolean acknowledged) {}

  public record Baseline(
      String mode,
      LocalDate date,
      long cashMinor,
      long pendingMinor,
      String source,
      Instant capturedAt,
      Map<String, Long> revisions) {}

  public record Event(
      String id,
      LocalDate date,
      String label,
      String direction,
      String certainty,
      long amountMinor,
      String source) {}

  public record Stored(
      String name, Baseline baseline, LocalDate endDate, long reserveMinor, List<Event> events) {}

  public record Day(
      LocalDate date,
      long openingMinor,
      long expensesMinor,
      long expectedIncomeMinor,
      long uncertainIncomeMinor,
      long appliedIncomeMinor,
      long lowMinor,
      long closingMinor,
      long headroomMinor,
      List<Event> events) {}

  public record Projection(
      long startingMinor,
      long totalExpensesMinor,
      long expectedIncomeMinor,
      long uncertainIncomeMinor,
      long appliedIncomeMinor,
      long closingMinor,
      long lowestMinor,
      LocalDate lowestDate,
      LocalDate shortfallDate,
      LocalDate reserveBreachDate,
      long cashOnlyHeadroomMinor,
      long planningHeadroomMinor,
      List<Day> days) {}

  public record View(
      long revision,
      Instant updatedAt,
      Stored inputs,
      String evidenceState,
      boolean currentDecisionReady,
      List<String> warnings,
      Projection projection) {}

  public record Workspace(LocalDate today, LocalDate suggestedLedgerDate, View plan) {}

  public record WhatIf(
      Long revision,
      String expenseEuro,
      LocalDate expenseDate,
      String delayedIncomeId,
      LocalDate delayedUntil,
      Boolean includeUncertain) {}

  public record Comparison(
      long revision,
      String evidenceState,
      boolean currentDecisionReady,
      List<String> warnings,
      Projection base,
      Projection scenario,
      View checkedPlan) {}

  private static final long SAFE = 9_007_199_254_740_991L, MAX_AMOUNT = 1_000_000_000_000L;
  private final ForecastPlanRepository repository;
  private final LedgerService ledger;
  private final ObjectMapper json;
  private final Clock clock;

  @Autowired
  public ForecastService(
      ForecastPlanRepository repository, LedgerService ledger, ObjectMapper json) {
    this(repository, ledger, json, Clock.system(ZoneId.of("Europe/Dublin")));
  }

  ForecastService(
      ForecastPlanRepository repository, LedgerService ledger, ObjectMapper json, Clock clock) {
    this.repository = repository;
    this.ledger = ledger;
    this.json = json;
    this.clock = clock;
  }

  private LocalDate today() {
    return LocalDate.now(clock);
  }

  private static void check(boolean condition, String message) {
    if (!condition) throw new IllegalArgumentException(message);
  }

  private static long add(long a, long b) {
    try {
      long value = Math.addExact(a, b);
      check(value >= -SAFE && value <= SAFE, "Forecast totals exceed the safe integer range");
      return value;
    } catch (ArithmeticException e) {
      throw new IllegalArgumentException("Forecast totals exceed the safe integer range");
    }
  }

  private static long amount(String value, boolean signed) {
    check(
        value != null
            && value.length() <= 30
            && value.matches((signed ? "-?" : "") + "(?:0|[1-9][0-9]*)(?:\\.[0-9]{1,2})?"),
        "Use exact EUR text with at most two decimal places");
    try {
      long minor = new BigDecimal(value).movePointRight(2).longValueExact();
      check(minor >= -MAX_AMOUNT && minor <= MAX_AMOUNT, "Amount exceeds the local planning limit");
      return minor;
    } catch (ArithmeticException e) {
      throw new IllegalArgumentException("Amount exceeds the local planning limit");
    }
  }

  private static String text(String value, int max, String field) {
    check(
        value != null && !value.isBlank() && value.trim().length() <= max,
        field + " is required and must fit the displayed limit");
    return value.trim();
  }

  private Stored read(ForecastPlan plan) {
    try {
      Stored value = json.readValue(plan.payload, Stored.class);
      check(
          value != null
              && value.baseline() != null
              && value.events() != null
              && value.events().size() <= 200,
          "Invalid saved forecast");
      Baseline b = value.baseline();
      check(
          b.date() != null
              && value.endDate() != null
              && value.endDate().isAfter(b.date())
              && ChronoUnit.DAYS.between(b.date(), value.endDate()) <= 90,
          "Invalid saved horizon");
      check(
          Set.of("LEDGER", "MANUAL").contains(Objects.toString(b.mode(), ""))
              && b.cashMinor() >= -SAFE
              && b.cashMinor() <= SAFE
              && b.pendingMinor() >= 0
              && b.pendingMinor() <= SAFE
              && value.reserveMinor() >= 0
              && value.reserveMinor() <= MAX_AMOUNT
              && b.revisions() != null
              && b.revisions().size() <= 30,
          "Invalid saved starting evidence");
      check(
          !b.date().isAfter(today())
              && b.capturedAt() != null
              && b.source() != null
              && !b.source().isBlank()
              && b.source().length() <= 200,
          "Invalid saved evidence metadata");
      var checked =
          events(
              value.events().stream()
                  .map(
                      e ->
                          new EventInput(
                              e.id(),
                              e.date(),
                              e.label(),
                              e.direction(),
                              e.certainty(),
                              BigDecimal.valueOf(e.amountMinor(), 2).toPlainString(),
                              e.source()))
                  .toList(),
              b,
              value.endDate());
      return new Stored(
          text(value.name(), 100, "Plan name"), b, value.endDate(), value.reserveMinor(), checked);
    } catch (Exception e) {
      throw new IllegalArgumentException("Saved forecast is invalid; restore a verified backup");
    }
  }

  private Baseline capture(BaselineInput input) {
    check(
        input != null
            && input.date() != null
            && !input.date().isAfter(today())
            && !input.date().isBefore(today().minusDays(365)),
        "Choose a starting evidence date within the past year");
    if ("LEDGER".equals(input.mode())) {
      check(
          input.cashEuro() == null && input.pendingEuro() == null && input.source() == null,
          "Ledger amounts and sources are read by the server");
      var position = ledger.position(input.date());
      var cash = position.accounts().stream().filter(a -> a.kind().equals("CASH")).toList();
      check(
          !cash.isEmpty() && cash.stream().allMatch(LedgerService.AccountView::reconciled),
          "Reconcile every cash ledger account for the selected evidence date first");
      Map<String, Long> revisions = new TreeMap<>();
      cash.forEach(a -> revisions.put(a.id(), a.revision()));
      return new Baseline(
          "LEDGER",
          input.date(),
          position.cashMinor(),
          position.cashPendingDebitMinor(),
          "Reconciled cash ledger as of " + input.date(),
          clock.instant(),
          Map.copyOf(revisions));
    }
    check(
        "MANUAL".equals(input.mode()),
        "Choose reconciled ledger evidence or an explicit manual estimate");
    return new Baseline(
        "MANUAL",
        input.date(),
        amount(input.cashEuro(), true),
        amount(input.pendingEuro(), false),
        text(input.source(), 200, "Manual evidence source"),
        clock.instant(),
        Map.of());
  }

  private List<Event> events(List<EventInput> inputs, Baseline baseline, LocalDate end) {
    check(inputs != null && inputs.size() <= 200, "Provide up to 200 dated expectations");
    Set<String> ids = new HashSet<>();
    List<Event> result = new ArrayList<>();
    for (var input : inputs) {
      check(input != null, "Complete expectation required");
      String id;
      try {
        id = UUID.fromString(input.id()).toString();
      } catch (Exception e) {
        throw new IllegalArgumentException("Each expectation needs a stable UUID");
      }
      check(ids.add(id), "Duplicate expectation ID; each occurrence must appear only once");
      check(
          input.date() != null
              && input.date().isAfter(baseline.date())
              && !input.date().isAfter(end),
          "Move or remove expectations on/before the baseline or outside the horizon; review"
              + " actuals before replacing evidence");
      check(
          Set.of("INCOME", "EXPENSE").contains(Objects.toString(input.direction(), "")),
          "Choose income or expense");
      check(
          Set.of("EXPECTED", "UNCERTAIN").contains(Objects.toString(input.certainty(), ""))
              && (!input.direction().equals("EXPENSE") || input.certainty().equals("EXPECTED")),
          "Expenses are reserved; only income may be marked uncertain");
      long value = amount(input.amountEuro(), false);
      check(value > 0, "Expectation amounts must be positive");
      result.add(
          new Event(
              id,
              input.date(),
              text(input.label(), 100, "Expectation label"),
              input.direction(),
              input.certainty(),
              value,
              text(input.source(), 200, "Expectation source")));
    }
    return result.stream()
        .sorted(
            Comparator.comparing(Event::date)
                .thenComparing(Event::direction)
                .thenComparing(Event::id))
        .toList();
  }

  @Transactional
  public synchronized Workspace save(Input input) {
    check(
        input != null
            && input.revision() != null
            && input.revision() >= 0
            && Boolean.TRUE.equals(input.acknowledged()),
        "Confirm starting evidence and exclude expenses already reserved, paid, or represented by"
            + " pending debits");
    var plan = repository.findById("household").orElse(null);
    check(
        input.revision() == (plan == null ? 0 : plan.version + 1),
        "Plan changed; reload before saving corrections");
    Baseline baseline =
        input.baseline() != null
            ? capture(input.baseline())
            : plan == null ? null : read(plan).baseline();
    check(baseline != null, "Choose explicit starting evidence");
    check(
        input.endDate() != null
            && input.endDate().isAfter(baseline.date())
            && ChronoUnit.DAYS.between(baseline.date(), input.endDate()) <= 90,
        "Choose a horizon of 1–90 days after the starting evidence date");
    long reserve = amount(input.reserveEuro(), false);
    var stored =
        new Stored(
            text(input.name(), 100, "Plan name"),
            baseline,
            input.endDate(),
            reserve,
            events(input.events(), baseline, input.endDate()));
    // Validate arithmetic before persisting any edit.
    projection(stored, stored.events(), false);
    if (plan == null) {
      plan = new ForecastPlan();
      plan.id = "household";
    }
    try {
      plan.payload = json.writeValueAsString(stored);
    } catch (Exception e) {
      throw new IllegalStateException("Cannot save forecast");
    }
    plan.updatedAt = clock.instant();
    repository.saveAndFlush(plan);
    return workspace(plan);
  }

  private LocalDate suggestedLedgerDate() {
    var cash =
        ledger.position(today()).accounts().stream().filter(a -> a.kind().equals("CASH")).toList();
    if (cash.isEmpty() || cash.get(0).reconciledDate() == null) return null;
    LocalDate date = cash.get(0).reconciledDate();
    if (cash.stream().anyMatch(a -> !date.equals(a.reconciledDate()))) return null;
    try {
      return ledger.position(date).accounts().stream()
              .filter(a -> a.kind().equals("CASH"))
              .allMatch(LedgerService.AccountView::reconciled)
          ? date
          : null;
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  @Transactional(readOnly = true)
  public Workspace get() {
    return workspace(repository.findById("household").orElse(null));
  }

  private Workspace workspace(ForecastPlan plan) {
    return new Workspace(today(), suggestedLedgerDate(), plan == null ? null : view(plan));
  }

  private View view(ForecastPlan plan) {
    var stored = read(plan);
    List<String> warnings = new ArrayList<>();
    Baseline b = stored.baseline();
    String state = "REVIEWED";
    if (b.mode().equals("MANUAL")) {
      state = "ESTIMATED";
      warnings.add(
          "Manual starting cash is an estimate. This projection does not verify bank funds.");
    } else {
      var p = ledger.position(b.date());
      Map<String, Long> current = new TreeMap<>();
      p.accounts().stream()
          .filter(a -> a.kind().equals("CASH"))
          .forEach(a -> current.put(a.id(), a.revision()));
      if (current.isEmpty()
          || !current.equals(b.revisions())
          || p.cashMinor() != b.cashMinor()
          || p.cashPendingDebitMinor() != b.pendingMinor()
          || p.accounts().stream()
              .filter(a -> a.kind().equals("CASH"))
              .anyMatch(a -> !a.reconciled())) {
        state = "SOURCE_CHANGED";
        warnings.add(
            "Cash ledger evidence changed or is no longer reconciled. Replace the starting evidence"
                + " after reviewing actuals.");
      }
    }
    if (b.date().isBefore(today())) {
      warnings.add(
          "Starting cash is dated "
              + b.date()
              + "; subsequent actual activity is not included automatically.");
      if (state.equals("REVIEWED")) state = "DATED";
    }
    if (ChronoUnit.DAYS.between(b.date(), today()) > 7 || stored.endDate().isBefore(today())) {
      warnings.add(
          "Forecast evidence or horizon is stale. Review actuals and replace the starting"
              + " evidence.");
      if (!state.equals("SOURCE_CHANGED")) state = "STALE";
    }
    for (var e : stored.events())
      if (e.date().isBefore(today()))
        warnings.add(
            e.label()
                + ": overdue expectation; unpaid expenses stay reserved, unreceived income is"
                + " excluded until retimed or reflected in new starting evidence.");
    warnings.add(
        "Expected income is a projection, not received cash. Outflows occur before income on the"
            + " same date. Uncertain income is excluded from the base case.");
    warnings.add(
        "Pending debits are reserved once at the start. Do not add the same bill again or include"
            + " transfers between your own cash accounts.");
    return new View(
        plan.version + 1,
        plan.updatedAt,
        stored,
        state,
        state.equals("REVIEWED"),
        List.copyOf(warnings),
        projection(stored, stored.events(), false));
  }

  @Transactional(readOnly = true)
  public Comparison compare(WhatIf input) {
    var plan =
        repository
            .findById("household")
            .orElseThrow(
                () -> new IllegalArgumentException("Save reviewed plan inputs before comparing"));
    var view = view(plan);
    Stored stored = view.inputs();
    check(
        input != null
            && input.revision() != null
            && input.revision() == view.revision()
            && input.includeUncertain() != null,
        "Plan changed or scenario choices are incomplete; reload before comparing");
    long expense = amount(input.expenseEuro(), false);
    List<Event> scenario = new ArrayList<>(stored.events());
    if (expense > 0) {
      check(
          input.expenseDate() != null
              && input.expenseDate().isAfter(stored.baseline().date())
              && !input.expenseDate().isAfter(stored.endDate()),
          "Proposed purchase must be inside the forecast horizon");
      scenario.add(
          new Event(
              "what-if",
              input.expenseDate(),
              "Proposed purchase",
              "EXPENSE",
              "EXPECTED",
              expense,
              "Unsaved what-if"));
    }
    boolean delay = input.delayedIncomeId() != null && !input.delayedIncomeId().isBlank();
    check(
        delay == (input.delayedUntil() != null),
        "Select both the income to delay and its new date");
    if (delay) {
      var original =
          stored.events().stream()
              .filter(
                  e ->
                      e.id().equals(input.delayedIncomeId())
                          && e.direction().equals("INCOME")
                          && e.certainty().equals("EXPECTED"))
              .findFirst()
              .orElseThrow(
                  () ->
                      new IllegalArgumentException(
                          "Choose an expected income from this saved plan"));
      check(
          !input.delayedUntil().isBefore(original.date())
              && !input.delayedUntil().isAfter(stored.endDate()),
          "Delayed income date must be on/after its original date and inside the horizon");
      scenario.remove(original);
      scenario.add(
          new Event(
              original.id(),
              input.delayedUntil(),
              original.label(),
              original.direction(),
              original.certainty(),
              original.amountMinor(),
              original.source()));
    }
    List<String> warnings = new ArrayList<>(view.warnings());
    warnings.add("What-if is unsaved and changes no ledger balances or plan inputs.");
    if (input.includeUncertain())
      warnings.add(
          "Upside only: this scenario includes uncertain income and must not be treated as funds"
              + " available now.");
    return new Comparison(
        view.revision(),
        view.evidenceState(),
        view.currentDecisionReady() && !input.includeUncertain(),
        List.copyOf(warnings),
        view.projection(),
        projection(stored, scenario, input.includeUncertain()),
        view);
  }

  private Projection projection(Stored stored, List<Event> events, boolean includeUncertain) {
    long balance = add(stored.baseline().cashMinor(), -stored.baseline().pendingMinor()),
        start = balance,
        low = balance,
        totalOut = 0,
        expected = 0,
        uncertain = 0,
        applied = 0;
    LocalDate lowDate = stored.baseline().date(),
        shortfall = balance < 0 ? lowDate : null,
        breach = balance < stored.reserveMinor() ? lowDate : null;
    List<Day> days = new ArrayList<>();
    for (LocalDate date = stored.baseline().date().plusDays(1);
        !date.isAfter(stored.endDate());
        date = date.plusDays(1)) {
      LocalDate day = date;
      var daily =
          events.stream()
              .filter(e -> e.date().equals(day))
              .sorted(Comparator.comparing(Event::direction).thenComparing(Event::id))
              .toList();
      long opening = balance, out = 0, in = 0, maybe = 0;
      for (var e : daily) {
        if (e.direction().equals("EXPENSE")) out = add(out, e.amountMinor());
        else if (e.certainty().equals("UNCERTAIN")) maybe = add(maybe, e.amountMinor());
        else in = add(in, e.amountMinor());
      }
      long dailyLow = add(balance, -out);
      if (dailyLow < low) {
        low = dailyLow;
        lowDate = date;
      }
      if (shortfall == null && dailyLow < 0) shortfall = date;
      if (breach == null && dailyLow < stored.reserveMinor()) breach = date;
      // Never assume an overdue planned receipt actually arrived.
      long incoming = date.isBefore(today()) ? 0 : add(in, includeUncertain ? maybe : 0);
      balance = add(dailyLow, incoming);
      totalOut = add(totalOut, out);
      expected = add(expected, in);
      uncertain = add(uncertain, maybe);
      applied = add(applied, incoming);
      days.add(
          new Day(
              date,
              opening,
              out,
              in,
              maybe,
              incoming,
              dailyLow,
              balance,
              Math.max(0, add(dailyLow, -stored.reserveMinor())),
              daily));
    }
    return new Projection(
        start,
        totalOut,
        expected,
        uncertain,
        applied,
        balance,
        low,
        lowDate,
        shortfall,
        breach,
        Math.max(0, add(add(start, -totalOut), -stored.reserveMinor())),
        Math.max(0, add(Math.min(start, low), -stored.reserveMinor())),
        List.copyOf(days));
  }
}
