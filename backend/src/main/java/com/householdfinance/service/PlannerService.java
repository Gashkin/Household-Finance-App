package com.householdfinance.service;

import com.householdfinance.entity.AccountEntity;
import com.householdfinance.entity.CategoryEntity;
import com.householdfinance.entity.CommitmentEntity;
import com.householdfinance.repository.AccountRepository;
import com.householdfinance.repository.CategoryRepository;
import com.householdfinance.repository.CommitmentRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
@SuppressWarnings("null")
public class PlannerService {

    public record Account(String id, String name, String type, double balance) {}
    public record AccountUpsertRequest(String id, String name, String type, double balance) {}
    public record Category(String id, String name, String budget) {}
    public record CategoryUpsertRequest(String id, String name, String budget) {}
    public record Commitment(String id, String title, double amount, String dueDate) {}
    public record CommitmentUpsertRequest(String id, String title, double amount, String dueDate) {}
    public record PlannerData(List<Account> accounts, List<Category> categories, List<Commitment> commitments) {}

    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final CommitmentRepository commitmentRepository;

    public PlannerService(
            AccountRepository accountRepository,
            CategoryRepository categoryRepository,
            CommitmentRepository commitmentRepository
    ) {
        this.accountRepository = accountRepository;
        this.categoryRepository = categoryRepository;
        this.commitmentRepository = commitmentRepository;
    }

    @PostConstruct
    @Transactional
    public void ensureSeedData() {
        if (accountRepository.count() == 0) {
            accountRepository.save(new AccountEntity("A1", "Checking", "Bank", 4120.75));
            accountRepository.save(new AccountEntity("A2", "Savings", "Bank", 12500.00));
            accountRepository.save(new AccountEntity("A3", "Credit Card", "Credit", -850.10));
        }

        if (categoryRepository.count() == 0) {
            categoryRepository.save(new CategoryEntity("C1", "Housing", "$1,200"));
            categoryRepository.save(new CategoryEntity("C2", "Groceries", "$450"));
            categoryRepository.save(new CategoryEntity("C3", "Utilities", "$250"));
        }

        if (commitmentRepository.count() == 0) {
            commitmentRepository.save(new CommitmentEntity("M1", "Rent", 1200.00, "2026-07-01"));
            commitmentRepository.save(new CommitmentEntity("M2", "Electricity", 95.60, "2026-07-05"));
            commitmentRepository.save(new CommitmentEntity("M3", "Internet", 65.00, "2026-07-10"));
        }
    }

    public PlannerData getPlannerData() {
        return new PlannerData(
                accountRepository.findAll().stream()
                        .sorted(Comparator.comparing(entity -> Objects.toString(entity.getId(), "")))
                        .map(entity -> new Account(entity.getId(), entity.getName(), entity.getType(), entity.getBalance()))
                        .toList(),
                categoryRepository.findAll().stream()
                        .sorted(Comparator.comparing(entity -> Objects.toString(entity.getId(), "")))
                        .map(entity -> new Category(entity.getId(), entity.getName(), entity.getBudget()))
                        .toList(),
                commitmentRepository.findAll().stream()
                        .sorted(Comparator.comparing(entity -> Objects.toString(entity.getId(), "")))
                        .map(entity -> new Commitment(entity.getId(), entity.getTitle(), entity.getAmount(), entity.getDueDate()))
                        .toList()
        );
    }

    @Transactional
    public Account createAccount(AccountUpsertRequest request) {
        validateAccountRequest(request, false);
        var requestId = nonNull(request.id());
        if (accountRepository.existsById(requestId)) {
            throw new IllegalArgumentException("Account id already exists: " + requestId);
        }

        var saved = accountRepository.save(new AccountEntity(requestId, request.name(), request.type(), request.balance()));
        return new Account(saved.getId(), saved.getName(), saved.getType(), saved.getBalance());
    }

    @Transactional
    public Account updateAccount(String id, AccountUpsertRequest request) {
        validateAccountRequest(request, true);
        var entityId = nonNull(id);
        var entity = accountRepository.findById(entityId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + id));

        entity.setName(request.name());
        entity.setType(request.type());
        entity.setBalance(request.balance());

        var saved = accountRepository.save(entity);
        return new Account(saved.getId(), saved.getName(), saved.getType(), saved.getBalance());
    }

    @Transactional
    public void deleteAccount(String id) {
        var entityId = nonNull(id);
        if (!accountRepository.existsById(entityId)) {
            throw new IllegalArgumentException("Account not found: " + id);
        }
        accountRepository.deleteById(entityId);
    }

    @Transactional
    public Category createCategory(CategoryUpsertRequest request) {
        validateCategoryRequest(request, false);
        var requestId = nonNull(request.id());
        if (categoryRepository.existsById(requestId)) {
            throw new IllegalArgumentException("Category id already exists: " + requestId);
        }

        var saved = categoryRepository.save(new CategoryEntity(requestId, request.name(), request.budget()));
        return new Category(saved.getId(), saved.getName(), saved.getBudget());
    }

    @Transactional
    public Category updateCategory(String id, CategoryUpsertRequest request) {
        validateCategoryRequest(request, true);
        var entityId = nonNull(id);
        var entity = categoryRepository.findById(entityId)
                .orElseThrow(() -> new IllegalArgumentException("Category not found: " + id));

        entity.setName(request.name());
        entity.setBudget(request.budget());

        var saved = categoryRepository.save(entity);
        return new Category(saved.getId(), saved.getName(), saved.getBudget());
    }

    @Transactional
    public void deleteCategory(String id) {
        var entityId = nonNull(id);
        if (!categoryRepository.existsById(entityId)) {
            throw new IllegalArgumentException("Category not found: " + id);
        }
        categoryRepository.deleteById(entityId);
    }

    @Transactional
    public Commitment createCommitment(CommitmentUpsertRequest request) {
        validateCommitmentRequest(request, false);
        var requestId = nonNull(request.id());
        if (commitmentRepository.existsById(requestId)) {
            throw new IllegalArgumentException("Commitment id already exists: " + requestId);
        }

        var saved = commitmentRepository.save(
                new CommitmentEntity(requestId, request.title(), request.amount(), request.dueDate())
        );
        return new Commitment(saved.getId(), saved.getTitle(), saved.getAmount(), saved.getDueDate());
    }

    @Transactional
    public Commitment updateCommitment(String id, CommitmentUpsertRequest request) {
        validateCommitmentRequest(request, true);
        var entityId = nonNull(id);
        var entity = commitmentRepository.findById(entityId)
                .orElseThrow(() -> new IllegalArgumentException("Commitment not found: " + id));

        entity.setTitle(request.title());
        entity.setAmount(request.amount());
        entity.setDueDate(request.dueDate());

        var saved = commitmentRepository.save(entity);
        return new Commitment(saved.getId(), saved.getTitle(), saved.getAmount(), saved.getDueDate());
    }

    @Transactional
    public void deleteCommitment(String id) {
        var entityId = nonNull(id);
        if (!commitmentRepository.existsById(entityId)) {
            throw new IllegalArgumentException("Commitment not found: " + id);
        }
        commitmentRepository.deleteById(entityId);
    }

    private void validateAccountRequest(AccountUpsertRequest request, boolean update) {
        if (request == null) {
            throw new IllegalArgumentException("Account payload is required.");
        }
        if (!update && isBlank(request.id())) {
            throw new IllegalArgumentException("Account id is required.");
        }
        if (isBlank(request.name())) {
            throw new IllegalArgumentException("Account name is required.");
        }
        if (isBlank(request.type())) {
            throw new IllegalArgumentException("Account type is required.");
        }
    }

    private void validateCategoryRequest(CategoryUpsertRequest request, boolean update) {
        if (request == null) {
            throw new IllegalArgumentException("Category payload is required.");
        }
        if (!update && isBlank(request.id())) {
            throw new IllegalArgumentException("Category id is required.");
        }
        if (isBlank(request.name())) {
            throw new IllegalArgumentException("Category name is required.");
        }
        if (isBlank(request.budget())) {
            throw new IllegalArgumentException("Category budget is required.");
        }
    }

    private void validateCommitmentRequest(CommitmentUpsertRequest request, boolean update) {
        if (request == null) {
            throw new IllegalArgumentException("Commitment payload is required.");
        }
        if (!update && isBlank(request.id())) {
            throw new IllegalArgumentException("Commitment id is required.");
        }
        if (isBlank(request.title())) {
            throw new IllegalArgumentException("Commitment title is required.");
        }
        if (isBlank(request.dueDate())) {
            throw new IllegalArgumentException("Commitment dueDate is required.");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String nonNull(String value) {
        return Objects.requireNonNull(value);
    }
}
