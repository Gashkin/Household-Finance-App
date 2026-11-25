package com.householdfinance.access;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import com.householdfinance.access.AccessContext.Actor;

/** Stable workspace IDs and HTTP admission rules. Domain ownership checks still apply. */
public final class WorkspaceModules {
  public record Page(String id, String label, String path) {}
  public record NavigationModule(String id, String label, List<Page> pages) {}

  private enum Grant {
    SIGNED_IN, READ, EDIT, ADMIN, CUSTODIAN, CUSTODIAN_EDITOR;

    boolean allows(Actor actor) {
      return switch (this) {
        case SIGNED_IN -> true;
        case READ -> actor.reads();
        case EDIT -> actor.edits();
        case ADMIN -> actor.roles().contains("ADMIN");
        case CUSTODIAN -> actor.custodian();
        case CUSTODIAN_EDITOR -> actor.custodian() && actor.edits();
      };
    }
  }

  private record Item(String id, String label, Grant grant) {
    Item(String id, String label) {
      this(id, label, Grant.SIGNED_IN);
    }
  }

  private enum Module {
    OVERVIEW("overview", "Overview", Grant.READ, new Item("money", "Money position")),
    ACTIVITY("activity", "Activity", Grant.READ,
        new Item("member", "Spending & purchases"),
        new Item("review", "Review & reconcile", Grant.EDIT)),
    PLANNING("planning", "Planning", Grant.READ,
        new Item("forecast", "Plan ahead", Grant.EDIT),
        new Item("budget", "Budgets & allocations", Grant.EDIT),
        new Item("goals", "Goals & irregular costs", Grant.EDIT),
        new Item("plans", "Financial plans", Grant.EDIT),
        new Item("contracts", "Contracts & subscriptions")),
    DEBT("debt", "Debt", Grant.READ,
        new Item("debts", "Cards, loans & mortgages", Grant.EDIT),
        new Item("decision", "Debt decision", Grant.EDIT)),
    SETTINGS("settings", "Settings", Grant.SIGNED_IN,
        new Item("access", "Household access"),
        new Item("recovery", "Backup & recovery", Grant.CUSTODIAN),
        new Item("mcp", "ChatGPT connection", Grant.CUSTODIAN),
        new Item("planner", "Planner", Grant.CUSTODIAN_EDITOR),
        new Item("migrater", "Migrater", Grant.CUSTODIAN_EDITOR),
        new Item("consumer", "Consumer", Grant.CUSTODIAN_EDITOR));

    final String id;
    final String label;
    final Grant grant;
    final List<Item> items;

    Module(String id, String label, Grant grant, Item... items) {
      this.id = id;
      this.label = label;
      this.grant = grant;
      this.items = List.of(items);
    }

    NavigationModule navigation(Actor actor) {
      var pages = items.stream().filter(item -> item.grant().allows(actor))
          .map(item -> new Page(item.id(), item.label(), "/workspaces/" + id + "/" + item.id()))
          .toList();
      return new NavigationModule(id, label, pages);
    }
  }

  private record ApiAccess(Module module, Grant read, Grant write) {
    ApiAccess(Module module, Grant both) {
      this(module, both, both);
    }

    boolean allows(String method, Actor actor) {
      return module.grant.allows(actor) && (readMethod(method) ? read : write).allows(actor);
    }
  }

  private static final ApiAccess POSITION = new ApiAccess(Module.OVERVIEW, Grant.READ, Grant.EDIT);
  private static final ApiAccess BANKING = new ApiAccess(Module.OVERVIEW, Grant.CUSTODIAN_EDITOR);
  private static final ApiAccess MEMBER = new ApiAccess(Module.ACTIVITY, Grant.READ);
  private static final ApiAccess REVIEW = new ApiAccess(Module.ACTIVITY, Grant.READ, Grant.EDIT);
  private static final ApiAccess PLANNING = new ApiAccess(Module.PLANNING, Grant.READ, Grant.EDIT);
  private static final ApiAccess CONTRACT_ALERTS = new ApiAccess(Module.PLANNING, Grant.READ);
  private static final ApiAccess CONTRACT_LINKS = new ApiAccess(Module.PLANNING, Grant.EDIT);
  private static final ApiAccess DEBT = new ApiAccess(Module.DEBT, Grant.READ, Grant.EDIT);
  private static final ApiAccess INTAKE = new ApiAccess(Module.DEBT, Grant.CUSTODIAN_EDITOR);
  private static final ApiAccess PERSONAL = new ApiAccess(Module.SETTINGS, Grant.SIGNED_IN);
  private static final ApiAccess ADMIN = new ApiAccess(Module.SETTINGS, Grant.ADMIN);
  private static final ApiAccess CUSTODIAN = new ApiAccess(Module.SETTINGS, Grant.CUSTODIAN);
  private static final ApiAccess LEGACY = new ApiAccess(Module.SETTINGS, Grant.CUSTODIAN_EDITOR);

  private static final Set<String> PERSONAL_PATHS = Set.of(
      "/api/access/me", "/api/access/reauth", "/api/access/recovery-code",
      "/api/workspace/lock", "/api/workspace/status", "/auth/me");
  private static final Set<String> POSITION_PATHS = Set.of(
      "/api/ledger", "/api/ledger/shared", "/api/ledger/history", "/api/ledger/publish");
  private static final Set<String> READ_ONLY_CALCULATIONS = Set.of(
      "/api/debt-scenarios/compare", "/api/debt-scenarios/report", "/api/forecast/compare");

  private WorkspaceModules() {}

  public static List<NavigationModule> navigation(Actor actor) {
    return Arrays.stream(Module.values()).filter(module -> module.grant.allows(actor))
        .map(module -> module.navigation(actor)).filter(module -> !module.pages().isEmpty())
        .toList();
  }

  public static boolean permits(String path, String method, Actor actor) {
    var access = apiAccess(path);
    // These existing POST calculations read permitted evidence without saving changes.
    String operation = method.equals("POST") && READ_ONLY_CALCULATIONS.contains(path) ? "GET" : method;
    return actor != null && access != null && access.allows(operation, actor);
  }

  private static ApiAccess apiAccess(String path) {
    if (PERSONAL_PATHS.contains(path)) return PERSONAL;
    if (within(path, "/api/access")) return ADMIN;
    if (within(path, "/api/workspace") || within(path, "/api/mcp")) return CUSTODIAN;
    if (within(path, "/planner") || within(path, "/imports") ||
        within(path, "/migration") || within(path, "/reports")) return LEGACY;
    if (within(path, "/api/banking") || within(path, "/api/bank-sandbox")) return BANKING;
    if (within(path, "/api/member")) return MEMBER;
    if (within(path, "/api/ledger/review") ||
        (within(path, "/api/ledger/accounts") && path.endsWith("/reopen"))) return REVIEW;
    if (within(path, "/api/contracts/alerts")) return CONTRACT_ALERTS;
    if (within(path, "/api/contracts/links")) return CONTRACT_LINKS;
    if (within(path, "/api/contracts")) return PLANNING;
    if (within(path, "/api/forecast") || within(path, "/api/plans") ||
        within(path, "/api/ledger/budgets") || within(path, "/api/ledger/goals")) return PLANNING;
    if (within(path, "/api/debt-scenarios") || within(path, "/api/cash-position") ||
        within(path, "/api/ledger/debts")) return DEBT;
    if (within(path, "/api/evidence-intake")) return INTAKE;
    if (POSITION_PATHS.contains(path) || within(path, "/api/ledger/accounts")) return POSITION;
    return null;
  }

  private static boolean readMethod(String method) {
    return method.equals("GET") || method.equals("HEAD");
  }

  private static boolean within(String path, String prefix) {
    return path.equals(prefix) || path.startsWith(prefix + "/");
  }
}
