package com.householdfinance.contracts;

import com.householdfinance.access.AccessContext;
import com.householdfinance.access.AccessService;
import com.householdfinance.contracts.ContractStore.Alert;
import com.householdfinance.contracts.ContractStore.Saved;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.householdfinance.contracts.ContractReview.*;

@Service
class ContractAlerts {
  enum Action { MARK_READ, DISMISS, SNOOZE, REOPEN }

  record Notification(String contractId, String termId, String name, LocalDate cutoff,
      LocalDate target, Stage stage, boolean unread, boolean dismissed, LocalDate snoozedUntil,
      String timing) {}
  record Change(Action action, LocalDate until, String timing) {}
  record Inbox(LocalDate today, String zone, List<Notification> notifications) {}

  private final ContractStore store;
  private final ZoneId zone;
  private final Clock clock;

  ContractAlerts(ContractStore store, @Value("${workspace.time-zone:Europe/Dublin}") String zone,
      ObjectProvider<Clock> clock) {
    this.store = store;
    this.zone = ZoneId.of(zone);
    this.clock = clock.getIfAvailable(Clock::systemUTC);
  }

  LocalDate today() { return LocalDate.ofInstant(clock.instant(), zone); }

  @EventListener(ApplicationReadyEvent.class)
  @Scheduled(initialDelay = 60_000, fixedDelay = 60_000)
  @Transactional
  public void evaluate() {
    store.revokeAlerts();
    var today = today();
    var users = store.readers();
    for (var saved : store.all()) reconcile(saved, today, users);
  }

  @EventListener
  @Transactional
  public void capabilitiesChanged(AccessService.CapabilitiesChanged change) {
    store.revokeAlerts();
    if (store.readers().contains(change.userId())) {
      var today = today();
      for (var saved : store.visible(change.userId())) reconcile(saved, today, List.of(change.userId()));
    }
  }

  void reconcile(Saved saved) {
    store.revokeAlerts();
    reconcile(saved, today(), store.readers());
  }

  private void reconcile(Saved saved, LocalDate today, List<String> users) {
    var term = saved.record().asOf(today);
    var deadline = deadline(term, today);
    if (!term.terms().reviewEnabled() || !actionable(deadline)) return;
    for (var user : users) {
      if (saved.shared() || saved.owner().equals(user)) {
        var alert = store.alert(saved.id(), term.id(), user);
        if (alert == null || !sameTiming(alert, deadline)) {
          store.saveAlert(saved.id(), term.id(), user, deadline.timing(), Disposition.UNREAD, null);
        } else if (alert.until() != null && !alert.until().isAfter(today)) {
          store.saveAlert(saved.id(), term.id(), user, deadline.timing(), Disposition.UNREAD, null);
        }
      }
    }
  }

  @Transactional(readOnly = true)
  public Inbox inbox() {
    AccessContext.requireRead();
    var user = AccessContext.userId();
    var today = today();
    var alerts = store.alerts(user).stream().collect(Collectors.toMap(
        a -> a.contract() + "/" + a.term(), Function.identity()));
    var notifications = new ArrayList<Notification>();
    for (var saved : store.visible(user)) {
      var current = saved.record().asOf(today);
      for (var term : saved.record().terms()) {
        if (!term.terms().reviewEnabled()) continue;
        var deadline = deadline(term, today);
        if (!term.id().equals(current.id()) && deadline.stage() != Stage.COMPLETED) continue;
        var alert = Optional.ofNullable(alerts.get(saved.id() + "/" + term.id()))
            .filter(a -> sameTiming(a, deadline)).orElse(null);
        boolean active = actionable(deadline);
        boolean dismissed = active && alert != null && alert.disposition() == Disposition.DISMISSED;
        var snoozed = active && alert != null && alert.until() != null && alert.until().isAfter(today)
            ? alert.until() : null;
        boolean unread = active && (alert == null || alert.disposition() == Disposition.UNREAD
            || alert.disposition() == Disposition.SNOOZED && snoozed == null);
        notifications.add(new Notification(saved.id(), term.id(), saved.record().name(), deadline.cutoff(),
            deadline.target(), deadline.stage(), unread, dismissed, snoozed, deadline.timing()));
      }
    }
    notifications.sort(Comparator.comparingInt(ContractAlerts::priority)
        .thenComparing(Notification::cutoff, Comparator.nullsLast(Comparator.naturalOrder()))
        .thenComparing(Notification::target, Comparator.nullsLast(Comparator.naturalOrder()))
        .thenComparing(Notification::name)
        .thenComparing(Notification::contractId).thenComparing(Notification::termId));
    return new Inbox(today, zone.getId(), List.copyOf(notifications));
  }

  private static int priority(Notification notification) {
    if (notification.stage() == Stage.COMPLETED) return 6;
    if (notification.dismissed()) return 5;
    if (notification.snoozedUntil() != null) return 4;
    return switch (notification.stage()) {
      case OVERDUE -> 0;
      case DUE -> 1;
      case NEEDS_DATE -> 2;
      default -> 3;
    };
  }

  @Transactional
  public void change(String contractId, String termId, Change change) {
    AccessContext.requireRead();
    var user = AccessContext.userId();
    var saved = ContractsService.visible(store, contractId);
    var today = today();
    var term = saved.record().asOf(today);
    var deadline = deadline(term, today);
    require(term.id().equals(termId) && term.terms().reviewEnabled() && actionable(deadline)
        && change != null && change.action() != null && Objects.equals(change.timing(), deadline.timing()),
        "Review changed. Reload your inbox.");
    var until = change.until();
    date(until);
    require(change.action() == Action.SNOOZE ? until != null && until.isAfter(today) : until == null,
        "Choose a future snooze date");
    var disposition = switch (change.action()) {
      case MARK_READ -> Disposition.READ;
      case DISMISS -> Disposition.DISMISSED;
      case SNOOZE -> Disposition.SNOOZED;
      case REOPEN -> Disposition.UNREAD;
    };
    store.saveAlert(saved.id(), term.id(), user, deadline.timing(), disposition, until);
  }

  private static boolean actionable(Deadline deadline) {
    return deadline.stage() == Stage.OVERDUE || deadline.stage() == Stage.DUE;
  }

  private static boolean sameTiming(Alert alert, Deadline deadline) {
    return alert.timing().equals(deadline.timing());
  }
}
