package com.householdfinance.ledger;

import jakarta.persistence.*;

@Entity
@Table(name = "ledger_accounts")
public class LedgerAccount {
  @Id
  @Column(length = 36)
  public String id;

  @Column(nullable = false, length = 100)
  public String name;

  @Column(nullable = false, length = 8)
  public String kind;

  @Column(nullable = false)
  public java.time.LocalDate openingDate;

  @Column(nullable = false)
  public long openingMinor;

  @Column(nullable = false, length = 200)
  public String source;

  @Column(length = 64, unique = true)
  public String sandboxIdentity;

  @Column(nullable = false)
  public long revision;

  public java.time.LocalDate reconciledDate;

  public Long reconciledRevision;

  public Long reconciledBalance;

  @Column(length = 200)
  public String reconciledSource;
}
