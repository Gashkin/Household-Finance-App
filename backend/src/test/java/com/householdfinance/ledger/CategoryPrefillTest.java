package com.householdfinance.ledger;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CategoryPrefillTest {
  private final List<ActivityReviewService.Category> categories = List.of(category("food", false),
      category("fuel", false), category("old", true));

  private static ActivityReviewService.Category category(String id, boolean retired) {
    return new ActivityReviewService.Category(id, 0, id, null, retired, "0");
  }

  private static LedgerEntry activity(String description, String category, String state) {
    var row = new LedgerEntry();
    row.description = description;
    row.amountMinor = -100;
    row.categoryId = category;
    row.reviewState = state;
    row.status = "BOOKED";
    row.kind = "ACTIVITY";
    return row;
  }

  @Test void fuzzyApprovedMerchantWinsOverHistoryWhilePartialNamesAndInactiveRulesDoNotMatch() {
    var rule = new ActivityReviewService.Rule("r", 0, "POS 174 Sable Market", "food", true);
    var inactive = new ActivityReviewService.Rule("i", 0, "Sable Books", "fuel", false);
    var prefill = new CategoryPrefill(List.of(rule, inactive), categories,
        List.of(activity("Sable Market", "fuel", "CONFIRMED")));
    var target = activity("TAP SABLE-MARKET 351", null, "PROVISIONAL");
    assertEquals("food", prefill.suggest(target).categoryId());
    assertNull(target.categoryId);
    assertNull(prefill.suggest(activity("Sable Books", null, "PROVISIONAL")).categoryId());
  }

  @Test void conflictingConfirmedHistoryAbstainsAndOnlyEligibleExamplesCount() {
    var target = activity("TAP North Books", null, "PROVISIONAL");
    var good = activity("North Books", "food", "CONFIRMED");
    var pending = activity("North Books", "fuel", "CONFIRMED");
    pending.status = "PENDING";
    var linked = activity("North Books", "fuel", "CONFIRMED");
    linked.linkType = "REFUND";
    var credit = activity("North Books", "fuel", "CONFIRMED");
    credit.amountMinor = 100;
    var history = List.of(good, pending, linked, credit, activity("North Books","old","CONFIRMED"),
        activity("North Books","fuel","PROVISIONAL"), target);
    assertEquals("food", new CategoryPrefill(List.of(), categories, history).suggest(target).categoryId());
    var conflict = new CategoryPrefill(List.of(), categories, List.of(good, activity("North Books","fuel","CONFIRMED")));
    assertNull(conflict.suggest(target).categoryId());
    assertEquals("LOW", conflict.suggest(target).confidence());
  }
}
