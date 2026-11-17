package com.householdfinance.service;

import com.householdfinance.entity.AccountEntity;
import com.householdfinance.entity.CategoryEntity;
import com.householdfinance.entity.MonthlyCommitmentEntity;
import com.householdfinance.entity.TransactionEntity;
import com.householdfinance.entity.TransactionType;
import com.householdfinance.repository.AccountRepository;
import com.householdfinance.repository.CategoryRepository;
import com.householdfinance.repository.MonthlyCommitmentRepository;
import com.householdfinance.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
@SuppressWarnings("null")
public class PlannerService {

    public record Account(String id, String name, String type, double balance) {}
    public record AccountUpsertRequest(String id, String name, String type, java.math.BigDecimal balance) {}
    public record Category(String id, String name, String budget) {}
    public record CategoryUpsertRequest(String id, String name, String budget) {}
        public record Commitment(String id, String title, double amount, String dueDate, String category, Integer dayOfMonth) {}
        public record CommitmentUpsertRequest(String id, String title, java.math.BigDecimal amount, String dueDate, String category, Integer dayOfMonth) {}
    public record PlannerData(List<Account> accounts, List<Category> categories, List<Commitment> commitments) {}

    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
        private final MonthlyCommitmentRepository monthlyCommitmentRepository;
        private final TransactionRepository transactionRepository;

        private static final DateTimeFormatter DUE_MONTH_LABEL = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("MMMyy")
            .toFormatter(Locale.ENGLISH);

    public PlannerService(
            AccountRepository accountRepository,
            CategoryRepository categoryRepository,
            MonthlyCommitmentRepository monthlyCommitmentRepository,
            TransactionRepository transactionRepository
    ) {
        this.accountRepository = accountRepository;
        this.categoryRepository = categoryRepository;
        this.monthlyCommitmentRepository = monthlyCommitmentRepository;
        this.transactionRepository = transactionRepository;
    }

    public PlannerData getPlannerData() {
        com.householdfinance.access.AccessContext.requireLegacy();
        var monthlyCommitments = monthlyCommitmentRepository.findAll().stream()
            .sorted(Comparator.comparing(entity -> Objects.toString(entity.getId(), "")))
            .map(entity -> new Commitment(
                entity.getId(),
                entity.getTitle(),
                entity.getAmount(),
                "Recurring",
                entity.getCategory(),
                entity.getDayOfMonth()
            ))
            .toList();

        var futureTransactions = transactionRepository.findByTxnTypeIn(List.of(
                TransactionType.ONE_OFF_FUTURE,
                TransactionType.MONTHLY_INSTANCE_FUTURE
            )).stream()
            .sorted(Comparator.comparing(entity -> Objects.toString(entity.getId(), "")))
            .map(entity -> new Commitment(
                entity.getId(),
                entity.getTitle(),
                entity.getAmount(),
                toDueDateString(entity.getTxnDate()),
                entity.getCategory(),
                null
            ))
            .toList();

        return new PlannerData(
                accountRepository.findAll().stream()
                        .sorted(Comparator.comparing(entity -> Objects.toString(entity.getId(), "")))
                        .map(entity -> new Account(entity.getId(), entity.getName(), entity.getType(), entity.getBalance()))
                        .toList(),
                categoryRepository.findAll().stream()
                        .sorted(Comparator.comparing(entity -> Objects.toString(entity.getId(), "")))
                        .map(entity -> new Category(entity.getId(), entity.getName(), entity.getBudget()))
                        .toList(),
                mergeCommitments(monthlyCommitments, futureTransactions)
        );
    }

    @Transactional
    public Account createAccount(AccountUpsertRequest request) {
        com.householdfinance.access.AccessContext.requireLegacy();
        validateAccountRequest(request, false);
        var requestId = nonNull(request.id());
        if (accountRepository.existsById(requestId)) {
            throw new IllegalArgumentException("Account id already exists: " + requestId);
        }

        var saved = accountRepository.save(new AccountEntity(requestId, request.name(), request.type(), LegacyMoney.require(request.balance(), true)));
        return new Account(saved.getId(), saved.getName(), saved.getType(), saved.getBalance());
    }

    @Transactional
    public Account updateAccount(String id, AccountUpsertRequest request) {
        com.householdfinance.access.AccessContext.requireLegacy();
        validateAccountRequest(request, true);
        var entityId = nonNull(id);
        var entity = accountRepository.findById(entityId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + id));

        entity.setName(request.name());
        entity.setType(request.type());
        entity.setBalance(LegacyMoney.require(request.balance(), true));

        var saved = accountRepository.save(entity);
        return new Account(saved.getId(), saved.getName(), saved.getType(), saved.getBalance());
    }

    @Transactional
    public void deleteAccount(String id) {
        com.householdfinance.access.AccessContext.requireLegacy();
        var entityId = nonNull(id);
        if (!accountRepository.existsById(entityId)) {
            throw new IllegalArgumentException("Account not found: " + id);
        }
        accountRepository.deleteById(entityId);
    }

    @Transactional
    public Category createCategory(CategoryUpsertRequest request) {
        com.householdfinance.access.AccessContext.requireLegacy();
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
        com.householdfinance.access.AccessContext.requireLegacy();
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
        com.householdfinance.access.AccessContext.requireLegacy();
        var entityId = nonNull(id);
        if (!categoryRepository.existsById(entityId)) {
            throw new IllegalArgumentException("Category not found: " + id);
        }
        categoryRepository.deleteById(entityId);
    }

    @Transactional
    public Commitment createCommitment(CommitmentUpsertRequest request) {
        com.householdfinance.access.AccessContext.requireLegacy();
        validateCommitmentRequest(request, false);
        var requestId = nonNull(request.id());
        if (monthlyCommitmentRepository.existsById(requestId) || transactionRepository.existsById(requestId)) {
            throw new IllegalArgumentException("Commitment id already exists: " + requestId);
        }

        if (isRecurringDueDate(request.dueDate())) {
            var saved = monthlyCommitmentRepository.save(
                    new MonthlyCommitmentEntity(
                            requestId,
                            request.title(),
                            LegacyMoney.require(request.amount(), false),
                            normalizeDayOfMonth(request.dayOfMonth()),
                            request.category()
                    )
            );
            return new Commitment(saved.getId(), saved.getTitle(), saved.getAmount(), "Recurring", saved.getCategory(), saved.getDayOfMonth());
        }

        var dueDate = parseNonRecurringDueDate(request.dueDate());
        var saved = transactionRepository.save(
                new TransactionEntity(
                        requestId,
                        dueDate,
                        request.title(),
                        LegacyMoney.require(request.amount(), false),
                        request.category(),
                        TransactionType.ONE_OFF_FUTURE,
                        null
                )
        );
            return new Commitment(saved.getId(), saved.getTitle(), saved.getAmount(), toDueDateString(saved.getTxnDate()), saved.getCategory(), null);
    }

    @Transactional
    public Commitment updateCommitment(String id, CommitmentUpsertRequest request) {
        com.householdfinance.access.AccessContext.requireLegacy();
        validateCommitmentRequest(request, true);
        var entityId = nonNull(id);
        if (monthlyCommitmentRepository.existsById(entityId)) {
            if (!isRecurringDueDate(request.dueDate())) {
                throw new IllegalArgumentException("Recurring commitment must use dueDate 'Recurring'.");
            }
            var entity = monthlyCommitmentRepository.findById(entityId)
                    .orElseThrow(() -> new IllegalArgumentException("Commitment not found: " + id));
            entity.setTitle(request.title());
            entity.setAmount(LegacyMoney.require(request.amount(), false));
            entity.setCategory(request.category());
            entity.setDayOfMonth(normalizeDayOfMonth(request.dayOfMonth()));
            var saved = monthlyCommitmentRepository.save(entity);
            return new Commitment(saved.getId(), saved.getTitle(), saved.getAmount(), "Recurring", saved.getCategory(), saved.getDayOfMonth());
        }

        var entity = transactionRepository.findById(entityId)
                .orElseThrow(() -> new IllegalArgumentException("Commitment not found: " + id));

        if (isRecurringDueDate(request.dueDate())) {
            throw new IllegalArgumentException("One-off or future transaction commitments must use a dueDate like YYYY-MM-DD.");
        }

        entity.setTitle(request.title());
        entity.setAmount(LegacyMoney.require(request.amount(), false));
        entity.setCategory(request.category());
        entity.setTxnDate(parseNonRecurringDueDate(request.dueDate()));
        entity.setTxnType(TransactionType.ONE_OFF_FUTURE);

        var saved = transactionRepository.save(entity);
        return new Commitment(saved.getId(), saved.getTitle(), saved.getAmount(), toDueDateString(saved.getTxnDate()), saved.getCategory(), null);
    }

    @Transactional
    public void deleteCommitment(String id) {
        com.householdfinance.access.AccessContext.requireLegacy();
        var entityId = nonNull(id);
        if (monthlyCommitmentRepository.existsById(entityId)) {
            monthlyCommitmentRepository.deleteById(entityId);
            return;
        }
        if (transactionRepository.existsById(entityId)) {
            transactionRepository.deleteById(entityId);
            return;
        }
        if (!monthlyCommitmentRepository.existsById(entityId) && !transactionRepository.existsById(entityId)) {
            throw new IllegalArgumentException("Commitment not found: " + id);
        }
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
        LegacyMoney.require(request.balance(), true);
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
        LegacyMoney.parse(request.budget(), false);
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
        LegacyMoney.require(request.amount(), false);
        if (!isRecurringDueDate(request.dueDate())) {
            parseNonRecurringDueDate(request.dueDate());
        }
    }

    private boolean isRecurringDueDate(String dueDate) {
        return dueDate != null && "Recurring".equalsIgnoreCase(dueDate.trim());
    }

    private LocalDate parseNonRecurringDueDate(String dueDate) {
        try {
            return LocalDate.parse(dueDate.trim());
        } catch (DateTimeParseException ignored) {
        }

        try {
            var monthYear = java.time.YearMonth.parse(dueDate.trim(), DUE_MONTH_LABEL);
            return monthYear.atDay(1);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Commitment dueDate must be 'Recurring' or an ISO date like 2026-07-14.");
        }
    }

    private String toDueDateString(LocalDate date) {
        return date.toString();
    }

    private int normalizeDayOfMonth(Integer dayOfMonth) {
        if (dayOfMonth == null) {
            return 1;
        }
        if (dayOfMonth < 1 || dayOfMonth > 31) {
            throw new IllegalArgumentException("Commitment dayOfMonth must be between 1 and 31.");
        }
        return dayOfMonth;
    }

    private List<Commitment> mergeCommitments(List<Commitment> monthlyCommitments, List<Commitment> futureTransactions) {
        return java.util.stream.Stream.concat(monthlyCommitments.stream(), futureTransactions.stream())
                .sorted(Comparator.comparing(commitment -> Objects.toString(commitment.id(), "")))
                .toList();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String nonNull(String value) {
        return Objects.requireNonNull(value);
    }
}
