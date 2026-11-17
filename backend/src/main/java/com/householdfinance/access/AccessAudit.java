package com.householdfinance.access;

import jakarta.persistence.*;

@Entity @Table(name="access_audit")
public class AccessAudit {
  @Id @Column(length=36) public String id;
  @Column(nullable=false,length=36) public String actorId;
  @Column(nullable=false,length=36) public String targetId;
  @Column(nullable=false,length=32) public String action;
  @Column(nullable=false,length=32) public String outcome;
  @Column(nullable=false) public java.time.Instant recordedAt;
}
