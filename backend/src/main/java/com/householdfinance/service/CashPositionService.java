package com.householdfinance.service;

import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class CashPositionService {
    public static final long LIMIT = 1_000_000_000_000L;
    public record Balance(String name, Long balanceMinor, LocalDate asOf, String source) {}
    public record Commitment(String name, Long amountMinor, LocalDate dueOn) {}
    public record Input(LocalDate asOf, LocalDate through, List<Balance> balances, List<Commitment> commitments) {}
    public record Result(LocalDate asOf, LocalDate through, long liquidCashMinor, long commitmentsMinor,
                         long availableCashMinor, List<Balance> balances, List<Commitment> includedCommitments,
                         List<String> warnings, String calculationVersion) {}

    public Result calculate(Input input) {
        if (input == null || input.asOf() == null || input.through() == null || input.through().isBefore(input.asOf())
            || input.through().isAfter(input.asOf().plusYears(1)))
            throw new IllegalArgumentException("Choose a valid cash planning period of at most one year");
        if (input.balances() == null || input.balances().isEmpty() || input.balances().size() > 100
            || input.commitments() == null || input.commitments().size() > 500)
            throw new IllegalArgumentException("Provide 1–100 balances and at most 500 commitments");
        long cash = 0, committed = 0;
        var warnings = new ArrayList<String>();
        var names = new HashSet<String>();
        for (var balance : input.balances()) {
            if (balance == null) throw new IllegalArgumentException("Every balance must be complete");
            text(balance.name()); text(balance.source());
            if (!names.add(balance.name().strip().toLowerCase(Locale.ROOT)))
                throw new IllegalArgumentException("Account names must be unique");
            amount(balance.balanceMinor(), true);
            if (balance.asOf() == null || balance.asOf().isAfter(input.asOf()))
                throw new IllegalArgumentException("Balance dates must not be after the position date");
            if (!balance.asOf().equals(input.asOf())) warnings.add(balance.name() + ": balance is older than the position date; reconcile intervening activity");
            cash = Math.addExact(cash, balance.balanceMinor());
        }
        var included = new ArrayList<Commitment>();
        for (var commitment : input.commitments()) {
            if (commitment == null) throw new IllegalArgumentException("Every commitment must be complete");
            text(commitment.name()); amount(commitment.amountMinor(), false);
            if (commitment.dueOn() == null) throw new IllegalArgumentException("Commitment due dates are required");
            if (commitment.dueOn().isBefore(input.asOf())) warnings.add(commitment.name() + ": overdue item excluded; confirm whether it is already paid");
            else if (!commitment.dueOn().isAfter(input.through())) {
                committed = Math.addExact(committed, commitment.amountMinor()); included.add(commitment);
            }
        }
        long available = Math.subtractExact(cash, committed);
        if (available < 0) warnings.add("Listed commitments exceed liquid cash. No additional borrowing capacity is counted.");
        warnings.add("Only listed liquid balances and unpaid commitments are included; future income and unlisted spending are excluded.");
        return new Result(input.asOf(), input.through(), cash, committed, available,
            List.copyOf(input.balances()), List.copyOf(included), List.copyOf(warnings), "cash-position-v1");
    }
    private void text(String value) {
        if (value == null || value.isBlank() || value.length() > 160) throw new IllegalArgumentException("Names and sources must contain 1–160 characters");
    }
    private void amount(Long value, boolean signed) {
        if (value == null || value > LIMIT || value < (signed ? -LIMIT : 0))
            throw new IllegalArgumentException("Amounts must be within the supported range; commitments cannot be negative");
    }
}
