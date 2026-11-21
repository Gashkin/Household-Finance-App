package com.householdfinance.ledger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.householdfinance.access.AccessContext;
import com.householdfinance.service.DebtScenarioService;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Statement evidence describes debt; only the canonical ledger moves money. */
@Service
public class DebtService {
  public record Rate(LocalDate from, String annualPercent) {}

  public record Terms(
      String accountId,
      String name,
      String kind,
      String paymentAccountId,
      String creditLimitEuro,
      String monthlyPaymentEuro,
      String monthlyFeeEuro,
      LocalDate maturity,
      String extraLimitEuro,
      String extraFeeEuro,
      String lenderAssumptions,
      List<Rate> rates) {}

  public record Statement(
      String id,
      LocalDate start,
      LocalDate end,
      LocalDate due,
      String source,
      boolean complete,
      String openingEuro,
      String borrowingEuro,
      String interestEuro,
      String feesEuro,
      String paidEuro,
      String balanceEuro,
      String minimumEuro,
      String supersedes) {}

  public record Debt(String id, Terms terms, List<Statement> statements) {}

  public record State(long revision, List<Debt> debts, Map<String, String> operations) {}

  public record Change(
      Long revision,
      String operationId,
      String reason,
      String id,
      Terms terms,
      Statement statement) {}

  public record Row(
      Debt debt,
      Long outstandingMinor,
      Long statementDifferenceMinor,
      boolean reconciled,
      long remainingPaymentMinor,
      Long paymentCashMinor,
      List<String> warnings) {}

  public record Workspace(
      LocalDate today,
      State state,
      List<Row> rows,
      List<LedgerService.AccountView> accounts,
      List<String> warnings) {}

  public record Compare(Long revision, String debtId, String extraEuro, String reserveEuro) {}

  public record Payment(
      LocalDate date,
      long openingMinor,
      String annualPercent,
      long principalMinor,
      long interestMinor,
      long feesMinor,
      long paymentMinor,
      long closingMinor) {}

  public record Schedule(
      List<Payment> payments,
      long interestMinor,
      long feesMinor,
      long totalPaidMinor,
      long remainingMinor,
      boolean paidOff) {}

  public record Comparison(
      String name,
      LocalDate evidenceDate,
      boolean reconciled,
      Schedule baseline,
      Schedule extra,
      DebtScenarioService.Result fixedRateComparison,
      long netSavingMinor,
      long remainingCashMinor,
      long reserveShortfallMinor,
      List<String> warnings,
      List<String> assumptions,
      Compare input,
      Terms terms,
      long principalMinor,
      long extraFeeMinor) {}

  private final LedgerReviewRepository records;
  private final LedgerAuditRepository audits;
  private final LedgerService ledger;
  private final LedgerEntryRepository entries;
  private final ObjectMapper json;
  private final DebtScenarioService scenarios;

  public DebtService(
      LedgerReviewRepository r,
      LedgerAuditRepository a,
      LedgerService l,
      LedgerEntryRepository e,
      ObjectMapper j,
      DebtScenarioService s) {
    records = r;
    audits = a;
    ledger = l;
    entries = e;
    json = j;
    scenarios = s;
  }

  private LocalDate today() {
    return LocalDate.now(ZoneId.of("Europe/Dublin"));
  }

  private void check(boolean b, String m) {
    if (!b) throw new IllegalArgumentException(m);
  }

  private String uuid(String s) {
    try {
      return UUID.fromString(s).toString();
    } catch (Exception e) {
      throw new IllegalArgumentException("A stable UUID is required");
    }
  }

  private String text(String s, int max) {
    check(
        s != null && !s.isBlank() && s.strip().length() <= max,
        "A bounded name, source, reason and lender assumptions are required");
    return s.strip();
  }

  private void date(LocalDate d) {
    check(
        d != null && d.getYear() >= 2000 && d.getYear() <= 2200, "Choose a date from 2000 to 2200");
  }

  private long amount(String s) {
    long n = LedgerService.money(s);
    check(n >= 0, "Use a nonnegative amount");
    return n;
  }

  private String encode(Object o) {
    try {
      return json.writeValueAsString(o);
    } catch (Exception e) {
      throw new IllegalStateException("Cannot encode debt evidence", e);
    }
  }

  private String hash(Object o) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(encode(o).getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private String stateId() {
    return UUID.nameUUIDFromBytes(
            (AccessContext.userId() + ":debts").getBytes(StandardCharsets.UTF_8))
        .toString();
  }

  private State read() {
    AccessContext.requireRead();
    var r = records.findById(stateId()).orElse(null);
    if (r == null) return new State(0, List.of(), Map.of());
    check(AccessContext.owns(r.ownerId) && r.kind.equals("DEBTS"), "Invalid debt ownership");
    try {
      return json.readValue(r.payload, State.class);
    } catch (Exception e) {
      throw new IllegalStateException("Invalid debt evidence; restore a verified backup", e);
    }
  }

  private LedgerService.AccountView account(String id, LocalDate day, String kind) {
    return ledger.position(day).accounts().stream()
        .filter(a -> a.id().equals(id) && a.owned() && a.kind().equals(kind))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Choose an owned " + kind + " account available at the evidence date"));
  }

  private BigDecimal rate(String s) {
    check(
        s != null && s.matches("(?:0|[1-9][0-9]*)(?:\\.[0-9]{1,6})?"),
        "Use a decimal annual percentage with up to six places");
    var n = new BigDecimal(s);
    check(n.compareTo(BigDecimal.valueOf(100)) <= 0, "Annual rate must be 0 to 100 percent");
    return n;
  }

  private void terms(Terms t, Debt old) {
    check(t != null, "Complete debt terms required");
    uuid(t.accountId());
    uuid(t.paymentAccountId());
    text(t.name(), 100);
    text(t.lenderAssumptions(), 1000);
    check(
        Set.of("CARD", "LOAN", "MORTGAGE").contains(Objects.toString(t.kind(), "")),
        "Choose card, loan or mortgage");
    account(t.accountId(), today(), "DEBT");
    account(t.paymentAccountId(), today(), "CASH");
    amount(t.creditLimitEuro());
    amount(t.monthlyPaymentEuro());
    amount(t.monthlyFeeEuro());
    amount(t.extraLimitEuro());
    amount(t.extraFeeEuro());
    date(t.maturity());
    check(
        t.rates() != null && !t.rates().isEmpty() && t.rates().size() <= 100,
        "Provide 1 to 100 dated rates");
    LocalDate last = null;
    for (var x : t.rates()) {
      check(x != null, "Complete rate required");
      date(x.from());
      rate(x.annualPercent());
      check(last == null || x.from().isAfter(last), "Rate dates must increase");
      last = x.from();
    }
    if (!t.kind().equals("CARD"))
      check(amount(t.monthlyPaymentEuro()) > 0, "Contractual monthly repayment required");
    if (old != null) {
      check(
          old.terms().accountId().equals(t.accountId()) && old.terms().kind().equals(t.kind()),
          "Retain the debt account and type");
      var prior = old.terms().rates();
      check(
          t.rates().size() >= prior.size() && t.rates().subList(0, prior.size()).equals(prior),
          "Retain dated rate history; append a new effective rate");
      for (var x : t.rates().subList(prior.size(), t.rates().size()))
        check(!x.from().isBefore(today()), "New rates cannot rewrite past assumptions");
    }
  }

  private void statement(Statement s, Debt old, Terms t) {
    uuid(s.id());
    date(s.start());
    date(s.end());
    date(s.due());
    text(s.source(), 200);
    check(
        !s.end().isBefore(s.start()) && !s.end().isAfter(today()) && !s.due().isBefore(s.end()),
        "Statement cycle must end by today and precede its due date");
    account(t.accountId(), s.start(), "DEBT");
    long balance = LedgerService.money(s.balanceEuro());
    check(
        old == null || old.statements().stream().noneMatch(x -> x.id().equals(s.id())),
        "Statement ID already exists");
    if (s.supersedes() != null) {
      check(old != null && !old.statements().isEmpty(), "No statement to correct");
      var prior = old.statements().getLast();
      check(
          prior.id().equals(s.supersedes())
              && prior.start().equals(s.start())
              && prior.end().equals(s.end()),
          "Correction must supersede the latest exact cycle; prior evidence is retained");
    } else
      check(
          old == null || s.start().isAfter(old.statements().getLast().end()),
          "Statement cycles overlap; explicitly correct the latest statement instead");
    check(
        !s.due().isAfter(t.maturity()),
        "Maturity or card planning end must cover the payment due date");
    for (String v :
        Arrays.asList(
            s.borrowingEuro(), s.interestEuro(), s.feesEuro(), s.paidEuro(), s.minimumEuro()))
      if (v != null && !v.isBlank()) amount(v);
    if (s.openingEuro() != null && !s.openingEuro().isBlank()) LedgerService.money(s.openingEuro());
    if (s.complete()) {
      long opening = LedgerService.money(s.openingEuro()),
          draw = amount(s.borrowingEuro()),
          interest = amount(s.interestEuro()),
          fees = amount(s.feesEuro()),
          paid = amount(s.paidEuro()),
          minimum = amount(s.minimumEuro());
      check(
          opening + draw + interest + fees - paid == balance,
          "Statement components do not reconcile to its closing balance");
      check(minimum <= Math.max(0, balance), "Minimum cannot exceed a positive statement balance");
    }
    check(
        !t.rates().getFirst().from().isAfter(s.end()),
        "Rate history must cover the statement closing date");
  }

  @Transactional
  public State change(Change in) {
    AccessContext.requireEdit();
    check(in != null, "Complete debt change required");
    var old = read();
    String op = uuid(in.operationId()), digest = hash(in);
    if (old.operations().containsKey(op)) {
      check(
          old.operations().get(op).equals(digest),
          "Operation ID already used with different input");
      return old;
    }
    check(
        in.revision() != null && in.revision() == old.revision(),
        "Debt evidence changed; compare saved values with your retained draft");
    check(old.operations().size() < 2000, "Debt operation capacity reached");
    String id = uuid(in.id()), why = text(in.reason(), 200);
    var before = old.debts().stream().filter(d -> d.id().equals(id)).findFirst().orElse(null);
    terms(in.terms(), before);
    check(
        old.debts().stream()
            .noneMatch(
                d -> !d.id().equals(id) && d.terms().accountId().equals(in.terms().accountId())),
        "This debt account already has a statement record");
    var statements = new ArrayList<Statement>();
    if (before != null) statements.addAll(before.statements());
    if (in.statement() != null) {
      statement(in.statement(), before, in.terms());
      statements.add(in.statement());
    }
    check(
        statements.size() <= 240 && (!statements.isEmpty()),
        "Provide statement evidence (up to 240 cycles)");
    check(
        !in.terms().maturity().isBefore(statements.getLast().due()),
        "Maturity or planning end must cover the latest statement due date");
    var debts = new ArrayList<>(old.debts());
    if (before != null) debts.remove(before);
    check(debts.size() < 100, "Debt capacity reached");
    debts.add(new Debt(id, in.terms(), List.copyOf(statements)));
    var operations = new LinkedHashMap<>(old.operations());
    operations.put(op, digest);
    var next = new State(old.revision() + 1, List.copyOf(debts), Map.copyOf(operations));
    var r = records.findById(stateId()).orElseGet(LedgerReviewRecord::new);
    r.id = stateId();
    r.ownerId = AccessContext.userId();
    r.kind = "DEBTS";
    r.revision = next.revision();
    r.payload = encode(next);
    records.save(r);
    var a = new LedgerAudit();
    a.id = UUID.randomUUID().toString();
    a.accountId = AccessContext.publicationId();
    a.recordedAt = Instant.now();
    a.action = "DEBT_CHANGE";
    a.source = why;
    a.beforeJson = encode(old);
    a.afterJson = encode(next);
    audits.save(a);
    return next;
  }

  private long paid(Debt d, LocalDate after, LocalDate through) {
    long paid = 0;
    for (var e : ledger.activity(d.terms().accountId()))
      if (e.date.isAfter(after)
          && !e.date.isAfter(through)
          && e.amountMinor > 0
          && e.status.equals("BOOKED")
          && e.reviewState.equals("CONFIRMED")
          && LedgerService.isTransferRelationship(e)
          && e.linkedEntryId != null) {
        var peer = entries.findById(e.linkedEntryId).orElse(null);
        if (peer != null
            && peer.accountId.equals(d.terms().paymentAccountId())
            && peer.status.equals("BOOKED")
            && peer.reviewState.equals("CONFIRMED")
            && Objects.equals(peer.linkedEntryId, e.id)
            && peer.amountMinor == -e.amountMinor
            && LedgerService.isTransferRelationship(peer))
          paid = Math.addExact(paid, e.amountMinor);
      }
    return paid;
  }

  private Row row(Debt d) {
    var s = d.statements().getLast();
    var notes = new ArrayList<String>();
    var a = account(d.terms().accountId(), today(), "DEBT");
    var historical = account(d.terms().accountId(), s.end(), "DEBT");
    long diff = historical.bookedMinor() + LedgerService.money(s.balanceEuro());
    boolean ok =
        s.complete() && diff == 0 && historical.reconciled() && historical.pendingEntries() == 0;
    if (!s.complete())
      notes.add(
          "Incomplete statement: estimates only; reconcile all statement components before relying"
              + " on repayment figures.");
    if (diff != 0)
      notes.add(
          "Statement differs from ledger. Review activity and reconcile in Money position; saving"
              + " evidence never adjusts balances.");
    if (!historical.reconciled())
      notes.add("Statement date/revision is not reconciled in Money position.");
    if (s.end().isBefore(today().minusDays(45)))
      notes.add("Statement evidence is more than 45 days old.");
    if (a.bookedMinor() > 0)
      notes.add("Lender credit balance: no repayment required; it is not household cash.");
    long planned =
        d.terms().kind().equals("CARD")
            ? Math.max(0, LedgerService.money(s.balanceEuro()))
            : amount(d.terms().monthlyPaymentEuro());
    long remaining = Math.max(0, planned - paid(d, s.end(), today()));
    remaining = Math.min(remaining, Math.max(0, -a.bookedMinor()));
    var cash = account(d.terms().paymentAccountId(), today(), "CASH");
    long liquid = cash.bookedMinor() - cash.pendingDebitMinor();
    if (remaining > Math.max(0, liquid))
      notes.add(
          "Repayment shortfall on the payment account (after pending debits); overdraft capacity"
              + " and credit limits are not cash.");
    if (d.terms().kind().equals("CARD")
        && s.minimumEuro() != null
        && !s.minimumEuro().isBlank()
        && paid(d, s.end(), today()) < amount(s.minimumEuro())
        && s.due().isBefore(today()))
      notes.add("Minimum payment overdue or not yet linked to reviewed settlement evidence.");
    if (!d.terms().kind().equals("CARD") && s.due().isBefore(today()) && remaining > 0)
      notes.add(
          "Contractual payment overdue or not linked to reviewed settlement evidence; comparison"
              + " begins from current booked debt, not a lender arrears quote.");
    notes.add(
        "Purchases count as spending; linked settlements count once as transfers. Record and link"
            + " both actual legs in Review & reconcile. Do not also enter these repayments as"
            + " separate bills.");
    return new Row(d, -a.bookedMinor(), diff, ok, remaining, liquid, List.copyOf(notes));
  }

  @Transactional(readOnly = true)
  public Workspace get() {
    var state = read();
    return new Workspace(
        today(),
        state,
        state.debts().stream().map(this::row).toList(),
        ledger.position(today()).accounts().stream()
            .filter(LedgerService.AccountView::owned)
            .toList(),
        List.of(
            "Statement amounts owed are positive; lender credits are negative. Ledger debt balances"
                + " use the opposite sign. Credit limits never increase cash.",
            "No payments or lender actions. Statement and term history are included in encrypted"
                + " workspace backups."));
  }

  private Rate effective(Terms t, LocalDate date) {
    return t.rates().stream()
        .filter(x -> !x.from().isAfter(date))
        .reduce((a, b) -> b)
        .orElseThrow(
            () -> new IllegalArgumentException("Rate history does not cover repayment date"));
  }

  private Schedule schedule(Debt d, LocalDate first, long balance, long extra) {
    var t = d.terms();
    var rows = new ArrayList<Payment>();
    long remaining = Math.max(0, balance - extra), interestTotal = 0, feesTotal = 0, total = 0;
    for (int i = 0; i < 1200 && remaining > 0; i++) {
      LocalDate date = first.plusMonths(i);
      if (date.isAfter(t.maturity())) break;
      var rate = effective(t, date);
      long
          interest =
              DebtScenarioService.monthlyInterest(remaining, new BigDecimal(rate.annualPercent())),
          fees = amount(t.monthlyFeeEuro()),
          due = remaining + interest + fees,
          payment = Math.min(amount(t.monthlyPaymentEuro()), due);
      long principal = payment - interest - fees, closing = remaining - principal;
      check(
          closing <= 9_007_199_254_740_991L
              && interestTotal + interest <= 9_007_199_254_740_991L
              && total + payment <= 9_007_199_254_740_991L,
          "Repayment model exceeds safe planning range; review payment and rate assumptions");
      rows.add(
          new Payment(
              date, remaining, rate.annualPercent(), principal, interest, fees, payment, closing));
      remaining = closing;
      interestTotal += interest;
      feesTotal += fees;
      total += payment;
    }
    return new Schedule(
        List.copyOf(rows), interestTotal, feesTotal, total, remaining, remaining == 0);
  }

  @Transactional(readOnly = true)
  public Comparison compare(Compare in) {
    check(in != null && in.revision() != null, "Saved revision required");
    var state = read();
    check(in.revision() == state.revision(), "Debt evidence changed; refresh before comparing");
    var d =
        state.debts().stream()
            .filter(x -> x.id().equals(in.debtId()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown debt"));
    check(
        !d.terms().kind().equals("CARD"),
        "Use card statement balance and due date; loan comparison requires a contractual monthly"
            + " payment");
    var s = d.statements().getLast();
    var row = row(d);
    long balance = Math.max(0, row.outstandingMinor()),
        extra = amount(in.extraEuro()),
        reserve = amount(in.reserveEuro()),
        fee = extra == 0 ? 0 : amount(d.terms().extraFeeEuro());
    check(
        balance > 0 && extra <= balance && extra <= amount(d.terms().extraLimitEuro()),
        "Extra payment exceeds current debt or the stated lender allowance");
    long cash = Math.max(0, row.paymentCashMinor());
    check(extra + fee <= cash, "Extra payment and lender fee exceed cash after pending debits");
    int skipped = 0;
    while (s.due().plusMonths(skipped).isBefore(today())) skipped++;
    LocalDate first = s.due().plusMonths(skipped);
    check(
        !first.isAfter(d.terms().maturity()),
        "Review maturity and overdue contractual payments before comparing");
    var base = schedule(d, first, balance, 0);
    var alt = schedule(d, first, balance, extra);
    DebtScenarioService.Result fixed = null;
    var t = d.terms();
    boolean constant = t.rates().stream().noneMatch(x -> x.from().isAfter(today()));
    if (constant && amount(t.monthlyFeeEuro()) == 0 && base.paidOff()) {
      fixed =
          scenarios.compare(
              new DebtScenarioService.Input(
                  balance,
                  new BigDecimal(effective(t, today()).annualPercent()),
                  amount(t.monthlyPaymentEuro()),
                  extra,
                  cash - fee,
                  reserve));
      check(
          base.payments().size() == fixed.baseline().months()
              && alt.payments().size() == fixed.withExtraPayment().months(),
          "Contractual rows differ from the fixed-rate engine");
      base =
          new Schedule(
              base.payments(),
              fixed.baseline().interestMinor(),
              0,
              fixed.baseline().totalPaidMinor(),
              0,
              true);
      alt =
          new Schedule(
              alt.payments(),
              fixed.withExtraPayment().interestMinor(),
              0,
              fixed.withExtraPayment().totalPaidMinor(),
              0,
              true);
    }
    var warnings = new ArrayList<>(row.warnings());
    if (base.payments().stream().anyMatch(p -> p.principalMinor() <= 0))
      warnings.add("Contractual payment does not reduce principal at a dated rate; debt can grow.");
    if (!base.paidOff() || !alt.paidOff())
      warnings.add(
          "Contractual maturity leaves a balloon balance. Obtain a lender payoff quote; savings are"
              + " not a like-for-like full payoff comparison.");
    if (!row.reconciled())
      warnings.add(
          "Illustrative comparison: statement evidence is incomplete, mismatched or unreconciled.");
    long left = row.paymentCashMinor() - extra - fee;
    if (left < reserve)
      warnings.add("Extra repayment leaves the payment account below the chosen cash reserve.");
    return new Comparison(
        t.name(),
        s.end(),
        row.reconciled(),
        base,
        alt,
        fixed,
        base.interestMinor() + base.feesMinor() - alt.interestMinor() - alt.feesMinor() - fee,
        left,
        Math.max(0, reserve - left),
        List.copyOf(warnings),
        List.of(
            t.lenderAssumptions(),
            "Monthly model: dated rate effective on payment date, annual percent / 12; interest"
                + " rounded half-up to cents. This is not daily lender accrual.",
            "Extra principal applied immediately; stated extra fee paid from cash. Monthly fees and"
                + " contractual maturity retained. No tax, offset, insurance or unentered future"
                + " rates.",
            "Uses current booked ledger debt and payment-account cash after pending debits. Pending"
                + " debt activity excluded. No account balances or lender instructions change."),
        in,
        t,
        balance,
        fee);
  }

  /** Source-only cash debit: no fictional cash credit at the lender and no purchase spending. */
  @Transactional(readOnly = true)
  public List<CommitmentService.Occurrence> obligations(
      LocalDate from, LocalDate end, LocalDate baseline) {
    var out = new ArrayList<CommitmentService.Occurrence>();
    for (var d : read().debts()) {
      var s =
          d.statements().stream()
              .filter(x -> !x.end().isAfter(baseline))
              .reduce((a, b) -> b)
              .orElse(null);
      if (s == null) continue;
      var t = d.terms();
      var a = account(t.accountId(), baseline, "DEBT");
      if (a.bookedMinor() >= 0) continue;
      long credits = paid(d, s.end(), baseline),
          balance = Math.max(0, LedgerService.money(s.balanceEuro()));
      var payments = new ArrayList<Payment>();
      if (t.kind().equals("CARD"))
        payments.add(new Payment(s.due(), balance, "0", balance, 0, 0, balance, 0));
      else {
        var contract = schedule(d, s.due(), balance, 0);
        payments.addAll(contract.payments());
        if (!contract.paidOff())
          payments.add(
              new Payment(
                  t.maturity(),
                  contract.remainingMinor(),
                  "BALLOON",
                  contract.remainingMinor(),
                  0,
                  0,
                  contract.remainingMinor(),
                  0));
      }
      for (var payment : payments) {
        LocalDate date = payment.date();
        if (date.isAfter(end)) break;
        long amount = payment.paymentMinor();
        long used = Math.min(amount, credits);
        credits -= used;
        amount -= used;
        if (t.kind().equals("CARD")) amount = Math.min(amount, -a.bookedMinor());
        if (amount == 0 || date.isBefore(from)) continue;
        var warnings = new ArrayList<String>();
        warnings.add(
            "Debt statement expectation; do not duplicate as a bill. Linked reviewed settlements"
                + " reduce it once. Monthly rate/fee model; obtain updated statements for lender"
                + " accrual and changed terms.");
        if (date.equals(t.maturity()) && !t.kind().equals("CARD"))
          warnings.add(
              "Contractual maturity: any remaining principal is reserved as a balloon. Obtain a"
                  + " lender payoff quote for final accrual and fees.");
        if (!s.complete()) warnings.add("Incomplete debt statement; repayment is illustrative.");
        if (account(t.accountId(), s.end(), "DEBT").bookedMinor()
                + LedgerService.money(s.balanceEuro())
            != 0)
          warnings.add(
              "Debt statement differs from ledger; review reconciliation before relying on"
                  + " outlook.");
        out.add(
            new CommitmentService.Occurrence(
                "debt:"
                    + d.id()
                    + "/"
                    + date
                    + (payment.annualPercent().equals("BALLOON") ? "/balloon" : ""),
                "debt:" + d.id(),
                date,
                date,
                t.name() + " repayment",
                t.paymentAccountId(),
                null,
                "TRANSFER",
                "EXPECTED",
                "ESTIMATED",
                amount,
                null,
                date.isBefore(today()) ? "DATA_UNCONFIRMED" : "UPCOMING",
                null,
                null,
                false,
                List.copyOf(warnings)));
      }
    }
    return List.copyOf(out);
  }

  public record Report(Instant generatedAt, Workspace statementEvidence, Comparison comparison) {}

  @Transactional(readOnly = true)
  public Report report(Compare in) {
    return new Report(Instant.now(), get(), in == null ? null : compare(in));
  }

  @Transactional(readOnly = true)
  public Map<String, Boolean> operation(String id) {
    return Map.of("completed", read().operations().containsKey(uuid(id)));
  }

  @Transactional(readOnly = true)
  public List<LedgerAudit> history() {
    AccessContext.requireRead();
    return audits.findAll().stream()
        .filter(a -> a.actorId.equals(AccessContext.userId()) && a.action.equals("DEBT_CHANGE"))
        .sorted(Comparator.comparing(a -> a.recordedAt))
        .toList();
  }
}
