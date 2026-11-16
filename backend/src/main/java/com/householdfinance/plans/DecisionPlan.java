package com.householdfinance.plans;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "decision_plans")
public class DecisionPlan {
  @Column(length=36) public String ownerId = com.householdfinance.access.AccessContext.actor()==null?"owner":com.householdfinance.access.AccessContext.userId();
  @Id
  @Column(length = 36)
  public String id;

  @Version public long version;

  @Lob
  @Column(nullable = false)
  public String payload;

  @Column(nullable = false)
  public Instant updatedAt;
}
