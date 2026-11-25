package com.householdfinance.contracts;

import com.householdfinance.access.AccessContext;
import com.householdfinance.access.AccessService;
import com.householdfinance.contracts.ContractReview.Record;
import com.householdfinance.contracts.ContractStore.Saved;
import com.householdfinance.ledger.CommitmentService;
import com.householdfinance.ledger.LedgerAccountRepository;
import com.householdfinance.ledger.LedgerEntryRepository;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static com.householdfinance.contracts.ContractReview.*;

@Service
class ContractsService {
  record Create(String requestId, String name, Kind kind, Assessment assessment, Terms terms) {}
  record Edit(Long revision, String name, Assessment assessment, Terms terms) {}
  record Sharing(Long revision, Boolean shared) {}
  record DecisionInput(Long revision, Decision decision, LocalDate date, String evidence) {}
  record AttemptInput(Long revision, Decision action, LocalDate date, String evidence) {}
  record ConfirmInput(Long revision, Outcome outcome, LocalDate date, LocalDate effective,
      String evidence, Terms next) {}
  record Reactivate(Long revision, Terms next) {}
  record Summary(String id, String name, Kind kind, Status status, boolean owned, boolean shared,
      String provider, String currentTermId) {}
  record TermView(Term term, Deadline deadline, boolean editable) {}
  record Detail(Summary summary, long revision, String latestTermId, Assessment assessment, List<TermView> terms) {}
  record Option(String id, String label) {}
  record LinkOptions(List<Option> bills, List<Option> accounts) {}

  private final ContractStore store;
  private final ContractAlerts alerts;
  private final CommitmentService commitments;
  private final LedgerAccountRepository accounts;
  private final LedgerEntryRepository entries;
  private final AccessService access;

  ContractsService(ContractStore store, ContractAlerts alerts, CommitmentService commitments,
      LedgerAccountRepository accounts, LedgerEntryRepository entries, AccessService access) {
    this.store = store;
    this.alerts = alerts;
    this.commitments = commitments;
    this.accounts = accounts;
    this.entries = entries;
    this.access = access;
  }

  @Transactional(readOnly = true)
  public List<Summary> list() {
    AccessContext.requireRead();
    var today = alerts.today();
    return visibleContracts().stream().map(saved -> summary(saved, today)).toList();
  }

  @Transactional(readOnly = true)
  public Detail detail(String id) {
    return detail(visible(store, id), alerts.today());
  }

  @Transactional(readOnly = true)
  public List<Detail> exportVisible() {
    AccessContext.requireRead();
    var today = alerts.today();
    return visibleContracts().stream().map(saved -> detail(saved, today)).toList();
  }

  @Transactional(readOnly = true)
  public LinkOptions linkOptions() {
    AccessContext.requireEdit();
    var owned = accounts.findAll().stream().filter(a -> AccessContext.owns(a.ownerId))
        .map(a -> new Option(a.id, a.name)).sorted(Comparator.comparing(Option::label)).toList();
    return new LinkOptions(bills(), owned);
  }

  @Transactional(readOnly = true)
  public List<Option> transactions(String accountId) {
    AccessContext.requireEdit();
    ownAccount(accountId);
    return entries.findByAccountIdOrderByDateAscReferenceAsc(accountId).reversed().stream().limit(100)
        .map(e -> new Option(e.id, e.date + " · " + e.description)).toList();
  }

  @Transactional
  public Detail create(Create input) {
    AccessContext.requireEdit();
    require(input != null && input.kind() != null, "Choose a contract type");
    require(input.requestId() != null && input.requestId().matches("[0-9a-f]{32}"), "A valid request identifier is required");
    String owner = AccessContext.userId();
    String id = UUID.nameUUIDFromBytes(("contract:" + owner + ":" + input.requestId()).getBytes(StandardCharsets.UTF_8)).toString();
    var existing = store.visible(id, owner);
    if (existing != null && existing.owner().equals(owner)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "This contract was already created. Reload the register.");
    }
    require(store.count(owner) < 100, "Contract register is full");
    var record = new Record(required(input.name(), 120, "Contract name"), input.kind(),
        assessment(input.kind(), input.assessment()), List.of(Term.open(validTerms(input.terms()))));
    store.insert(id, owner, record);
    var saved = visible(store, id);
    alerts.reconcile(saved);
    return detail(saved, alerts.today());
  }

  @Transactional
  public Detail edit(String id, String termId, Edit input) {
    var saved = owned(id, input.revision());
    var old = saved.record();
    var terms = old.terms();
    var newTerms = input.terms();
    if (newTerms != null) {
      var term = openTerm(saved, termId);
      var validated = validTerms(newTerms);
      if (terms.size() > 1) {
        require(validated.start().equals(term.terms().start()), "The start date was set by the previous confirmed outcome");
      }
      terms = replaceLatest(terms, term.edit(validated, alerts.today()));
    } else {
      require(old.latest().id().equals(termId), "Contract term changed. Reload before saving.");
    }
    return save(saved, new Record(required(input.name(), 120, "Contract name"), old.kind(),
        assessment(old.kind(), input.assessment()), terms));
  }

  @Transactional
  public Detail sharing(String id, Sharing input) {
    var saved = owned(id, input.revision());
    require(input.shared() != null, "Choose private or shared");
    if (saved.shared() != input.shared()) {
      store.replace(saved, input.shared(), saved.record());
      if (!input.shared()) access.scopeChanged();
      saved = visible(store, id);
      alerts.reconcile(saved);
    }
    return detail(saved, alerts.today());
  }

  @Transactional
  public Detail decide(String id, String termId, DecisionInput input) {
    var saved = owned(id, input.revision());
    var term = reviewing(saved, termId);
    require(input.decision() != null, "Choose a review decision");
    var decision = new DecisionEntry(input.decision(), observed(input.date()), text(input.evidence(), 1000));
    return saveTerm(saved, new Term(term.id(), term.terms(), term.revisions(), append(term.decisions(), decision),
        term.attempts(), null), null);
  }

  @Transactional
  public Detail attempt(String id, String termId, AttemptInput input) {
    var saved = owned(id, input.revision());
    var term = reviewing(saved, termId);
    require(input.action() != null && input.action() == term.decision()
        && List.of(Decision.RENEW, Decision.SWITCH, Decision.CANCEL).contains(input.action()),
        "Record the matching renewal, switch or cancellation decision first");
    var date = observed(input.date());
    require(!date.isBefore(term.decisions().getLast().date()), "Attempt date precedes the decision");
    var attempt = new Attempt(input.action(), date, required(input.evidence(), 1000, "Action record"));
    return saveTerm(saved, new Term(term.id(), term.terms(), term.revisions(), term.decisions(),
        append(term.attempts(), attempt), null), null);
  }

  @Transactional
  public Detail confirm(String id, String termId, ConfirmInput input) {
    var saved = owned(id, input.revision());
    var term = openTerm(saved, termId);
    var observed = observed(input.date());
    require(input.outcome() != null && input.effective() != null
        && !input.effective().isBefore(term.terms().start()), "Choose a valid outcome and effective date");
    date(input.effective());
    var confirmation = new Confirmation(input.outcome(), observed, input.effective(),
        required(input.evidence(), 1000, "Provider evidence"));
    Term next = null;
    if (input.outcome() == Outcome.CANCELLED) {
      require(input.next() == null, "A cancellation cannot include a replacement term");
    } else {
      var terms = validTerms(input.next());
      require(terms.start().equals(input.effective()), "The next term must start on the confirmed effective date");
      if (input.outcome() == Outcome.RENEWED) {
        require(terms.provider().equalsIgnoreCase(term.terms().provider()), "Use Switch to record a different provider");
      }
      next = Term.open(terms);
    }
    return saveTerm(saved, new Term(term.id(), term.terms(), term.revisions(), term.decisions(), term.attempts(), confirmation), next);
  }

  @Transactional
  public Detail reactivate(String id, Reactivate input) {
    var saved = owned(id, input.revision());
    var old = saved.record();
    require(old.status(alerts.today()) == Status.CLOSED && old.latest().confirmation() != null
        && old.latest().confirmation().outcome() == Outcome.CANCELLED, "Only a closed contract can be reactivated");
    var terms = validTerms(input.next());
    require(!terms.start().isBefore(old.latest().confirmation().effective()), "Reactivation cannot precede closure");
    return save(saved, new Record(old.name(), old.kind(), old.assessment(), append(old.terms(), Term.open(terms))));
  }

  static Saved visible(ContractStore store, String id) {
    AccessContext.requireRead();
    var saved = store.visible(uuid(id), AccessContext.userId());
    if (saved == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Contract unavailable");
    return saved;
  }

  private List<Saved> visibleContracts() {
    return store.visible(AccessContext.userId()).stream()
        .sorted(Comparator.comparing((Saved saved) -> saved.record().name(), String.CASE_INSENSITIVE_ORDER)
            .thenComparing(Saved::id)).toList();
  }

  private Saved owned(String id, Long revision) {
    AccessContext.requireEdit();
    var saved = visible(store, id);
    if (!AccessContext.owns(saved.owner())) AccessService.forbidden();
    if (revision == null || saved.revision() != revision) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Contract changed. Reload before saving.");
    }
    return saved;
  }

  private Summary summary(Saved saved, LocalDate today) {
    var current = saved.record().asOf(today);
    return new Summary(saved.id(), saved.record().name(), saved.record().kind(), saved.record().status(today),
        AccessContext.owns(saved.owner()), saved.shared(), current.terms().provider(), current.id());
  }

  private Detail detail(Saved saved, LocalDate today) {
    var summary = summary(saved, today);
    var last = saved.record().latest().id();
    var history = saved.record().terms().stream().map(term -> {
      var permitted = summary.owned() ? term : term.hideReferences();
      return new TermView(permitted, term.terms().reviewEnabled() ? deadline(term, today) : null,
          summary.owned() && AccessContext.actor().edits() && term.id().equals(last) && term.confirmation() == null);
    }).toList();
    return new Detail(summary, saved.revision(), last, saved.record().assessment(), history);
  }

  private static Term openTerm(Saved saved, String termId) {
    var term = saved.record().latest();
    require(term.id().equals(termId) && term.confirmation() == null, "Only the latest unconfirmed term can be changed");
    return term;
  }

  private static Term reviewing(Saved saved, String termId) {
    var term = openTerm(saved, termId);
    require(term.terms().reviewEnabled(), "Enable review before recording a decision");
    return term;
  }

  private LocalDate observed(LocalDate date) {
    ContractReview.date(date);
    require(date != null && !date.isAfter(alerts.today()), "Choose a date on or before today");
    return date;
  }

  private Detail saveTerm(Saved saved, Term term, Term next) {
    var old = saved.record();
    var terms = replaceLatest(old.terms(), term);
    if (next != null) terms = append(terms, next);
    return save(saved, new Record(old.name(), old.kind(), old.assessment(), terms));
  }

  private static List<Term> replaceLatest(List<Term> terms, Term replacement) {
    var copy = new ArrayList<>(terms);
    copy.set(copy.size() - 1, replacement);
    return List.copyOf(copy);
  }

  private Detail save(Saved saved, Record record) {
    resetChangedAlert(saved, record);
    store.replace(saved, saved.shared(), record);
    var updated = visible(store, saved.id());
    alerts.reconcile(updated);
    return detail(updated, alerts.today());
  }

  private void resetChangedAlert(Saved saved, Record next) {
    var today = alerts.today();
    var before = saved.record().latest();
    var after = next.terms().stream().filter(term -> term.id().equals(before.id())).findFirst().orElseThrow();
    var prior = deadline(before, today);
    var updated = deadline(after, today);
    if (!before.terms().reviewEnabled().equals(after.terms().reviewEnabled())
        || !prior.timing().equals(updated.timing())
        || prior.stage() == Stage.COMPLETED && updated.stage() != Stage.COMPLETED) {
      store.clearAlerts(saved.id(), before.id());
    }
  }

  private Terms validTerms(Terms input) {
    var terms = validate(input);
    var refs = terms.references();
    if (refs == null) return terms;
    if (refs.billId() != null) {
      require(bills().stream().anyMatch(b -> b.id().equals(refs.billId())), "Choose an available recurring bill");
    }
    require((refs.accountId() == null) == (refs.transactionId() == null), "Choose both the account and transaction");
    if (refs.accountId() != null) {
      ownAccount(refs.accountId());
      require(entries.findById(uuid(refs.transactionId())).filter(e -> e.accountId.equals(refs.accountId())).isPresent(),
          "Choose an available transaction on that account");
    }
    return refs.billId() == null && refs.transactionId() == null ? terms.withReferences(null) : terms;
  }

  private void ownAccount(String accountId) {
    require(accounts.findById(uuid(accountId)).filter(a -> AccessContext.owns(a.ownerId)).isPresent(),
        "Choose an available owned account");
  }

  private List<Option> bills() {
    try {
      return commitments.get().state().schedules().stream().filter(schedule -> {
        var rule = schedule.versions().getLast().rule();
        return rule.direction().equals("EXPENSE") && !rule.frequency().equals("ONCE");
      }).map(s -> new Option(s.id(), s.versions().getLast().rule().name()))
          .sorted(Comparator.comparing(Option::label)).toList();
    } catch (IllegalArgumentException e) {
      // The commitment workspace can become unavailable after source access is removed.
      return List.of();
    }
  }
}
