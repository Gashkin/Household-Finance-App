package com.householdfinance.ledger;

public interface LedgerAccountRepository
    extends org.springframework.data.jpa.repository.JpaRepository<LedgerAccount, String> {
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select a from LedgerAccount a where a.id = :id")
  java.util.Optional<LedgerAccount> findByIdForUpdate(
      @org.springframework.data.repository.query.Param("id") String id);
}
