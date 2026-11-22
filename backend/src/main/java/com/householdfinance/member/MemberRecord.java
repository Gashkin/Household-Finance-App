package com.householdfinance.member;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name="member_records")
public class MemberRecord {
  @Id @Column(length=80) public String id;
  @Column(nullable=false,length=16) public String kind;
  @Column(nullable=false,length=36) public String ownerId;
  @Version public long version;
  @Lob @Column(nullable=false) public String payload;
  @Column(nullable=false) public Instant updatedAt;
}
