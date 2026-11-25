package com.householdfinance.ledger;

import jakarta.persistence.*;

@Entity
@Table(
    name = "ledger_entries",
    uniqueConstraints = @UniqueConstraint(columnNames = {"account_id", "reference"}))
public class LedgerEntry {
  @Column(length=80) public String reviewedMerchant;
  @Column(length=36) public String categoryId;
  @Column(nullable=false, length=12) public String reviewState = "PROVISIONAL";
  @Column(length=36) public String linkedEntryId;
  @Column(length=16) public String linkType;
  @Id
  @Column(length = 36)
  public String id;

  @Column(nullable = false, length = 36, name = "account_id")
  public String accountId;

  @Column(nullable = false, length = 100)
  public String reference;

  @Column(nullable = false, name = "activity_date")
  public java.time.LocalDate date;

  @Column(nullable = false, length = 160)
  public String description;

  @Column(nullable = false)
  public long amountMinor;

  @Column(nullable = false, length = 8)
  public String status;

  @Column(nullable = false, length = 12)
  public String kind;

  @Column(nullable = false, length = 200)
  public String source;
}
