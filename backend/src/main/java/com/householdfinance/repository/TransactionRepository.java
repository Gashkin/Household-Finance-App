package com.householdfinance.repository;

import com.householdfinance.entity.TransactionEntity;
import com.householdfinance.entity.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface TransactionRepository extends JpaRepository<TransactionEntity, String> {
    List<TransactionEntity> findByTxnTypeIn(Collection<TransactionType> txnTypes);
}
