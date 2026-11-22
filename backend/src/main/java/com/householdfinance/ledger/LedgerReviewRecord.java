package com.householdfinance.ledger;
import jakarta.persistence.*;
@Entity @Table(name="ledger_review_records")
public class LedgerReviewRecord {
  @Id @Column(length=36) public String id;
  @Column(nullable=false,length=36) public String ownerId;
  @Column(nullable=false,length=16) public String kind;
  @Column(nullable=false) public long revision;
  @Lob @Column(nullable=false) public String payload;
}
