package com.householdfinance.access;

/** Product admission bounds shared by source capture and persisted derived evidence. */
public final class HouseholdLimits {
  private HouseholdLimits() {}
  public static final int MAX_USERS = 30;
  public static final int ACCOUNTS_PER_USER = 30;
  public static final int MAX_VISIBLE_ACCOUNTS = MAX_USERS * ACCOUNTS_PER_USER;
}
