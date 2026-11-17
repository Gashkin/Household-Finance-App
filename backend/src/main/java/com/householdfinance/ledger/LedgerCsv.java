package com.householdfinance.ledger;

import java.util.*;

/** Deliberately explicit EUR interchange schema; never guesses a bank's column meaning. */
public final class LedgerCsv {
  private LedgerCsv() {}

  public static List<List<String>> parse(String csv) {
    return parse(csv,List.of("reference", "date", "description", "amount", "status", "kind"));
  }

  public static List<List<String>> parse(String csv, List<String> header) {
    if (csv == null || csv.length() > 100_000)
      throw new IllegalArgumentException("CSV must contain at most 100,000 characters");
    if (csv.startsWith("\uFEFF")) csv = csv.substring(1);
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
      } else if (c == ',' || c == '\n' || c == '\r') {
        row.add(cell.toString());
        cell.setLength(0);
        closed = false;
        if (c != ',') {
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
      if (rows.size() > 500)
        throw new IllegalArgumentException("Import at most 500 activity rows at once");
    }
    if (quoted) throw new IllegalArgumentException("Unclosed CSV quote");
    if (cell.length() != 0 || closed || !row.isEmpty()) {
      row.add(cell.toString());
      rows.add(row);
    }
    if (rows.isEmpty()
        || !rows.remove(0)
            .equals(header))
      throw new IllegalArgumentException(
          "CSV header must be "+String.join(",",header));
    if (rows.isEmpty() || rows.size() > 500 || rows.stream().anyMatch(r -> r.size() != header.size()))
      throw new IllegalArgumentException("Provide 1–500 complete activity rows");
    return rows;
  }
}
