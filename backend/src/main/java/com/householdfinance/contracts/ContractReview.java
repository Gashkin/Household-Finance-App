package com.householdfinance.contracts;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

final class ContractReview {
  enum Kind { CONTRACT, SUBSCRIPTION }
  enum TermType { FIXED, ROLLING }
  enum Certainty { VERIFIED, ESTIMATED, UNKNOWN }
  enum Frequency { WEEKLY, MONTHLY, QUARTERLY, YEARLY, ONE_OFF }
  enum Usage { HIGH, MEDIUM, LOW, UNUSED, UNKNOWN }
  enum Closing { KEEP, CONSIDER_CANCELLING, LIKELY_CANCEL, CANCELLED, UNDECIDED }
  enum Decision { KEEP, RENEW, SWITCH, CANCEL, UNDECIDED }
  enum Outcome { RENEWED, SWITCHED, CANCELLED }
  enum Disposition { UNREAD, READ, DISMISSED, SNOOZED }
  enum Stage { UPCOMING, NEEDS_DATE, DUE, OVERDUE, COMPLETED }
  enum Status { UPCOMING, ACTIVE, CLOSED }

  record DatedEvidence(Certainty certainty, LocalDate date, String source, String note) {}
  record Price(String amount, String currency, LocalDate effectiveFrom) {}
  record References(String billId, String accountId, String transactionId) {}
  record Terms(String provider, String plan, TermType type, LocalDate start, Boolean reviewEnabled,
      DatedEvidence termEnd, DatedEvidence notice, DatedEvidence promotion, DatedEvidence priceChange,
      DatedEvidence nextCharge, LocalDate reviewTarget, String reviewSource, String reviewNote,
      Frequency frequency, Price promotionalPrice, Price standardPrice, Price exitFee,
      String noticeTerms, References references) {
    List<DatedEvidence> cutoffs() { return List.of(termEnd, notice, promotion, priceChange); }

    Terms withReferences(References permitted) {
      return new Terms(provider, plan, type, start, reviewEnabled, termEnd, notice, promotion, priceChange,
          nextCharge, reviewTarget, reviewSource, reviewNote, frequency, promotionalPrice, standardPrice,
          exitFee, noticeTerms, permitted);
    }
  }

  record Assessment(Usage usage, Closing closing, String notes) {}
  record DecisionEntry(Decision decision, LocalDate date, String evidence) {}
  record Attempt(Decision action, LocalDate date, String evidence) {}
  record Confirmation(Outcome outcome, LocalDate date, LocalDate effective, String evidence) {}
  record TermRevision(LocalDate recordedOn, Terms previous) {}
  record Term(String id, Terms terms, List<TermRevision> revisions, List<DecisionEntry> decisions, List<Attempt> attempts,
      Confirmation confirmation) {
    static Term open(Terms terms) {
      return new Term(UUID.randomUUID().toString(), terms, List.of(), List.of(), List.of(), null);
    }

    Decision decision() {
      return decisions.isEmpty() ? Decision.UNDECIDED : decisions.getLast().decision();
    }

    Term edit(Terms replacement, LocalDate recordedOn) {
      if (terms.equals(replacement)) return this;
      return new Term(id, replacement, append(revisions, new TermRevision(recordedOn, terms)),
          decisions, attempts, confirmation);
    }

    Term hideReferences() {
      var safeHistory = revisions.stream()
          .map(revision -> new TermRevision(revision.recordedOn(), revision.previous().withReferences(null))).toList();
      return new Term(id, terms.withReferences(null), safeHistory, decisions, attempts, confirmation);
    }
  }

  record Record(String name, Kind kind, Assessment assessment, List<Term> terms) {
    Term latest() { return terms.getLast(); }

    Term asOf(LocalDate today) {
      return terms.reversed().stream().filter(term -> !term.terms().start().isAfter(today))
          .findFirst().orElse(terms.getFirst());
    }

    Status status(LocalDate today) {
      var term = asOf(today);
      if (term.terms().start().isAfter(today)) return Status.UPCOMING;
      var confirmation = term.confirmation();
      return confirmation != null && !confirmation.effective().isAfter(today)
          && confirmation.outcome() == Outcome.CANCELLED ? Status.CLOSED : Status.ACTIVE;
    }
  }

  record Deadline(LocalDate cutoff, LocalDate suggested, LocalDate target, Stage stage, String timing) {}

  static Deadline deadline(Term term, LocalDate today) {
    var details = term.terms();
    var cutoff = details.cutoffs().stream().filter(value -> value.certainty() == Certainty.VERIFIED)
        .map(DatedEvidence::date).min(LocalDate::compareTo).orElse(null);
    var suggested = cutoff == null ? null : cutoff.minusDays(30);
    var target = details.reviewTarget() == null ? suggested : details.reviewTarget();
    var stage = Stage.UPCOMING;
    if (term.confirmation() != null || term.decision() == Decision.KEEP) stage = Stage.COMPLETED;
    else if ((cutoff != null && cutoff.isBefore(today)) || (target != null && target.isBefore(today))) stage = Stage.OVERDUE;
    else if (today.equals(cutoff) || today.equals(target)) stage = Stage.DUE;
    else if (target == null) stage = Stage.NEEDS_DATE;
    return new Deadline(cutoff, suggested, target, stage, target + "/" + cutoff);
  }

  static Terms validate(Terms t) {
    require(t != null && t.type() != null && t.start() != null && t.reviewEnabled() != null, "Choose a term type, start date and review setting");
    var termEnd = dateEvidence(t.termEnd());
    var notice = dateEvidence(t.notice());
    var promotion = dateEvidence(t.promotion());
    var priceChange = dateEvidence(t.priceChange());
    var nextCharge = dateEvidence(t.nextCharge());
    date(t.start());
    date(t.reviewTarget());
    require(termEnd.date() == null || !termEnd.date().isBefore(t.start()), "Term end precedes the term start");
    return new Terms(required(t.provider(), 120, "Provider"), text(t.plan(), 120), t.type(), t.start(),
        t.reviewEnabled(), termEnd, notice, promotion, priceChange, nextCharge, t.reviewTarget(),
        text(t.reviewSource(), 180), text(t.reviewNote(), 500), t.frequency(), price(t.promotionalPrice()),
        price(t.standardPrice()), price(t.exitFee()), text(t.noticeTerms(), 1000), t.references());
  }

  private static DatedEvidence dateEvidence(DatedEvidence d) {
    if (d == null) return new DatedEvidence(Certainty.UNKNOWN, null, null, null);
    require(d.certainty() != null && (d.certainty() == Certainty.UNKNOWN) == (d.date() == null),
        "Verified and estimated dates need a date. Unknown dates must be empty.");
    date(d.date());
    var source = d.certainty() == Certainty.VERIFIED ? required(d.source(), 180, "Verification source") : text(d.source(), 180);
    return new DatedEvidence(d.certainty(), d.date(), source, text(d.note(), 500));
  }

  private static Price price(Price p) {
    if (p == null) return null;
    Currency currency;
    try { currency = Currency.getInstance(Objects.toString(p.currency(), "")); }
    catch (IllegalArgumentException e) { throw new IllegalArgumentException("Choose a valid currency such as EUR"); }
    require(p.amount() != null && p.amount().matches("[0-9]{1,9}([.][0-9]{1,4})?"), "Enter a valid non-negative price");
    var amount = new BigDecimal(p.amount());
    require(amount.scale() <= currency.getDefaultFractionDigits(), "Price has too many decimal places for its currency");
    date(p.effectiveFrom());
    return new Price(amount.toPlainString(), currency.getCurrencyCode(), p.effectiveFrom());
  }

  static Assessment assessment(Kind kind, Assessment value) {
    if (kind != Kind.SUBSCRIPTION) return null;
    if (value == null) return new Assessment(Usage.UNKNOWN, Closing.UNDECIDED, null);
    require(value.usage() != null && value.closing() != null, "Choose usage and cancellation assessments");
    return new Assessment(value.usage(), value.closing(), text(value.notes(), 1000));
  }

  static void date(LocalDate date) {
    require(date == null || date.getYear() >= 1900 && date.getYear() <= 9999, "Choose a date between 1900 and 9999");
  }

  static String required(String value, int max, String label) {
    var clean = text(value, max);
    require(clean != null, label + " is required");
    return clean;
  }

  static String text(String value, int max) {
    if (value == null || value.isBlank()) return null;
    var clean = value.strip();
    require(clean.length() <= max, "Text must be at most " + max + " characters");
    return clean;
  }

  static String uuid(String value) {
    require(value != null && value.matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}"), "A valid identifier is required");
    return value;
  }

  static <T> List<T> append(List<T> existing, T next) {
    require(existing.size() < 100, "Contract history limit reached");
    return Stream.concat(existing.stream(), Stream.of(next)).toList();
  }

  static void require(boolean condition, String message) {
    if (!condition) throw new IllegalArgumentException(message);
  }

  private ContractReview() {}
}
