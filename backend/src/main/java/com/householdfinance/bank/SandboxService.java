package com.householdfinance.bank;

import com.fasterxml.jackson.databind.*;
import com.householdfinance.ledger.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class SandboxService {
  public record Bank(String name, String country, long maximumConsentSeconds) {}

  public record Account(String handle, String label, String currency, String kind) {}

  public record Status(
      boolean configured,
      String environment,
      String state,
      Instant expiresAt,
      Instant lastSuccessfulFetch,
      String lastError,
      List<Account> accounts,
      String callback) {}

  public record Start(String name, String country, Boolean acknowledged) {}

  public record Complete(String state, String code) {}

  public record Fetch(
      String accountHandle,
      String ledgerAccountId,
      LocalDate from,
      LocalDate to,
      Boolean acknowledged) {}

  public record Fetched(
      LedgerService.Preview preview,
      List<Balance> balances,
      List<String> warnings,
      Instant fetchedAt) {}

  public record Balance(String type, String amountEuro, String date) {}

  private record ProviderAccount(String uid, String identity, Account publicAccount) {}

  private record Pending(String state, String binding, Instant expires, Instant consentUntil) {}

  private final SandboxGateway gateway;
  private final LedgerService ledger;
  private Pending pending;
  private String sessionId;
  private Instant expires, lastSuccess;
  private boolean blocked, reauthorize;
  private String lastError = "";
  private final Map<String, ProviderAccount> accounts = new LinkedHashMap<>();
  private Instant attemptWindow = Instant.EPOCH;
  private int attempts, fetches;

  public SandboxService(SandboxGateway gateway, LedgerService ledger) {
    this.gateway = gateway;
    this.ledger = ledger;
  }

  private void check(boolean ok, String message) {
    if (!ok) throw new IllegalArgumentException(message);
  }

  private static String digest(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private String uuid(String value) {
    try {
      return UUID.fromString(value).toString();
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid sandbox response identifier");
    }
  }

  private String text(JsonNode parent, String name, int max) {
    var value = parent.get(name);
    check(
        value != null
            && value.isTextual()
            && !value.asText().isBlank()
            && value.asText().length() <= max,
        "Sandbox response is missing a required field");
    return value.asText();
  }

  public synchronized Status status() {
    String state =
        sessionId == null
            ? "DISCONNECTED"
            : blocked
                ? "REVOCATION_PENDING"
                : reauthorize
                    ? "RECONNECT_REQUIRED"
                    : !Instant.now().isBefore(expires) ? "EXPIRED" : "CONNECTED";
    return new Status(
        gateway.configured(),
        "SANDBOX",
        state,
        expires,
        lastSuccess,
        lastError,
        accounts.values().stream().map(ProviderAccount::publicAccount).toList(),
        gateway.callback());
  }

  public synchronized List<Bank> banks() {
    gateway.verifySandbox();
    List<Bank> banks = new ArrayList<>();
    JsonNode values = gateway.request("GET", "/aspsps", null).path("aspsps");
    check(values.isArray() && values.size() <= 500, "Invalid sandbox bank list");
    for (var bank : values)
      if (bank.path("name").asText().equals("Mock ASPSP")) {
        String country = text(bank, "country", 2);
        long max = bank.path("maximum_consent_validity").asLong(0);
        if (country.matches("[A-Z]{2}") && max > 0) banks.add(new Bank("Mock ASPSP", country, max));
      }
    check(!banks.isEmpty(), "Mock ASPSP is unavailable for this sandbox application");
    return banks;
  }

  public synchronized Map<String, String> start(Start input, String binding) {
    check(
        input != null && Boolean.TRUE.equals(input.acknowledged()),
        "Confirm that only synthetic sandbox accounts will be connected for read-only"
            + " balances/transactions");
    check(sessionId == null, "Disconnect the existing sandbox session before connecting again");
    Instant now = Instant.now();
    if (!now.isBefore(attemptWindow.plusSeconds(60))) {
      attemptWindow = now;
      attempts = 0;
    }
    check(attempts++ < 5, "Too many sandbox connection attempts; wait a minute");
    Bank selected =
        banks().stream()
            .filter(b -> b.name().equals(input.name()) && b.country().equals(input.country()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Select a listed Mock ASPSP"));
    String state = Base64.getUrlEncoder().withoutPadding().encodeToString(random());
    Instant until = now.plusSeconds(Math.min(3600, selected.maximumConsentSeconds()));
    var response =
        gateway.request(
            "POST",
            "/auth",
            Map.of(
                "aspsp",
                Map.of("name", selected.name(), "country", selected.country()),
                "access",
                Map.of(
                    "balances",
                    true,
                    "transactions",
                    true,
                    "transactions_extended_history",
                    false,
                    "valid_until",
                    until.toString()),
                "state",
                state,
                "redirect_url",
                gateway.callback(),
                "psu_type",
                "personal"));
    String redirect = gateway.safeRedirect(text(response, "url", 4096));
    pending = new Pending(state, binding, now.plusSeconds(600), until);
    lastError = "";
    return Map.of("url", redirect);
  }

  private byte[] random() {
    byte[] bytes = new byte[32];
    new SecureRandom().nextBytes(bytes);
    return bytes;
  }

  public synchronized Status complete(Complete input, String binding) {
    check(
        input != null
            && pending != null
            && Instant.now().isBefore(pending.expires())
            && Objects.equals(binding, pending.binding())
            && input.state() != null
            && MessageDigest.isEqual(
                pending.state().getBytes(StandardCharsets.UTF_8),
                input.state().getBytes(StandardCharsets.UTF_8)),
        "Sandbox callback state is invalid, expired or from another workspace session; connect"
            + " again");
    check(
        input.code() != null && !input.code().isBlank() && input.code().length() <= 4096,
        "Sandbox authorization code missing");
    Pending flow = pending;
    pending = null;
    var response = gateway.request("POST", "/sessions", Map.of("code", input.code()));
    String id = uuid(text(response, "session_id", 36));
    try {
      Instant expiry = Instant.parse(text(response.path("access"), "valid_until", 80));
      check(expiry.isAfter(Instant.now()), "Sandbox consent already expired");
      var source = response.path("accounts");
      check(
          source.isArray() && !source.isEmpty() && source.size() <= 30,
          "Expected 1–30 sandbox accounts");
      Map<String, ProviderAccount> selected = new LinkedHashMap<>();
      int number = 0;
      for (var a : source) {
        check(text(a, "currency", 3).equals("EUR"), "Only EUR sandbox accounts are supported");
        String type = text(a, "cash_account_type", 8);
        check(
            Set.of("CACC", "CASH", "SVGS", "CARD", "LOAN").contains(type),
            "Unsupported sandbox account type");
        String uid = uuid(text(a, "uid", 36));
        String identity = digest(text(a, "identification_hash", 4096));
        String handle = UUID.randomUUID().toString();
        String kind = Set.of("CARD", "LOAN").contains(type) ? "DEBT" : "CASH";
        var publicAccount =
            new Account(handle, "Sandbox account " + (++number) + " (" + type + ")", "EUR", kind);
        selected.put(handle, new ProviderAccount(uid, identity, publicAccount));
      }
      sessionId = id;
      expires = expiry.isBefore(flow.consentUntil()) ? expiry : flow.consentUntil();
      accounts.clear();
      accounts.putAll(selected);
      blocked = false;
      reauthorize = false;
      fetches = 0;
      lastSuccess = null;
      lastError = "";
      return status();
    } catch (Exception e) {
      try {
        gateway.request("DELETE", "/sessions/" + id, null);
      } catch (Exception ignored) {
      }
      throw new IllegalArgumentException(
          "Sandbox session response was invalid; reconnect using synthetic EUR accounts");
    }
  }

  private void active(String uid) {
    check(
        sessionId != null && !blocked && !reauthorize && Instant.now().isBefore(expires),
        "Sandbox session expired or disconnected; reconnect before fetching");
    gateway.verifySandbox();
    var session = gateway.request("GET", "/sessions/" + sessionId, null);
    if (!session.path("status").asText().equals("AUTHORIZED")) reauthorize = true;
    check(!reauthorize, "Sandbox consent is no longer authorized; disconnect/reconnect");
    boolean selected = false;
    for (var item : session.path("accounts")) if (item.asText().equals(uid)) selected = true;
    if (!selected) reauthorize = true;
    check(selected, "Selected account is no longer authorized");
    try {
      check(
          Instant.parse(text(session.path("access"), "valid_until", 80)).isAfter(Instant.now()),
          "Sandbox consent expired");
    } catch (DateTimeException e) {
      throw new IllegalArgumentException("Invalid sandbox session expiry");
    }
  }

  public synchronized Fetched fetch(Fetch input) {
    check(
        input != null && Boolean.TRUE.equals(input.acknowledged()),
        "Confirm this explicit synthetic data refresh");
    var a = accounts.get(input.accountHandle());
    check(a != null, "Select an authorized sandbox account");
    var target =
        ledger.position(LocalDate.now(ZoneId.of("Europe/Dublin"))).accounts().stream()
            .filter(v -> v.id().equals(input.ledgerAccountId()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Select a local ledger account first"));
    check(
        target.kind().equals(a.publicAccount().kind()), "Sandbox and ledger account types differ");
    check(
        input.from() != null
            && input.to() != null
            && !input.from().isBefore(target.openingDate())
            && !input.from().isAfter(input.to())
            && !input.to().isAfter(LocalDate.now(ZoneId.of("Europe/Dublin")))
            && java.time.temporal.ChronoUnit.DAYS.between(input.from(), input.to()) <= 90,
        "Choose at most 90 days from the ledger opening date through today");
    check(fetches++ < 20, "Sandbox session refresh limit reached; reconnect later");
    Instant deadline = Instant.now().plusSeconds(40);
    try {
      active(a.uid());
      List<Balance> balances = new ArrayList<>();
      var balanceData =
          gateway.request("GET", "/accounts/" + a.uid() + "/balances", null).path("balances");
      check(balanceData.isArray() && balanceData.size() <= 30, "Invalid sandbox balances");
      for (var b : balanceData)
        if (Set.of("CLBD", "ITBD", "OPBD").contains(b.path("balance_type").asText())) {
          String amount = amount(b.path("balance_amount"), true);
          balances.add(
              new Balance(
                  b.path("balance_type").asText(),
                  amount,
                  b.path("reference_date").asText("Undated — do not assume current")));
        }
      StringBuilder csv = new StringBuilder("reference,date,description,amount,status,kind\n");
      String continuation = null;
      Set<String> pages = new HashSet<>(), references = new HashSet<>();
      int count = 0;
      do {
        check(
            Instant.now().isBefore(deadline) && pages.size() < 50,
            "Sandbox pagination limit/time exceeded; narrow the date range");
        String path =
            "/accounts/"
                + a.uid()
                + "/transactions?date_from="
                + input.from()
                + "&date_to="
                + input.to()
                + "&strategy=default"
                + (continuation == null
                    ? ""
                    : "&continuation_key="
                        + URLEncoder.encode(continuation, StandardCharsets.UTF_8));
        var page = gateway.request("GET", path, null);
        var rows = page.path("transactions");
        check(rows.isArray() && rows.size() <= 500, "Invalid sandbox transaction page");
        for (var r : rows) {
          String state = text(r, "status", 8);
          check(
              Set.of("BOOK", "PDNG").contains(state),
              "Unsupported transaction status; no partial import");
          String date =
              state.equals("BOOK")
                  ? text(r, "booking_date", 10)
                  : r.path("transaction_date").asText(r.path("value_date").asText(""));
          LocalDate day = LocalDate.parse(date);
          check(
              !day.isBefore(input.from()) && !day.isAfter(input.to()),
              "Provider returned activity outside the selected date range");
          String reference =
              "eb-" + digest(a.identity() + "\u0000" + text(r, "entry_reference", 512));
          check(
              references.add(reference),
              "Repeated transaction reference across pages; no partial import");
          String value = amount(r.path("transaction_amount"), false);
          String direction = text(r, "credit_debit_indicator", 4);
          check(Set.of("CRDT", "DBIT").contains(direction), "Invalid transaction direction");
          String description = "Sandbox activity";
          var remittance = r.path("remittance_information");
          if (remittance.isArray() && !remittance.isEmpty())
            description = remittance.get(0).asText("Sandbox activity");
          description = description.replace('\n', ' ').replace('\r', ' ');
          if (description.isBlank()) description = "Sandbox activity";
          if (description.length() > 160) description = description.substring(0, 160);
          csv.append(reference)
              .append(',')
              .append(day)
              .append(',')
              .append('"')
              .append(description.replace("\"", "\"\""))
              .append('"')
              .append(',')
              .append(direction.equals("DBIT") ? "-" : "")
              .append(value)
              .append(',')
              .append(state.equals("BOOK") ? "BOOKED" : "PENDING")
              .append(",ACTIVITY\n");
          check(++count <= 500, "More than 500 sandbox rows; narrow the date range");
        }
        var key = page.get("continuation_key");
        continuation = key == null || key.isNull() ? null : key.asText();
        if (continuation != null) {
          check(
              !continuation.isBlank() && continuation.length() <= 4096 && pages.add(continuation),
              "Invalid/repeated sandbox continuation; no partial import");
        }
      } while (continuation != null);
      check(count > 0, "No sandbox activity in that date range");
      var preview =
          ledger.sandboxPreview(
              target.id(),
              a.identity(),
              new LedgerService.PreviewInput(
                  csv.toString(),
                  "Enable Banking SANDBOX; fetched "
                      + Instant.now()
                      + "; "
                      + input.from()
                      + " to "
                      + input.to()));
      lastSuccess = Instant.now();
      lastError = "";
      return new Fetched(
          preview,
          List.copyOf(balances),
          List.of(
              "Synthetic SANDBOX evidence only; review every row and transfer classification before"
                  + " approval.",
              "Balances are informational evidence with their provider dates/types. They do not"
                  + " automatically reconcile or change cash.",
              "Only stable entry references are imported. Date history can be incomplete; reconcile"
                  + " against the booked statement."),
          lastSuccess);
    } catch (IllegalArgumentException e) {
      lastError = "Sandbox refresh failed; no partial activity was imported";
      throw e;
    } catch (Exception e) {
      lastError = "Sandbox refresh failed; no partial activity was imported";
      throw new IllegalArgumentException(lastError);
    }
  }

  private String amount(JsonNode value, boolean signed) {
    check(value.path("currency").asText().equals("EUR"), "Only EUR sandbox amounts are supported");
    String amount = text(value, "amount", 40);
    check(
        amount.matches((signed ? "-?" : "") + "(?:0|[1-9][0-9]*)(?:\\.[0-9]{1,2})?"),
        "Unsupported sandbox amount precision/sign");
    return amount;
  }

  public synchronized Status disconnect() {
    pending = null;
    blocked = true;
    if (sessionId != null) {
      gateway.request("DELETE", "/sessions/" + sessionId, null);
    }
    return forget();
  }

  public synchronized Status forget() {
    pending = null;
    sessionId = null;
    accounts.clear();
    expires = null;
    lastSuccess = null;
    blocked = false;
    reauthorize = false;
    lastError =
        "Local connection cleared. Provider revocation is separate if disconnect did not succeed.";
    return status();
  }
}
