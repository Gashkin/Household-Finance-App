package com.householdfinance.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "cash_position_snapshots")
public class CashPositionSnapshotEntity {
    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false)
    private LocalDate asOfDate;

    @Column(nullable = false)
    private Instant recordedAt;

    @Column(nullable = false, length = 200)
    private String sourceNote;

    @Column(nullable = false, length = 16)
    private String verification;

    @Column(nullable = false)
    private long minimumReserveMinor;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "cash_position_lines", joinColumns = @JoinColumn(name = "snapshot_id"))
    @OrderColumn(name = "line_order")
    private List<CashPositionLine> lines = new ArrayList<>();

    protected CashPositionSnapshotEntity() {}

    public CashPositionSnapshotEntity(String id, LocalDate asOfDate, Instant recordedAt,
            String sourceNote, String verification, long minimumReserveMinor,
            List<CashPositionLine> lines) {
        this.id = id;
        this.asOfDate = asOfDate;
        this.recordedAt = recordedAt;
        this.sourceNote = sourceNote;
        this.verification = verification;
        this.minimumReserveMinor = minimumReserveMinor;
        this.lines = new ArrayList<>(lines);
    }

    public String getId() { return id; }
    public LocalDate getAsOfDate() { return asOfDate; }
    public Instant getRecordedAt() { return recordedAt; }
    public String getSourceNote() { return sourceNote; }
    public String getVerification() { return verification; }
    public long getMinimumReserveMinor() { return minimumReserveMinor; }
    public List<CashPositionLine> getLines() { return lines; }
}
