package com.householdfinance.ledger;

public interface LedgerAuditRepository
    extends org.springframework.data.jpa.repository.JpaRepository<LedgerAudit, String> {
  java.util.List<LedgerAudit> findByAccountIdOrderByRecordedAtDesc(String accountId);
}
