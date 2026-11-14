package com.householdfinance.service;

import com.householdfinance.entity.CashPositionLine;
import com.householdfinance.entity.CashPositionSnapshotEntity;
import com.householdfinance.repository.CashPositionSnapshotRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CashPositionService {
    private static final long MAX_SAFE_INTEGER = 9_007_199_254_740_991L;
    private static final int MAX_LINES = 30;

    public record Line(String label, long amountMinor) {}
    public record Input(LocalDate asOfDate, String sourceNote, String verification,
            List<Line> cashBalances, List<Line> upcomingCommitments, long minimumReserveMinor) {}
    public record Snapshot(String id, LocalDate asOfDate, Instant recordedAt,
            String sourceNote, String verification,
            List<Line> cashBalances, List<Line> upcomingCommitments,
            long liquidCashMinor, long committedMinor, long availableAfterCommitmentsMinor,
            long minimumReserveMinor, long discretionaryAfterReserveMinor,
            boolean stale, List<String> warnings) {}

    private final CashPositionSnapshotRepository repository;
    private final Clock clock;

    public CashPositionService(CashPositionSnapshotRepository repository) {
        this.repository = repository;
        this.clock = Clock.system(java.time.ZoneId.of("Europe/Dublin"));
    }

    @Transactional
    public Snapshot save(Input input) {
        if (input == null || input.asOfDate() == null || input.asOfDate().isAfter(LocalDate.now(clock)))
            throw new IllegalArgumentException("asOfDate must be today or an earlier date");
        if (input.sourceNote() == null || input.sourceNote().isBlank() || input.sourceNote().trim().length() > 200)
            throw new IllegalArgumentException("sourceNote must contain 1–200 characters");
        if (!"REVIEWED".equals(input.verification()) && !"ESTIMATED".equals(input.verification()))
            throw new IllegalArgumentException("verification must be REVIEWED or ESTIMATED");
        requireAmount(input.minimumReserveMinor(), "minimumReserveMinor");
        if (input.cashBalances() == null || input.cashBalances().isEmpty() || input.cashBalances().size() > MAX_LINES)
            throw new IllegalArgumentException("Provide 1–30 cash balances");
        if (input.upcomingCommitments() == null || input.upcomingCommitments().size() > MAX_LINES)
            throw new IllegalArgumentException("Provide 0–30 upcoming commitments");

        List<CashPositionLine> lines = new ArrayList<>();
        long cash = 0, committed = 0;
        for (Line line : input.cashBalances()) {
            validateLine(line);
            cash = checkedSum(cash, line.amountMinor());
            lines.add(new CashPositionLine(CashPositionLine.Kind.CASH, line.label().trim(), line.amountMinor()));
        }
        for (Line line : input.upcomingCommitments()) {
            validateLine(line);
            committed = checkedSum(committed, line.amountMinor());
            lines.add(new CashPositionLine(CashPositionLine.Kind.COMMITMENT, line.label().trim(), line.amountMinor()));
        }
        CashPositionSnapshotEntity saved = repository.saveAndFlush(new CashPositionSnapshotEntity(
                UUID.randomUUID().toString(), input.asOfDate(), Instant.now(clock),
                input.sourceNote().trim(), input.verification(), input.minimumReserveMinor(), lines));
        return toSnapshot(saved);
    }

    @Transactional(readOnly = true)
    public Optional<Snapshot> latest() {
        return repository.findFirstByOrderByRecordedAtDescIdDesc().map(this::toSnapshot);
    }

    private Snapshot toSnapshot(CashPositionSnapshotEntity entity) {
        List<Line> cash = new ArrayList<>();
        List<Line> commitments = new ArrayList<>();
        long cashTotal = 0, committedTotal = 0;
        for (CashPositionLine line : entity.getLines()) {
            Line value = new Line(line.getLabel(), line.getAmountMinor());
            if (line.getKind() == CashPositionLine.Kind.CASH) {
                cash.add(value);
                cashTotal = checkedSum(cashTotal, value.amountMinor());
            } else {
                commitments.add(value);
                committedTotal = checkedSum(committedTotal, value.amountMinor());
            }
        }
        long afterCommitments = cashTotal - committedTotal;
        long discretionary = Math.max(0, afterCommitments - entity.getMinimumReserveMinor());
        boolean stale = ChronoUnit.DAYS.between(entity.getAsOfDate(), LocalDate.now(clock)) > 30;
        List<String> warnings = new ArrayList<>();
        if (stale) warnings.add("Cash evidence is more than 30 days old; review it before a financial decision");
        if ("ESTIMATED".equals(entity.getVerification())) warnings.add("Cash position is an estimate, not reviewed evidence");
        if (afterCommitments < 0) warnings.add("Upcoming commitments exceed reported cash; this position cannot fund an extra repayment");
        if (afterCommitments < entity.getMinimumReserveMinor()) warnings.add("Cash after commitments is below the protected reserve");
        return new Snapshot(entity.getId(), entity.getAsOfDate(), entity.getRecordedAt(),
                entity.getSourceNote(), entity.getVerification(), List.copyOf(cash), List.copyOf(commitments),
                cashTotal, committedTotal, afterCommitments, entity.getMinimumReserveMinor(),
                discretionary, stale, List.copyOf(warnings));
    }

    private static void validateLine(Line line) {
        if (line == null || line.label() == null || line.label().isBlank() || line.label().trim().length() > 100)
            throw new IllegalArgumentException("Each entry needs a label of 1–100 characters");
        requireAmount(line.amountMinor(), "entry amountMinor");
    }

    private static void requireAmount(long amount, String name) {
        if (amount < 0 || amount > MAX_SAFE_INTEGER)
            throw new IllegalArgumentException(name + " must be a non-negative safe integer");
    }

    private static long checkedSum(long a, long b) {
        long sum = Math.addExact(a, b);
        if (sum > MAX_SAFE_INTEGER) throw new IllegalArgumentException("Cash totals exceed the safe integer range");
        return sum;
    }
}
