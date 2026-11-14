package com.householdfinance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Embeddable
public class CashPositionLine {
    public enum Kind { CASH, COMMITMENT }

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Kind kind;

    @Column(nullable = false, length = 100)
    private String label;

    @Column(nullable = false)
    private long amountMinor;

    protected CashPositionLine() {}

    public CashPositionLine(Kind kind, String label, long amountMinor) {
        this.kind = kind;
        this.label = label;
        this.amountMinor = amountMinor;
    }

    public Kind getKind() { return kind; }
    public String getLabel() { return label; }
    public long getAmountMinor() { return amountMinor; }
}
