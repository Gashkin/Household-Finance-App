package com.householdfinance.bank;

import com.fasterxml.jackson.databind.*;
import com.sun.net.httpserver.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

/** Test source only: generated keys, mock bank and no external traffic. */
public final class SyntheticProvider implements AutoCloseable {
  final ObjectMapper json = new ObjectMapper();
  final HttpServer server;
  final KeyPair key;
  final String app = "11111111-1111-4111-8111-111111111111";
  final String session = "22222222-2222-4222-8222-222222222222";
  final String account = "33333333-3333-4333-8333-333333333333";
  final String callback;
  volatile String environment = "SANDBOX", mode = "ok", state = "", authorization = "";
  volatile JsonNode auth;
  volatile int deletes, transactionPages;

  public SyntheticProvider(String callback) throws Exception {
    this.callback = callback;
    var generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    key = generator.generateKeyPair();
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", this::handle);
    server.start();
  }

  URI base() {
    return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
  }

  SandboxGateway gateway() {
    return new SandboxGateway(true, app, callback, base(), key::getPrivate, json);
  }

  String until() {
    return Instant.now().plusSeconds(3500).toString();
  }

  void reset() {
    environment = "SANDBOX";
    mode = "ok";
    deletes = 0;
    transactionPages = 0;
  }

  Map<String, Object> access() {
    return Map.of("valid_until", mode.equals("bad-expiry") ? "not-an-instant" : mode.equals("expired") ? Instant.EPOCH.toString() : until());
  }

  void handle(HttpExchange ex) throws java.io.IOException {
    try {
      String path = ex.getRequestURI().getPath();
      authorization = Objects.toString(ex.getRequestHeaders().getFirst("Authorization"), "");
      Object result;
      if (path.equals("/consent")) {
        var url = callback + "?code=synthetic-code&state=" + state;
        byte[] html =
            ("<html><body><h1>Synthetic bank consent</h1><a href=\""
                    + url
                    + "\">Authorise synthetic read-only accounts</a></body></html>")
                .getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/html");
        ex.sendResponseHeaders(200, html.length);
        ex.getResponseBody().write(html);
        return;
      }
      if (path.equals("/sessions") && mode.equals("session-exchange-fail")) { ex.sendResponseHeaders(503,-1); return; }
      if (path.equals("/sessions") && mode.equals("session-bad-json")) {
        byte[] invalid="not-json".getBytes(StandardCharsets.UTF_8); ex.sendResponseHeaders(200,invalid.length); ex.getResponseBody().write(invalid); return;
      }
      if (path.equals("/application"))
        result =
            Map.of(
                "environment",
                environment,
                "active",
                !mode.equals("inactive"),
                "services",
                mode.equals("pis-only") ? List.of("PIS") : mode.equals("unknown-scope") ? List.of("AIS", "UNKNOWN") : mode.equals("empty-scope") ? List.of() : environment.equals("PRODUCTION") && !mode.equals("pis") ? List.of("AIS") : List.of("AIS", "PIS"),
                "redirect_urls",
                List.of(callback));
      else if (path.equals("/aspsps"))
        result =
            Map.of(
                "aspsps",
                List.of(
                    Map.of(
                        "name", environment.equals("PRODUCTION") ? "AIB" : "Mock ASPSP", "country", environment.equals("PRODUCTION") ? "IE" : "FI", "psu_types", List.of("personal"), "maximum_consent_validity", 15552000)));
      else if (path.equals("/auth")) {
        auth = json.readTree(ex.getRequestBody());
        state = auth.path("state").asText();
        result =
            Map.of(
                "url",
                mode.equals("redirect") ? "https://evil.example/consent" : base() + "/consent");
      } else if (path.equals("/sessions"))
        result =
            Map.of(
                "session_id",
                mode.equals("malformed-session") ? "not-a-session-identifier" : session,
                "access",
                access(),
                "accounts",
                List.of(
                    Map.of(
                        "uid",
                        account,
                        "identification_hash",
                        "synthetic-stable-identity",
                        "currency",
                        mode.equals("currency") ? "USD" : "EUR",
                        "cash_account_type",
                        "CACC")));
      else if (path.equals("/sessions/" + session)) {
        if (ex.getRequestMethod().equals("DELETE")) {
          deletes++;
          if (mode.equals("delete-fail")) {
            ex.sendResponseHeaders(503, -1);
            return;
          }
          result = Map.of();
        } else
          result =
              Map.of(
                  "status",
                  mode.equals("revoked") ? "REVOKED" : "AUTHORIZED",
                  "accounts",
                  List.of(account),
                  "access",
                  access());
      } else if (path.endsWith("/balances"))
        result =
            Map.of(
                "balances",
                List.of(
                    Map.of(
                        "balance_type",
                        "CLBD",
                        "balance_amount",
                        Map.of("currency", "EUR", "amount", "990.00"),
                        "reference_date",
                        LocalDate.now(ZoneId.of("Europe/Dublin")).toString()),
                    Map.of(
                        "balance_type",
                        "CLAV",
                        "balance_amount",
                        Map.of("currency", "EUR", "amount", "99999.00"))));
      else if (path.endsWith("/transactions")) {
        transactionPages++;
        boolean second = ex.getRequestURI().getQuery().contains("continuation_key");
        if (mode.equals("partial") && second) {
          ex.sendResponseHeaders(503, -1);
          return;
        }
        if (mode.equals("oversize")) {
          byte[] huge = ("{\"padding\":\"" + "x".repeat(270000) + "\"}").getBytes();
          ex.sendResponseHeaders(200, 0);
          ex.getResponseBody().write(huge);
          return;
        }
        var row = new LinkedHashMap<String, Object>();
        row.put("entry_reference", second ? "pending" : "booked");
        row.put("booking_date", LocalDate.now(ZoneId.of("Europe/Dublin")).toString());
        row.put("transaction_date", LocalDate.now(ZoneId.of("Europe/Dublin")).toString());
        row.put("status", second ? "PDNG" : "BOOK");
        row.put(
            "transaction_amount", Map.of("currency", "EUR", "amount", second ? "5.00" : "10.00"));
        row.put("credit_debit_indicator", "DBIT");
        row.put("remittance_information", List.of("Synthetic groceries"));
        if (mode.equals("missing-id")) row.remove("entry_reference");
        result =
            second && !mode.equals("repeat")
                ? Map.of("transactions", List.of(row))
                : Map.of("transactions", List.of(row), "continuation_key", "next");
      } else {
        ex.sendResponseHeaders(404, -1);
        return;
      }
      byte[] bytes = json.writeValueAsBytes(result);
      ex.getResponseHeaders().set("Content-Type", "application/json");
      ex.sendResponseHeaders(200, bytes.length);
      ex.getResponseBody().write(bytes);
    } catch (Exception e) {
      try {
        ex.sendResponseHeaders(500, -1);
      } catch (Exception ignored) {
      }
    } finally {
      ex.close();
    }
  }

  public void close() {
    server.stop(0);
  }
}
