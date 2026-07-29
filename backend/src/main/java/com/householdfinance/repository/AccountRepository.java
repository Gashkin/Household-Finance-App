package com.householdfinance.repository;

import com.householdfinance.entity.AccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AccountRepository extends JpaRepository<AccountEntity, String> {
	Optional<AccountEntity> findByNameIgnoreCase(String name);
}
