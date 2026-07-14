package com.householdfinance.repository;

import com.householdfinance.entity.MonthlyCommitmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MonthlyCommitmentRepository extends JpaRepository<MonthlyCommitmentEntity, String> {
}
