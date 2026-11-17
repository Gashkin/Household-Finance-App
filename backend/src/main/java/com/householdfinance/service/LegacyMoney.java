package com.householdfinance.service;

import java.math.BigDecimal;

/** Strict admission for legacy double storage; never round or invent missing amounts. */
public final class LegacyMoney {
    private static final BigDecimal MAX = new BigDecimal("90071992547409.91");
    private LegacyMoney() {}

    public static double require(BigDecimal value, boolean signed) {
        if (value == null || value.abs().compareTo(MAX) > 0 || (!signed && value.signum() < 0)
                || value.stripTrailingZeros().scale() > 2)
            throw new IllegalArgumentException("An explicit, bounded amount in whole cents is required.");
        double stored = value.doubleValue();
        if (!Double.isFinite(stored) || BigDecimal.valueOf(stored).compareTo(value) != 0)
            throw new IllegalArgumentException("Amount cannot be stored without losing cent precision.");
        return stored;
    }

    public static double parse(String raw, boolean signed) {
        if (raw == null || raw.length() > 64)
            throw new IllegalArgumentException("An explicit monetary amount is required.");
        String text=raw.trim().replaceFirst("^(?:€|\\$|EUR\\s+)", "");
        if (!text.matches("[+-]?(?:[0-9]+|[0-9]{1,3}(?:,[0-9]{3})+)(?:\\.[0-9]{1,2})?"))
            throw new IllegalArgumentException("Amount must be numeric whole cents; missing or malformed amounts are not zero.");
        return require(new BigDecimal(text.replace(",", "")), signed);
    }
}
