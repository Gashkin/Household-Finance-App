package com.householdfinance.ledger;

public interface LedgerEntryRepository
    extends org.springframework.data.jpa.repository.JpaRepository<LedgerEntry, String> {
  java.util.List<LedgerEntry> findByAccountIdOrderByDateAscReferenceAsc(String accountId);

  java.util.Optional<LedgerEntry> findByAccountIdAndReference(String accountId, String reference);
}
