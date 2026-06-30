package com.householdfinance.repository;

import com.householdfinance.entity.CommitmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommitmentRepository extends JpaRepository<CommitmentEntity, String> {
}
