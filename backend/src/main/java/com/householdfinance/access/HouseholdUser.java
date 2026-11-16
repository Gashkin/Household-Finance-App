package com.householdfinance.access;

import jakarta.persistence.*;

@Entity @Table(name="household_users")
public class HouseholdUser {
  @Id @Column(length=36) public String id;
  @Column(nullable=false,unique=true,length=40) public String username;
  @Column(length=160) public String credential;
  @Column(nullable=false,length=40) public String roles = "";
  @Column(nullable=false,length=16) public String state = "INVITED";
  @Column(nullable=false) public long revision;
  @Column(length=64) public String invitationHash;
  public java.time.Instant invitationExpiry;
  @Column(length=64) public String recoveryHash;
}
