package com.householdfinance.service;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class CashPositionServiceTest {
    final CashPositionService service = new CashPositionService();
    final LocalDate day = LocalDate.of(2026, 11, 14);
    CashPositionService.Balance balance(long value, LocalDate date) { return new CashPositionService.Balance("Current", value, date, "Statement"); }
    @Test void netsOverdraftAndIncludesBothDateBoundaries() {
        var result = service.calculate(new CashPositionService.Input(day, day.plusDays(6),
            List.of(balance(100000, day), new CashPositionService.Balance("Other", -20000L, day, "App")),
            List.of(new CashPositionService.Commitment("Today", 10000L, day), new CashPositionService.Commitment("End", 20000L, day.plusDays(6)),
            new CashPositionService.Commitment("Later", 90000L, day.plusDays(7)))));
        assertEquals(80000, result.liquidCashMinor()); assertEquals(30000, result.commitmentsMinor()); assertEquals(50000, result.availableCashMinor());
        assertEquals(2, result.includedCommitments().size()); assertEquals("Statement", result.balances().getFirst().source());
    }
    @Test void flagsStaleAndOverdueWithoutSilentlyCountingOverdueAgain() {
        var result = service.calculate(new CashPositionService.Input(day, day, List.of(balance(100, day.minusDays(1))),
            List.of(new CashPositionService.Commitment("Old", 500L, day.minusDays(1)))));
        assertEquals(100, result.availableCashMinor()); assertEquals(3, result.warnings().size());
    }
    @Test void rejectsFutureBalancesAndMissingAmounts() {
        assertThrows(IllegalArgumentException.class, () -> service.calculate(new CashPositionService.Input(day, day, List.of(balance(100, day.plusDays(1))), List.of())));
        assertThrows(IllegalArgumentException.class, () -> service.calculate(new CashPositionService.Input(day, day, List.of(new CashPositionService.Balance("X", null, day, "App")), List.of())));
    }
    @Test void preservesNegativeAvailabilityAndRejectsDuplicateAccounts() {
        assertEquals(-100, service.calculate(new CashPositionService.Input(day, day, List.of(balance(-100, day)), List.of())).availableCashMinor());
        assertThrows(IllegalArgumentException.class, () -> service.calculate(new CashPositionService.Input(day, day, List.of(balance(100, day), balance(100, day)), List.of())));
    }
}
