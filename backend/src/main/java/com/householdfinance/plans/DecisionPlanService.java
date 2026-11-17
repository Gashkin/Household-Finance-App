package com.householdfinance.plans;

import com.fasterxml.jackson.databind.*;
import com.householdfinance.controller.DebtScenarioController;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** A recorded household choice is not authority to execute a financial change. */
@Service
public class DecisionPlanService {
  public record Evidence(
      String id, String source, LocalDate observedOn, String summary, String uncertainty) {}

  public record Option(String id, String label, String impact, String uncertainty) {}

  public record Content(String title, String goal, List<Evidence> evidence, List<Option> options) {}

  public record Action(
      String id, String label, LocalDate dueOn, String outcome, Instant completedAt) {}

  public record ActionInput(String id, String label, LocalDate dueOn) {}

  public record Decision(
      String optionId,
      String optionLabel,
      String rationale,
      LocalDate reviewOn,
      Instant decidedAt) {}

  public record History(
      String kind,
      Instant at,
      Content content,
      DebtScenarioController.Report report,
      Decision decision,
      List<Action> actions,
      String note) {}

  public record Stored(
      Content content,
      DebtScenarioController.Report report,
      Decision decision,
      List<Action> actions,
      List<History> history) {}

  public record View(String id, long revision, Instant updatedAt, String status, Stored data) {}

  public record Edit(Long revision, Content content) {}

  public record Choose(
      Long revision,
      String optionId,
      String rationale,
      LocalDate reviewOn,
      List<ActionInput> actions,
      Boolean confirmed) {}

  public record Complete(Long revision, String actionId, String outcome) {}

  private final DecisionPlanRepository plans;
  private final ObjectMapper json;
  private final DebtScenarioController debt;
  private final com.householdfinance.workspace.WorkspaceBackupService backups;
  @org.springframework.beans.factory.annotation.Autowired(required=false) private com.householdfinance.service.CashPositionService sources;

  public DecisionPlanService(
      DecisionPlanRepository plans,
      ObjectMapper json,
      DebtScenarioController debt,
      com.householdfinance.workspace.WorkspaceBackupService backups) {
    this.plans = plans;
    this.json = json;
    this.debt = debt;
    this.backups = backups;
  }

  public static LocalDate today() {
    return LocalDate.now(ZoneId.of("Europe/Dublin"));
  }

  static void require(boolean value, String message) {
    if (!value) throw new IllegalArgumentException(message);
  }

  static String text(String s, int max, String field) {
    require(
        s != null && !s.isBlank() && s.length() <= max,
        field + " is required (maximum " + max + " characters)");
    return s.strip();
  }

  static void id(String s) {
    require(s != null && s.matches("[A-Za-z0-9_-]{1,64}"), "Invalid item identifier");
  }

  static void unique(Set<String> ids, String value) {
    id(value);
    require(ids.add(value), "Duplicate item identifier");
  }

  static Content validate(Content c) {
    require(
        c != null && c.evidence() != null && c.options() != null,
        "Complete goal, evidence and options required");
    text(c.title(), 120, "Plan title");
    text(c.goal(), 2000, "Goal");
    require(
        c.evidence().size() <= 20 && c.options().size() <= 10,
        "Too many evidence items or options");
    Set<String> ids = new HashSet<>();
    for (var e : c.evidence()) {
      require(e != null, "Complete evidence required");
      unique(ids, e.id());
      text(e.source(), 500, "Evidence source");
      require(
          e.observedOn() != null && !e.observedOn().isAfter(today()),
          "Evidence date must be today or earlier");
      text(e.summary(), 2000, "Evidence summary");
      text(e.uncertainty(), 1000, "Evidence uncertainty or assumptions");
    }
    ids.clear();
    for (var o : c.options()) {
      require(o != null, "Complete option required");
      unique(ids, o.id());
      text(o.label(), 120, "Option label");
      text(o.impact(), 2000, "Option impact");
      text(o.uncertainty(), 1000, "Option uncertainty");
    }
    return c;
  }

  private Stored read(DecisionPlan p) {
    try {
      return json.readValue(p.payload, Stored.class);
    } catch (Exception e) {
      throw new IllegalStateException("Saved plan could not be read");
    }
  }
  private boolean visible(DecisionPlan p){
    if(!com.householdfinance.access.AccessContext.owns(p.ownerId))return false;
    if(sources==null)return true;
    var data=read(p);var reports=new ArrayList<DebtScenarioController.Report>();
    if(data.report()!=null)reports.add(data.report());
    data.history().stream().filter(h -> h.report()!=null).forEach(h -> reports.add(h.report()));
    return reports.stream().allMatch(r -> r.cashEvidence()==null||sources.byId(r.cashEvidence().id()).isPresent());
  }

  private View view(DecisionPlan p) {
    var s = read(p);
    String status =
        s.decision() == null
            ? "PROPOSAL"
            : !s.decision().reviewOn().isAfter(today()) ? "REVIEW_DUE" : "DECIDED";
    return new View(p.id, p.version + 1, p.updatedAt, status, s);
  }

  @Transactional(readOnly = true)
  public List<View> list() {
    com.householdfinance.access.AccessContext.requireRead();
    return plans.findAll().stream()
        .filter(this::visible)
        .sorted(Comparator.comparing((DecisionPlan p) -> p.updatedAt).reversed())
        .map(this::view)
        .toList();
  }

  @Transactional(readOnly = true)
  public View get(String id) {
    return view(find(id));
  }

  private DecisionPlan find(String id) {
    com.householdfinance.access.AccessContext.requireRead();
    return plans.findById(id).filter(this::visible).orElseThrow(() -> new IllegalArgumentException("Plan not found"));
  }

  private DecisionPlan owned(String id, Long revision) {
    var p = find(id);
    require(revision != null && revision == p.version + 1, "Plan changed; reload before saving");
    return p;
  }

  private View persist(
      DecisionPlan p,
      Stored old,
      Content content,
      DebtScenarioController.Report report,
      Decision decision,
      List<Action> actions,
      String kind,
      String note) {
    com.householdfinance.access.AccessContext.requireEdit();
    backups.guardPlanMutation();
    if (old == null) require(plans.findAll().stream().filter(x -> com.householdfinance.access.AccessContext.owns(x.ownerId)).count() < 100, "Local plan limit reached");
    var history = new ArrayList<History>(old == null ? List.of() : old.history());
    require(
        history.size() < 200, "Plan history limit reached; retain this plan and create a new one");
    p.updatedAt = Instant.now();
    history.add(
        new History(kind, p.updatedAt, content, report, decision, List.copyOf(actions), note));
    try {
      p.payload =
          json.writeValueAsString(
              new Stored(content, report, decision, List.copyOf(actions), List.copyOf(history)));
    } catch (Exception e) {
      throw new IllegalStateException("Cannot save plan");
    }
    var saved = plans.saveAndFlush(p);
    try {
      backups.assertPlanMutationRecoverable();
    } catch (IllegalArgumentException e) {
      if (e.getMessage() != null && e.getMessage().contains("backup limit"))
        throw new IllegalArgumentException(
            "This plan change would exceed the workspace backup limit. Saved records and history"
                + " were kept; export a backup. Downloading does not free capacity.");
      throw e;
    }
    return view(saved);
  }

  @Transactional
  public View create(Content input) {
    var p = new DecisionPlan();
    p.id = UUID.randomUUID().toString();
    return persist(
        p,
        null,
        validate(input),
        null,
        null,
        List.of(),
        "CREATED",
        "Proposals only; no financial change executed");
  }

  @Transactional
  public View fromDebt(JsonNode input) {
    var report = debt.report(input);
    var r = report.result();
    var content =
        new Content(
            "Debt repayment decision",
            "Decide whether to keep cash or make an extra debt repayment",
            List.of(
                new Evidence(
                    "debt-report",
                    "Server-generated debt comparison " + report.calculationVersion(),
                    today(),
                    "Frozen EUR comparison; see attached inputs, cash provenance and results",
                    String.join("; ", r.assumptions())
                        + " Manual/dated cash remains an estimate; refresh evidence before"
                        + " acting.")),
            List.of(
                new Option(
                    "keep",
                    "Keep repayment schedule",
                    "Future interest EUR "
                        + euro(r.baseline().interestMinor())
                        + "; "
                        + r.baseline().months()
                        + " months",
                    "Assumes unchanged rate and regular payments"),
                new Option(
                    "extra",
                    "Make extra repayment",
                    "Interest avoided EUR "
                        + euro(r.interestSavedMinor())
                        + "; cash remaining EUR "
                        + euro(r.remainingCashMinor())
                        + "; reserve shortfall EUR "
                        + euro(r.reserveShortfallMinor()),
                    String.join("; ", r.warnings())
                        + " Illustrative calculation; confirm lender terms")));
    var p = new DecisionPlan();
    p.id = UUID.randomUUID().toString();
    return persist(
        p,
        null,
        validate(content),
        report,
        null,
        List.of(),
        "CREATED_FROM_DEBT",
        "Frozen calculation, not a recommendation or payment");
  }

  private static String euro(long minor) {
    return java.math.BigDecimal.valueOf(minor, 2).toPlainString();
  }

  @Transactional
  public View edit(String id, Edit input) {
    var p = owned(id, input.revision());
    var old = read(p);
    return persist(
        p,
        old,
        validate(input.content()),
        old.report(),
        null,
        List.of(),
        "EVIDENCE_REVISED",
        "Previous decision and actions retained in history; explicit new decision required");
  }

  @Transactional
  public View choose(String id, Choose input) {
    var p = owned(id, input.revision());
    var s = read(p);
    require(s.decision() == null, "Revise the plan before replacing a recorded decision");
    require(Boolean.TRUE.equals(input.confirmed()), "Explicit decision confirmation required");
    require(
        !s.content().evidence().isEmpty() && s.content().options().size() >= 2,
        "Record evidence and at least two options before deciding");
    var option =
        s.content().options().stream()
            .filter(o -> o.id().equals(input.optionId()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Select an existing option"));
    text(input.rationale(), 2000, "Decision rationale");
    require(
        input.reviewOn() != null && !input.reviewOn().isBefore(today()),
        "Review date must be today or later");
    require(
        input.actions() != null && !input.actions().isEmpty() && input.actions().size() <= 20,
        "Add 1–20 follow-up actions");
    Set<String> ids = new HashSet<>();
    var actions = new ArrayList<Action>();
    for (var a : input.actions()) {
      require(a != null, "Complete action required");
      unique(ids, a.id());
      text(a.label(), 500, "Action label");
      require(
          a.dueOn() != null && !a.dueOn().isBefore(today()),
          "Action due date must be today or later");
      actions.add(new Action(a.id(), a.label(), a.dueOn(), null, null));
    }
    return persist(
        p,
        s,
        s.content(),
        s.report(),
        new Decision(
            option.id(), option.label(), input.rationale(), input.reviewOn(), Instant.now()),
        actions,
        "DECISION_RECORDED",
        "Explicit operator decision; no external action executed");
  }

  @Transactional
  public View complete(String id, Complete input) {
    var p = owned(id, input.revision());
    var s = read(p);
    require(s.decision() != null, "Current decision required");
    text(input.outcome(), 2000, "Observed outcome");
    var actions = new ArrayList<>(s.actions());
    boolean found = false;
    for (int i = 0; i < actions.size(); i++) {
      var a = actions.get(i);
      if (a.id().equals(input.actionId())) {
        require(a.completedAt() == null, "Action already completed");
        actions.set(i, new Action(a.id(), a.label(), a.dueOn(), input.outcome(), Instant.now()));
        found = true;
      }
    }
    require(found, "Action not found in current decision");
    return persist(
        p,
        s,
        s.content(),
        s.report(),
        s.decision(),
        actions,
        "ACTION_COMPLETED",
        "Operator-recorded outcome");
  }
}
