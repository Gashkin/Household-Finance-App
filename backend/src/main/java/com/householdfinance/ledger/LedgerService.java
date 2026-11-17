package com.householdfinance.ledger;

import com.householdfinance.access.AccessContext;

import com.fasterxml.jackson.databind.*;
import com.householdfinance.service.CashPositionService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerService {
  public record AccountInput(
      String name, String kind, LocalDate openingDate, String openingEuro, String source) {}

  public record Row(
      String reference,
      LocalDate date,
      String description,
      String amountEuro,
      String status,
      String kind) {}

  public record PreviewInput(String csv, String source) {}

  public record Proposal(Row row, String disposition, boolean possibleOverlap) {}

  public record Preview(String id, long revision, Instant expiresAt, List<Proposal> proposals) {}

  public record Approval(Long revision, List<Row> rows, Boolean acknowledgedOverlap) {}

  public record Imported(int added, int duplicates, int settled, long revision) {}

  public record Manual(Long revision, Row row, String source, Boolean acknowledgedOverlap) {}

  public record Correction(Long revision, Row row, String reason) {}

  public record Reconcile(
      Long revision,
      String statementEuro,
      String source,
      Boolean commit,
      Boolean acceptAdjustment) {}

  public record Reconciliation(
      long expectedMinor, long statementMinor, long differenceMinor, boolean applied) {}

  public record AccountView(
      String id,
      String name,
      String kind,
      LocalDate openingDate,
      long openingMinor,
      String source,
      long revision,
      long bookedMinor,
      long pendingMinor,
      long pendingDebitMinor,
      int pendingEntries,
      long incomeMinor,
      long spendingMinor,
      LocalDate reconciledDate,
      String reconciledSource,
      boolean reconciled,
      int entries,
      List<String> warnings,
      boolean shared,
      boolean owned) {}

  public record Position(
      LocalDate asOf,
      List<AccountView> accounts,
      long cashMinor,
      long debtSignedMinor,
      long netMinor,
      long cashPendingDebitMinor,
      long incomeMinor,
      long spendingMinor,
      List<String> warnings) {}

  public record Commitment(String label, String amountEuro) {}

  public record Publish(
      String requestId,
      LocalDate asOf,
      Map<String, Long> revisions,
      String reserveEuro,
      List<Commitment> commitments,
      Boolean acknowledged) {}

  private static final class Draft {
    String account, source;
    long revision;
    Instant expires;
    List<Row> rows;
    String approvalHash;
    Imported result;
  }

  private final LedgerAccountRepository accounts;
  private final LedgerEntryRepository entries;
  private final LedgerAuditRepository audits;
  private final CashPositionService cash;
  private final ObjectMapper json;
  private final Map<String, Draft> drafts = new HashMap<>();
  @org.springframework.beans.factory.annotation.Autowired(required=false) private com.householdfinance.access.AccessService identityAccess;

  public LedgerService(
      LedgerAccountRepository accounts,
      LedgerEntryRepository entries,
      LedgerAuditRepository audits,
      CashPositionService cash,
      ObjectMapper json) {
    this.accounts = accounts;
    this.entries = entries;
    this.audits = audits;
    this.cash = cash;
    this.json = json.copy().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
  }

  private LocalDate today() {
    return LocalDate.now(ZoneId.of("Europe/Dublin"));
  }

  private void check(boolean test, String message) {
    if (!test) throw new IllegalArgumentException(message);
  }

  private String text(String value, int max, String name) {
    check(
        value != null && !value.isBlank() && value.strip().length() <= max,
        name + " is required (maximum " + max + " characters)");
    return value.strip();
  }

  static long money(String value) {
    if (value == null || !value.matches("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]{1,2})?"))
      throw new IllegalArgumentException(
          "Use signed EUR amounts with at most two decimal places; no commas, symbols or exponent");
    try {
      long amount =
          new BigDecimal(value)
              .movePointRight(2)
              .setScale(0, RoundingMode.UNNECESSARY)
              .longValueExact();
      if (amount < -1_000_000_000_000L || amount > 1_000_000_000_000L)
        throw new ArithmeticException();
      return amount;
    } catch (ArithmeticException e) {
      throw new IllegalArgumentException("Amount exceeds supported range");
    }
  }

  private long sum(long a, long b) {
    long n = Math.addExact(a, b);
    check(
        n >= -9_007_199_254_740_991L && n <= 9_007_199_254_740_991L,
        "Ledger total exceeds supported range");
    return n;
  }

  private LedgerAccount account(String id) {
    return accounts
        .findById(id)
        .filter(a -> AccessContext.reads(a.ownerId,a.shared))
        .orElseThrow(() -> new IllegalArgumentException("Unknown ledger account"));
  }

  private void revision(LedgerAccount a, Long revision) {
    AccessContext.requireEdit();
    check(AccessContext.owns(a.ownerId), "Only the account owner may change this account");
    check(revision != null && a.revision == revision, "Account changed; reload and review again");
  }

  private void unprotected(LedgerAccount a, LocalDate date) {
    check(a.reconciledDate == null || date.isAfter(a.reconciledDate),
        "Activity is protected through the statement date; reopen reconciliation with a reason first");
  }

  private Row normalize(Row r, LedgerAccount a) {
    return normalize(r, a, false);
  }

  private Row normalize(Row r, LedgerAccount a, boolean correction) {
    check(r != null, "Complete activity row required");
    String ref = text(r.reference(), 100, "Reference");
    String label = text(r.description(), 160, "Description");
    check(
        r.date() != null && !r.date().isBefore(a.openingDate) && !r.date().isAfter(today()),
        "Activity date must be on/after opening date and no later than today");
    long amount = money(r.amountEuro());
    check(amount != 0, "Activity amount must not be zero");
    check(
        (correction ? Set.of("BOOKED", "PENDING", "VOID") : Set.of("BOOKED", "PENDING"))
            .contains(Objects.toString(r.status(), "")),
        "Status must be BOOKED or PENDING");
    check(
        Set.of("ACTIVITY", "TRANSFER").contains(Objects.toString(r.kind(), "")),
        "Kind must be ACTIVITY or TRANSFER");
    return new Row(
        ref, r.date(), label, BigDecimal.valueOf(amount, 2).toPlainString(), r.status(), r.kind());
  }

  private String encode(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalStateException("Cannot record ledger evidence", e);
    }
  }

  private String hash(Object value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(encode(value).getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private LedgerAudit audit(
      String account, String action, String source, Object before, Object after) {
    var event = new LedgerAudit();
    event.id = UUID.randomUUID().toString();
    event.accountId = account;
    event.action = action;
    event.source = source;
    event.recordedAt = Instant.now();
    event.beforeJson = encode(before);
    event.afterJson = encode(after);
    return audits.save(event);
  }

  @Transactional
  public AccountView create(AccountInput input) {
    AccessContext.requireEdit();
    check(input != null, "Account required");
    check(accounts.findAll().stream().filter(a -> AccessContext.owns(a.ownerId)).count() < com.householdfinance.access.HouseholdLimits.ACCOUNTS_PER_USER, "Use at most 30 owned ledger accounts");
    var a = new LedgerAccount();
    a.id = UUID.randomUUID().toString();
    a.name = text(input.name(), 80, "Account name");
    check(
        Set.of("CASH", "DEBT").contains(Objects.toString(input.kind(), "")),
        "Account kind must be CASH or DEBT");
    a.kind = input.kind();
    check(
        input.openingDate() != null && !input.openingDate().isAfter(today()),
        "Opening date must be today or earlier");
    a.openingDate = input.openingDate();
    a.openingMinor = money(input.openingEuro());
    a.source = text(input.source(), 200, "Opening evidence");
    accounts.saveAndFlush(a);
    audit(a.id, "OPEN", a.source, null, input);
    return view(a, today());
  }

  @Transactional
  public void bindSandboxIdentity(String id, String identity) {
    AccessContext.requireEdit();
    check(identity != null && identity.matches("[0-9a-f]{64}"), "Invalid sandbox account identity");
    var a = account(id);
    check(AccessContext.owns(a.ownerId), "Bank linking requires the account owner");
    check(
        a.sandboxIdentity == null || a.sandboxIdentity.equals(identity),
        "This ledger is already linked to a different sandbox account");
    check(
        accounts.findAll().stream()
            .noneMatch(other -> !other.id.equals(id) && identity.equals(other.sandboxIdentity)),
        "This sandbox account is already mapped to another ledger; reuse that ledger");
    if (a.sandboxIdentity == null) {
      a.sandboxIdentity = identity;
      accounts.saveAndFlush(a);
    }
  }

  @Transactional
  public synchronized Preview sandboxPreview(String id, String identity, PreviewInput input) {
    bindSandboxIdentity(id, identity);
    return preview(id, input);
  }

  private Row row(LedgerEntry e) {
    return new Row(
        e.reference,
        e.date,
        e.description,
        BigDecimal.valueOf(e.amountMinor, 2).toPlainString(),
        e.status,
        e.kind);
  }

  private String disposition(Row row, LedgerEntry old) {
    if (old == null) return "NEW";
    if (row.equals(row(old))) return "DUPLICATE";
    if (old.status.equals("PENDING") && row.status().equals("BOOKED")) return "SETTLE_PENDING";
    return "CONFLICT";
  }

  private boolean overlap(Row row, List<LedgerEntry> existing, List<Row> incoming) {
    long amount = money(row.amountEuro());
    return existing.stream()
            .anyMatch(
                e ->
                    !e.status.equals("VOID")
                        && !e.reference.equals(row.reference())
                        && e.date.equals(row.date())
                        && e.amountMinor == amount)
        || incoming.stream()
            .anyMatch(
                r ->
                    !r.reference().equals(row.reference())
                        && r.date().equals(row.date())
                        && money(r.amountEuro()) == amount);
  }

  @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 60000)
  public synchronized void expirePreviews() {
    purge();
  }

  private void purge() {
    drafts.values().removeIf(d -> !d.expires.isAfter(Instant.now()));
  }

  @Transactional(readOnly = true)
  public synchronized Preview preview(String id, PreviewInput input) {
    AccessContext.requireEdit();
    check(input != null, "Import required");
    var a = account(id);
    String source = text(input.source(), 200, "Import source");
    List<Row> rows = new ArrayList<>();
    Set<String> refs = new HashSet<>();
    int number = 1;
    for (var cells : LedgerCsv.parse(input.csv())) {
      number++;
      Row r;
      try {
        r =
            normalize(
                new Row(
                    cells.get(0),
                    LocalDate.parse(cells.get(1)),
                    cells.get(2),
                    cells.get(3),
                    cells.get(4),
                    cells.get(5)),
                a);
      } catch (Exception e) {
        throw new IllegalArgumentException(
            "CSV row " + number + ": check date, amount, reference, description, status and kind");
      }
      check(refs.add(r.reference()), "CSV references must be unique within this account import");
      rows.add(r);
    }
    var existing = entries.findByAccountIdOrderByDateAscReferenceAsc(id);
    purge();
    check(drafts.size() < 32, "Too many active previews; wait ten minutes for expiry");
    var d = new Draft();
    d.account = id;
    d.source = source;
    d.revision = a.revision;
    d.expires = Instant.now().plusSeconds(600);
    d.rows = List.copyOf(rows);
    String key = UUID.randomUUID().toString();
    drafts.put(key, d);
    return new Preview(
        key,
        a.revision,
        d.expires,
        rows.stream()
            .map(
                r ->
                    new Proposal(
                        r,
                        disposition(
                            r,
                            existing.stream()
                                .filter(e -> e.reference.equals(r.reference()))
                                .findFirst()
                                .orElse(null)),
                        overlap(r, existing, rows)))
            .toList());
  }

  private LedgerEntry put(LedgerAccount a, Row row, String source, LedgerEntry old) {
    LedgerEntry e = old == null ? new LedgerEntry() : old;
    if (old == null) {
      e.id = UUID.randomUUID().toString();
      e.accountId = a.id;
    }
    e.reference = row.reference();
    e.date = row.date();
    e.description = row.description();
    e.amountMinor = money(row.amountEuro());
    e.status = row.status();
    e.kind = row.kind();
    e.source = source;
    e.reviewState = "PROVISIONAL";
    return entries.save(e);
  }

  @Transactional
  public synchronized Imported approve(String id, String key, Approval input) {
    purge();
    Draft d = drafts.get(key);
    check(
        d != null && d.account.equals(id),
        "Preview expired or belongs to another account; preview again");
    check(
        input != null && Boolean.TRUE.equals(input.acknowledgedOverlap()),
        "Review source facts and possible overlaps explicitly");
    String fingerprint = hash(input);
    if (d.result != null) {
      check(
          fingerprint.equals(d.approvalHash),
          "This preview was already approved with different facts");
      return d.result;
    }
    var a = account(id);
    revision(a, input.revision());
    check(a.revision == d.revision, "Preview is stale; preview again");
    check(
        input.rows() != null && !input.rows().isEmpty() && input.rows().size() <= 500,
        "Select 1–500 reviewed rows");
    Set<String> selected = new HashSet<>();
    int added = 0, duplicates = 0, settled = 0;
    List<Row> reviewed = new ArrayList<>();
    check(
        entries.findByAccountIdOrderByDateAscReferenceAsc(id).size() + input.rows().size()
            <= 50_000,
        "Account activity limit reached");
    for (Row value : input.rows()) {
      Row r = normalize(value, a);
      check(
          selected.add(r.reference())
              && d.rows.stream().anyMatch(original -> original.reference().equals(r.reference())),
          "Keep unique references from this preview");
      reviewed.add(r);
      var old = entries.findByAccountIdAndReference(id, r.reference()).orElse(null);
      String type = disposition(r, old);
      check(
          !type.equals("CONFLICT"),
          "A booked reference has different facts; exclude it and use explicit correction");
      if (type.equals("DUPLICATE")) {
        duplicates++;
        continue;
      }
      unprotected(a, r.date());
      if (old != null) unprotected(a, old.date);
      if (type.equals("SETTLE_PENDING")) settled++;
      else added++;
      var before = old == null ? null : Map.of("row", row(old), "review", json.valueToTree(old));
      var saved = put(a, r, d.source, old);
      audit(id, old == null ? "IMPORT" : "SETTLE_PENDING", d.source,
          before, Map.of("row", r, "review", json.valueToTree(saved)));
    }
    if (added + settled > 0) {
      a.revision++;
      accounts.saveAndFlush(a);
      view(a, today());
    }
    audit(id, "REVIEW_IMPORT", d.source, d.rows, reviewed);
    var result = new Imported(added, duplicates, settled, a.revision);
    org.springframework.transaction.support.TransactionSynchronizationManager
        .registerSynchronization(
            new org.springframework.transaction.support.TransactionSynchronization() {
              @Override
              public void afterCommit() {
                synchronized (LedgerService.this) {
                  d.approvalHash = fingerprint;
                  d.result = result;
                }
              }
            });
    return result;
  }

  @Transactional
  public AccountView manual(String id, Manual input) {
    check(
        input != null && Boolean.TRUE.equals(input.acknowledgedOverlap()),
        "Review the manual entry and check existing activity for duplicates");
    var a = account(id);
    revision(a, input.revision());
    Row r = normalize(input.row(), a);
    String source = text(input.source(), 200, "Source");
    var old = entries.findByAccountIdAndReference(id, r.reference()).orElse(null);
    if (old != null) {
      check(row(old).equals(r), "Reference already exists with different facts; use correction");
      return view(a, today());
    }
    check(
        entries.findByAccountIdOrderByDateAscReferenceAsc(id).size() < 50_000,
        "Account activity limit reached");
    unprotected(a, r.date());
    put(a, r, source, null);
    a.revision++;
    accounts.saveAndFlush(a);
    audit(id, "MANUAL", source, null, r);
    return view(a, today());
  }

  @Transactional
  public AccountView correct(String id, String entryId, Correction input) {
    check(input != null, "Correction required");
    var a = account(id);
    revision(a, input.revision());
    var e =
        entries
            .findById(entryId)
            .orElseThrow(() -> new IllegalArgumentException("Unknown activity"));
    check(e.accountId.equals(id), "Activity belongs to another account");
    check(!e.kind.equals("ADJUSTMENT"), "Correct adjustments with a new sourced reconciliation");
    check(e.linkedEntryId == null && entries.findAll().stream().noneMatch(other -> e.id.equals(other.linkedEntryId)),
        "Unlink related activity with a reason before correcting its facts");
    unprotected(a, e.date);
    Row r = normalize(input.row(), a, true);
    unprotected(a, r.date());
    check(r.reference().equals(e.reference), "Stable references cannot be changed");
    String reason = text(input.reason(), 200, "Correction reason/source");
    var before = Map.of("row", row(e), "review", json.valueToTree(e));
    put(a, r, reason, e);
    e.reviewState = "PROVISIONAL";
    audit(id, "CORRECTION", reason, before, Map.of("row", r, "review", json.valueToTree(e)));
    a.revision++;
    accounts.saveAndFlush(a);
    return view(a, today());
  }

  private AccountView view(LedgerAccount a, LocalDate asOf) {
    long balance = a.openingMinor, pending = 0, pendingDebit = 0, income = 0, spent = 0;
    int count = 0, pendingCount = 0;
    List<String> warnings = new ArrayList<>();
    for (var e : entries.findByAccountIdOrderByDateAscReferenceAsc(a.id)) {
      if (e.date.isAfter(asOf)) continue;
      count++;
      if (e.status.equals("VOID")) continue;
      if (e.status.equals("PENDING")) {
        pending = sum(pending, e.amountMinor);
        pendingCount++;
        if (e.amountMinor < 0) pendingDebit = sum(pendingDebit, -e.amountMinor);
        continue;
      }
      balance = sum(balance, e.amountMinor);
      if (e.kind.equals("ACTIVITY") || Set.of("REFUND","REVERSAL").contains(Objects.toString(e.linkType,""))) {
        if (Set.of("REFUND","REVERSAL").contains(Objects.toString(e.linkType,""))) {
          if(e.amountMinor>0) spent = sum(spent, -e.amountMinor);
          else income = sum(income, e.amountMinor);
          continue;
        }
        if (e.amountMinor > 0) income = sum(income, e.amountMinor);
        else spent = sum(spent, -e.amountMinor);
      }
    }
    boolean reconciled =
        asOf.equals(a.reconciledDate)
            && Objects.equals(a.reconciledRevision, a.revision)
            && Objects.equals(a.reconciledBalance, balance);
    if (!reconciled) warnings.add("Not reconciled for this date/revision");
    if (pendingCount != 0) warnings.add("Pending activity is excluded from the booked balance");
    if (asOf.isBefore(today().minusDays(30)))
      warnings.add("Position date is more than 30 days old");
    return new AccountView(
        a.id,
        a.name,
        a.kind,
        a.openingDate,
        a.openingMinor,
        a.source,
        a.revision,
        balance,
        pending,
        pendingDebit,
        pendingCount,
        income,
        spent,
        a.reconciledDate,
        a.reconciledSource,
        reconciled,
        count,
        List.copyOf(warnings),a.shared,AccessContext.owns(a.ownerId));
  }

  @Transactional(readOnly = true)
  public Position position(LocalDate asOf) {
    return position(asOf,false);
  }
  @Transactional(readOnly=true)
  public Position sharedPosition(LocalDate asOf){return position(asOf,true);}
  private Position position(LocalDate asOf,boolean sharedOnly){
    AccessContext.requireRead();
    check(asOf != null && !asOf.isAfter(today()), "Position date must be today or earlier");
    List<AccountView> rows = new ArrayList<>();
    List<String> warnings = new ArrayList<>();
    long cashTotal = 0, debt = 0, income = 0, spent = 0, pendingDebits = 0;
    if (asOf.isBefore(today().minusDays(30)))
      warnings.add("Historical position: more than 30 days old; not current spending evidence");
    for (var a : accounts.findAll().stream().filter(a -> AccessContext.reads(a.ownerId,a.shared)&&(!sharedOnly||a.shared)).sorted(Comparator.comparing(a -> a.name)).toList()) {
      if (a.openingDate.isAfter(asOf)) {
        warnings.add(a.name + " opened after this date and is excluded");
        continue;
      }
      var v = view(a, asOf);
      rows.add(v);
      if (a.kind.equals("CASH")) {
        cashTotal = sum(cashTotal, v.bookedMinor());
        pendingDebits = sum(pendingDebits, v.pendingDebitMinor());
      } else debt = sum(debt, v.bookedMinor());
      income = sum(income, v.incomeMinor());
      spent = sum(spent, v.spendingMinor());
      if (!v.reconciled()) warnings.add(a.name + ": balance is not reconciled");
    }
    return new Position(
        asOf,
        List.copyOf(rows),
        cashTotal,
        debt,
        sum(cashTotal, debt),
        pendingDebits,
        income,
        spent,
        List.copyOf(warnings));
  }

  @Transactional(readOnly = true)
  public List<LedgerEntry> activity(String id) {
    var a = account(id);
    var list = entries.findByAccountIdOrderByDateAscReferenceAsc(id);
    Collections.reverse(list);
    if (!AccessContext.owns(a.ownerId)) {
      // Relationship identifiers can point to an owner's private counterpart.
      return list.stream().limit(500).map(e -> {
        var safe = new LedgerEntry(); safe.id=e.id; safe.accountId=e.accountId;
        safe.reference=e.reference; safe.date=e.date; safe.description=e.description;
        safe.amountMinor=e.amountMinor; safe.status=e.status; safe.kind=e.kind; safe.source=e.source;
        safe.reviewState=e.reviewState; return safe;
      }).toList();
    }
    return list.stream().limit(500).toList();
  }

  @Transactional(readOnly = true)
  public List<LedgerAudit> history(String id) {
    AccessContext.requireRead();
    if(id.equals("HOUSEHOLD"))id=AccessContext.publicationId();else {
      var a=account(id);
      if (!AccessContext.owns(a.ownerId)) return List.of();
    }
    return audits.findByAccountIdOrderByRecordedAtDesc(id).stream()
        .filter(e -> !e.action.startsWith("PUBLISH") || (e.source.startsWith("Snapshot ") && cash.byId(e.source.substring(9)).isPresent()))
        .toList();
  }
  @Transactional(readOnly=true)
  public boolean sourcesVisible(Set<String> ids){AccessContext.requireRead();return ids.stream().allMatch(id -> accounts.findById(id).filter(a -> AccessContext.reads(a.ownerId,a.shared)).isPresent());}

  @Transactional
  public Reconciliation reconcile(String id, LocalDate asOf, Reconcile input) {
    check(
        input != null && input.commit() != null && input.acceptAdjustment() != null,
        "Explicit reconciliation/adjustment choices required");
    var a = account(id);
    revision(a, input.revision());
    check(
        asOf != null && !asOf.isBefore(a.openingDate) && !asOf.isAfter(today()),
        "Reconciliation date must lie between opening date and today");
    String source = text(input.source(), 200, "Statement source");
    check(a.reconciledDate == null || !asOf.isBefore(a.reconciledDate),
        "Reopen the existing reconciliation before moving its protected date backwards");
    long expected = view(a, asOf).bookedMinor(),
        statement = money(input.statementEuro()),
        difference = sum(statement, -expected);
    if (!input.commit()) return new Reconciliation(expected, statement, difference, false);
    var before = json.valueToTree(a);
    check(difference == 0 || a.reconciledDate == null || asOf.isAfter(a.reconciledDate),
        "Reopen reconciliation before adjusting a protected statement date");
    check(
        difference == 0 || input.acceptAdjustment(),
        "Resolve the difference or explicitly record an adjustment");
    if (difference != 0) {
      var e = new LedgerEntry();
      e.id = UUID.randomUUID().toString();
      e.accountId = id;
      e.reference = "adjustment-" + e.id;
      e.date = asOf;
      e.description = "Explicit reconciliation adjustment";
      e.amountMinor = difference;
      e.status = "BOOKED";
      e.kind = "ADJUSTMENT";
      e.reviewState = "CONFIRMED";
      e.source = source;
      entries.saveAndFlush(e);
      a.revision++;
    }
    a.reconciledDate = asOf;
    a.reconciledBalance = statement;
    a.reconciledRevision = a.revision;
    a.reconciledSource = source;
    accounts.saveAndFlush(a);
    audit(
        id,
        "RECONCILE",
        source,
        Map.of("expectedMinor", expected, "account", before),
        Map.of("throughDate", asOf, "result", new Reconciliation(expected, statement, difference, true),
            "account", json.valueToTree(a)));
    return new Reconciliation(expected, statement, difference, true);
  }

  @Transactional
  public CashPositionService.Snapshot publish(Publish input) {
    AccessContext.requireEdit();
    check(
        input != null && Boolean.TRUE.equals(input.acknowledged()),
        "Confirm reconciled cash and check manual commitments against pending debits");
    try {
      UUID.fromString(input.requestId());
    } catch (Exception e) {
      throw new IllegalArgumentException("Publication request ID required");
    }
    String fingerprint = hash(input);
    var previous = audits.findById(input.requestId()).orElse(null);
    if (previous != null) {
      check(
          previous.action.equals("PUBLISH") && AccessContext.owns(previous.actorId) && previous.beforeJson.equals(fingerprint),
          "Publication request already used with other facts");
      return cash.byId(previous.afterJson)
          .orElseThrow(() -> new IllegalArgumentException("Published cash record unavailable"));
    }
    Position p = position(input.asOf());
    check(
        input.revisions() != null && input.revisions().size() == p.accounts().size(),
        "Review all account revisions again");
    List<CashPositionService.Line> balances = new ArrayList<>(), commitments = new ArrayList<>();
    List<Map<String, Object>> provenance = new ArrayList<>();
    for (var a : p.accounts()) {
      check(
          Objects.equals(input.revisions().get(a.id()), a.revision()),
          "Position changed; reload before publishing");
      if (!a.kind().equals("CASH")) continue;
      check(a.reconciled(), "Reconcile every cash account for this date before publishing");
      balances.add(new CashPositionService.Line(a.name(), a.bookedMinor()));
      long pendingDebit = 0;
      for (var e : entries.findByAccountIdOrderByDateAscReferenceAsc(a.id))
        if (!e.date.isAfter(p.asOf()) && e.status.equals("PENDING") && e.amountMinor < 0)
          pendingDebit = sum(pendingDebit, -e.amountMinor);
      if (pendingDebit > 0)
        commitments.add(new CashPositionService.Line("Pending debits: " + a.name(), pendingDebit));
      provenance.add(
          Map.of(
              "account",
              a.id(),
              "name",
              a.name(),
              "revision",
              a.revision(),
              "statement",
              a.reconciledSource(),
              "bookedMinor",
              a.bookedMinor(),
              "pendingDebitMinor",
              pendingDebit));
    }
    check(!balances.isEmpty(), "Add and reconcile a cash account first");
    check(
        input.commitments() != null && input.commitments().size() <= 30,
        "Explicit commitment list required");
    for (var c : input.commitments()) {
      check(c != null, "Commitment required");
      long amount = money(c.amountEuro());
      check(amount >= 0, "Commitments must not be negative");
      commitments.add(
          new CashPositionService.Line(text(c.label(), 100, "Commitment label"), amount));
    }
    long reserve = money(input.reserveEuro());
    check(reserve >= 0, "Reserve must not be negative");
    var snapshot =
        cash.saveLedger(
            new CashPositionService.Input(
                p.asOf(),
                "Reviewed ledger " + p.asOf() + "; evidence " + input.requestId(),
                "REVIEWED",
                balances,
                commitments,
                reserve),p.accounts().stream().filter(a -> a.kind().equals("CASH")).map(AccountView::id).collect(java.util.stream.Collectors.toSet()));
    var event = new LedgerAudit();
    event.id = input.requestId();
    event.accountId = AccessContext.publicationId();
    event.action = "PUBLISH";
    event.source = "Snapshot " + snapshot.id();
    event.recordedAt = Instant.now();
    event.beforeJson = fingerprint;
    event.afterJson = snapshot.id();
    audits.save(event);
    audit(AccessContext.publicationId(), "PUBLISH_EVIDENCE", event.source, null, provenance);
    return snapshot;
  }

  public record Sharing(Long revision,boolean shared) {}
  @Transactional
  public AccountView share(String id,Sharing input){
    check(input!=null,"Explicit sharing choice required");var a=account(id);revision(a,input.revision());
    boolean before=a.shared;a.shared=input.shared();a.revision++;accounts.saveAndFlush(a);
    audit(a.id,"SHARING","Explicit privacy change",before,a.shared);
    if(identityAccess!=null&&before!=a.shared)identityAccess.scopeChanged();
    return view(a,today());
  }
}
