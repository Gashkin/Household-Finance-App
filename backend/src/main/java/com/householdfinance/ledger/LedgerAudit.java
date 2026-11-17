package com.householdfinance.ledger;

import jakarta.persistence.*;

@Entity
@Table(name = "ledger_audit")
public class LedgerAudit {
  @Column(length=36) public String actorId = com.householdfinance.access.AccessContext.actor()==null?"owner":com.householdfinance.access.AccessContext.userId();
  @Id
  @Column(length = 36)
  public String id;

  @Column(nullable = false, length = 36)
  public String accountId;

  @Column(nullable = false)
  public java.time.Instant recordedAt;

  @Column(nullable = false, length = 24)
  public String action;

  @Column(nullable = false, length = 200)
  public String source;

  @Lob public String beforeJson;
  @Lob public String afterJson;
}
