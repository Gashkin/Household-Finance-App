package com.householdfinance.forecast;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "forecast_plans")
public class ForecastPlan {
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
