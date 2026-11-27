package com.householdfinance.ledger;

import java.util.*;
import java.util.regex.Pattern;
import com.householdfinance.ledger.ActivityReviewService.Category;
import com.householdfinance.ledger.ActivityReviewService.Rule;
import com.householdfinance.ledger.ActivityReviewService.Suggestion;

/** Read-only indexes for one account's review queue. A conflicting key never picks a winner. */
final class CategoryPrefill {
  private final Map<String, Set<String>> exactRules = new HashMap<>();
  private final Map<String, Set<String>> merchantRules = new HashMap<>();
  private record HistoryKey(String merchant, int direction) {}
  private final Map<HistoryKey, Set<String>> confirmed = new HashMap<>();
  private static final Pattern SEPARATORS = Pattern.compile("[^\\p{L}\\p{N}]+");
  private static final Pattern PREFIX = Pattern.compile("^(?:(?:pos|tap|contactless|debit card|card payment) +(?:[0-9]+ +)?)+");
  private static final Pattern TERMINAL = Pattern.compile(" +[0-9]{3,}$");
  private static final Set<String> GENERIC = Set.of("pos","tap","card","payment","transfer","direct debit","cash","refund","purchase","debit","card payment","contactless");

  CategoryPrefill(List<Rule> rules, List<Category> categories, List<LedgerEntry> history) {
    Set<String> active = new HashSet<>();
    for (var category : categories) if (!category.retired()) active.add(category.id());
    for (var rule : rules) {
      if (!rule.approved() || !active.contains(rule.categoryId())) continue;
      add(exactRules, rule.description().toLowerCase(Locale.ROOT), rule.categoryId());
      String merchant = merchantKey(rule.description());
      if (!merchant.isEmpty()) add(merchantRules, merchant, rule.categoryId());
    }
    for (var entry : history) {
      if (!"CONFIRMED".equals(entry.reviewState) || !"BOOKED".equals(entry.status)
          || !"ACTIVITY".equals(entry.kind) || entry.linkType != null || !active.contains(entry.categoryId)) continue;
      String merchant = merchantKey(entry.description);
      if (!merchant.isEmpty()) add(confirmed, new HistoryKey(merchant, Long.signum(entry.amountMinor)), entry.categoryId);
    }
  }

  private static <K> void add(Map<K, Set<String>> index, K key, String category) {
    index.computeIfAbsent(key, ignored -> new HashSet<>()).add(category);
  }

  private static String merchantKey(String description) {
    String words = SEPARATORS.matcher(description.toLowerCase(Locale.ROOT)).replaceAll(" ").strip();
    words = PREFIX.matcher(words).replaceFirst("");
    words = TERMINAL.matcher(words).replaceFirst("");
    return words.length() < 3 || GENERIC.contains(words) || words.codePoints().noneMatch(Character::isLetter) ? "" : words;
  }

  Suggestion suggest(LedgerEntry entry) {
    if ("CONFIRMED".equals(entry.reviewState) || !"ACTIVITY".equals(entry.kind) || entry.linkType != null)
      return new Suggestion(null, "LOW", "Category prefill does not apply to this activity");
    var exact = exactRules.get(entry.description.toLowerCase(Locale.ROOT));
    if (exact != null) return resolve(exact, "HIGH", "Exact description matches an approved rule");
    var merchant = merchantKey(entry.description);
    var aliases = merchantRules.get(merchant);
    if (aliases != null) return resolve(aliases, "MEDIUM", "Merchant wording matches an approved rule after removing payment formatting");
    var history = confirmed.get(new HistoryKey(merchant, Long.signum(entry.amountMinor)));
    if (history != null) return resolve(history, "MEDIUM", "Matches a confirmed category for this merchant and payment direction in this account");
    return new Suggestion(null, "NONE", "No local match. Choose a category or request a model suggestion");
  }

  private static Suggestion resolve(Set<String> categories, String confidence, String reason) {
    if (categories.size() == 1) return new Suggestion(categories.iterator().next(), confidence, reason);
    return new Suggestion(null, "LOW", "Matching examples disagree. Choose a category manually");
  }
}
