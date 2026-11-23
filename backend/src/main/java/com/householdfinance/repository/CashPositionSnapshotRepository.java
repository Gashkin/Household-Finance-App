package com.householdfinance.repository;

import com.householdfinance.entity.CashPositionSnapshotEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CashPositionSnapshotRepository extends JpaRepository<CashPositionSnapshotEntity, String> {
    Optional<CashPositionSnapshotEntity> findFirstByOrderByRecordedAtDescIdDesc();
    boolean existsByScopeRefsContaining(String accountId);
}
