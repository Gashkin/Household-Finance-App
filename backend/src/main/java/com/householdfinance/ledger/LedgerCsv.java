package com.householdfinance.ledger;

import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoField;

/** Deliberately explicit EUR interchange schema; never guesses a bank's column meaning. */
public final class LedgerCsv {
  private LedgerCsv() {}

  public record ParsedStatement(
      List<List<String>> rows, LocalDate statementDate, String statementBalanceEuro, String format) {}

  public static List<List<String>> parse(String csv) {
    return parse(csv,List.of("reference", "date", "description", "amount", "status", "kind"));
  }

  public static List<List<String>> parse(String csv, List<String> header) {
    List<List<String>> rows = parseTable(csv);
    if (rows.isEmpty() || !rows.remove(0).equals(header))
      throw new IllegalArgumentException("CSV header must be "+String.join(",",header));
    if (rows.isEmpty() || rows.size() > 500 || rows.stream().anyMatch(r -> r.size() != header.size()))
      throw new IllegalArgumentException("Provide 1–500 complete activity rows");
    return rows;
  }

  /** Accepts the canonical ledger schema or a recognised AIB transaction export. */
  public static ParsedStatement parseStatement(String csv) {
    List<List<String>> table = parseTable(csv);
    List<String> header = table.remove(0);
    if (header.equals(List.of("reference", "date", "description", "amount", "status", "kind"))) {
      if (table.isEmpty() || table.stream().anyMatch(r -> r.size() != 6))
        throw new IllegalArgumentException("Provide 1–500 complete activity rows");
      return new ParsedStatement(table, null, null, "canonical");
    }
    return parseAib(header, table);
  }

  private static List<List<String>> parseTable(String csv) {
    if (csv == null || csv.length() > 100_000)
      throw new IllegalArgumentException("CSV must contain at most 100,000 characters");
    if (csv.startsWith("\uFEFF")) csv = csv.substring(1);
    char delimiter = detectDelimiter(csv);
    List<List<String>> rows = new ArrayList<>();
    List<String> row = new ArrayList<>();
    StringBuilder cell = new StringBuilder();
    boolean quoted = false, closed = false;
    for (int i = 0; i < csv.length(); i++) {
      char c = csv.charAt(i);
      if (quoted) {
        if (c == '"') {
          if (i + 1 < csv.length() && csv.charAt(i + 1) == '"') {
            cell.append('"');
            i++;
          } else {
            quoted = false;
            closed = true;
          }
        } else cell.append(c);
      } else if (c == delimiter || c == '\n' || c == '\r') {
        row.add(cell.toString());
        cell.setLength(0);
        closed = false;
        if (c == '\n' || c == '\r') {
          if (c == '\r' && i + 1 < csv.length() && csv.charAt(i + 1) == '\n') i++;
          if (row.stream().anyMatch(s -> !s.isBlank())) rows.add(row);
          row = new ArrayList<>();
        }
      } else if (c == '"') {
        if (cell.length() != 0 || closed) throw new IllegalArgumentException("Invalid CSV quoting");
        quoted = true;
      } else {
        if (closed)
          throw new IllegalArgumentException("Unexpected characters after quoted CSV cell");
        cell.append(c);
      }
      if (rows.size() > 501)
        throw new IllegalArgumentException("Import at most 500 activity rows at once");
    }
    if (quoted) throw new IllegalArgumentException("Unclosed CSV quote");
    if (cell.length() != 0 || closed || !row.isEmpty()) {
      row.add(cell.toString());
      rows.add(row);
    }
    if (rows.isEmpty()) throw new IllegalArgumentException("CSV header and activity rows required");
    return rows;
  }

  private static ParsedStatement parseAib(List<String> header, List<List<String>> table) {
    if (table.isEmpty() || table.stream().anyMatch(r -> r.size() != header.size()))
      throw new IllegalArgumentException("Provide 1–500 complete AIB transaction rows");
    List<String> names = aibColumns(header);
    int date = find(names, "postedtransactionsdate", "posteddate", "transactiondate", "date", "posted", "bookeddate");
    int description = find(names, "description", "transactiondescription", "transactiondetails", "details", "narrative", "particulars");
    int debit = find(names, "debit", "debitamount", "debitamounteur", "paidout", "moneyout", "withdrawal", "withdrawals");
    int credit = find(names, "credit", "creditamount", "creditamounteur", "paidin", "moneyin", "deposit", "deposits");
    int amount = find(names, "amount", "transactionamount");
    int balance = find(names, "balance", "runningbalance", "accountbalance");
    int reference = find(names, "reference", "transactionreference", "transactionid", "bankreference");
    int transactionType = find(names, "transactiontype", "type");
    if (date < 0 || description < 0 || (amount < 0 && debit < 0 && credit < 0))
      throw new IllegalArgumentException(
          "Unsupported statement CSV. Select the standard AIB transaction export with date, description and amount columns.");
    if (amount >= 0 && (debit >= 0 || credit >= 0))
      throw new IllegalArgumentException("Ambiguous AIB amounts: use either Amount or Debit/Credit columns");
    DateTimeFormatter twoDigitYear = new DateTimeFormatterBuilder()
        .appendPattern("d/M/")
        .appendValueReduced(ChronoField.YEAR, 2, 2, 2000)
        .toFormatter(Locale.ROOT)
        .withResolverStyle(ResolverStyle.STRICT);
    List<List<String>> rows = new ArrayList<>();
    Map<String,Integer> generatedReferences = new HashMap<>();
    LocalDate statementDate = null;
    String statementBalance = null;
    for (int i = 0; i < table.size(); i++) {
      List<String> source = table.get(i);
      try {
        LocalDate booked = parseDate(source.get(date), twoDigitYear);
        String label = source.get(description).strip();
        if (label.isBlank()) throw new IllegalArgumentException();
        String signedAmount;
        if (amount >= 0) {
          signedAmount = cleanAmount(source.get(amount));
          if (transactionType >= 0 && !signedAmount.startsWith("-")) {
            String type = column(source.get(transactionType));
            if (Set.of("debit", "paidout", "withdrawal", "withdraw", "payment").contains(type))
              signedAmount = "-" + signedAmount;
          }
        }
        else {
          String debitText = debit < 0 ? "" : source.get(debit).strip();
          String creditText = credit < 0 ? "" : source.get(credit).strip();
          long debitMinor = debitText.isEmpty() ? 0 : money(cleanAmount(debitText));
          long creditMinor = creditText.isEmpty() ? 0 : money(cleanAmount(creditText));
          if (debitMinor < 0 || creditMinor < 0 || (debitMinor != 0 && creditMinor != 0))
            throw new IllegalArgumentException();
          signedAmount = java.math.BigDecimal.valueOf(creditMinor - debitMinor, 2).toPlainString();
        }
        if (money(signedAmount) == 0) throw new IllegalArgumentException();
        String stableReference;
        if (reference >= 0 && !source.get(reference).isBlank()) stableReference = source.get(reference).strip();
        else {
          String balanceValue = balance < 0 ? "" : source.get(balance).strip();
          String fingerprint = digest(booked + "\u0000" + label + "\u0000" + signedAmount + "\u0000" + balanceValue);
          int occurrence = generatedReferences.merge(fingerprint, 1, Integer::sum);
          stableReference = "AIB-" + fingerprint + "-" + occurrence;
        }
        rows.add(List.of(stableReference, booked.toString(), label, signedAmount, "BOOKED", "ACTIVITY"));
        if (balance >= 0 && !source.get(balance).isBlank()
            && (statementDate == null || booked.isAfter(statementDate))) {
          statementDate = booked;
          statementBalance = cleanAmount(source.get(balance));
        }
      } catch (Exception e) {
        throw new IllegalArgumentException("AIB CSV row " + (i + 2) + ": check date, description, debit/credit and balance values");
      }
    }
    return new ParsedStatement(rows, statementDate, statementBalance, "AIB");
  }

  private static String column(String value) {
    return value.strip().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
  }

  private static List<String> aibColumns(List<String> header) {
    List<String> names = new ArrayList<>(header.stream().map(LedgerCsv::column).toList());
    for (int i = 0; i < names.size(); i++) {
      if (i + 1 < names.size() && names.get(i).equals("posted")
          && names.get(i + 1).equals("transactions") && i + 2 < names.size()
          && names.get(i + 2).equals("date")) names.set(i + 2, "postedtransactionsdate");
      if (i + 1 < names.size() && Set.of("debit", "credit").contains(names.get(i))
          && names.get(i + 1).equals("amount")) {
        String direction = names.get(i);
        names.set(i, direction + "group");
        names.set(i + 1, direction + "amount");
      }
      if (i + 1 < names.size() && names.get(i).equals("transaction")
          && names.get(i + 1).equals("type")) names.set(i + 1, "transactiontype");
    }
    return names;
  }

  private static char detectDelimiter(String csv) {
    int commas = 0, semicolons = 0;
    boolean quoted = false;
    for (int i = 0; i < csv.length(); i++) {
      char c = csv.charAt(i);
      if (c == '"') {
        if (quoted && i + 1 < csv.length() && csv.charAt(i + 1) == '"') i++;
        else quoted = !quoted;
      } else if (!quoted && (c == '\n' || c == '\r')) break;
      else if (!quoted && c == ',') commas++;
      else if (!quoted && c == ';') semicolons++;
    }
    return semicolons > commas ? ';' : ',';
  }

  private static int find(List<String> names, String... aliases) {
    for (String alias : aliases) {
      for (int i = 0; i < names.size(); i++) if (names.get(i).equals(alias)) return i;
    }
    return -1;
  }

  private static LocalDate parseDate(String value, DateTimeFormatter twoDigitYear) {
    String clean = value.strip();
    try { return LocalDate.parse(clean); } catch (Exception ignored) { }
    try { return LocalDate.parse(clean, twoDigitYear); } catch (Exception ignored) { }
    return LocalDate.parse(clean, DateTimeFormatter.ofPattern("d/M/uuuu", Locale.ROOT).withResolverStyle(ResolverStyle.STRICT));
  }

  private static String cleanAmount(String value) {
    String clean = value.strip().replace("€", "").replace(" ", "").toUpperCase(Locale.ROOT).replace("EUR", "");
    boolean negative = clean.startsWith("(") && clean.endsWith(")");
    if (negative) clean = "-" + clean.substring(1, clean.length() - 1);
    if (clean.matches("-?[0-9]{1,3}(?:,[0-9]{3})+(?:\\.[0-9]{1,2})?")) clean = clean.replace(",", "");
    if (!clean.matches("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]{1,2})?"))
      throw new IllegalArgumentException("Invalid euro amount");
    return clean;
  }

  private static long money(String value) {
    long minor = new java.math.BigDecimal(value).movePointRight(2).longValueExact();
    if (minor < -1_000_000_000_000L || minor > 1_000_000_000_000L)
      throw new IllegalArgumentException("Amount exceeds supported range");
    return minor;
  }

  private static String digest(String value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) { throw new IllegalStateException(e); }
  }
}
