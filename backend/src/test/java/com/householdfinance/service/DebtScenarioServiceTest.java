package com.householdfinance.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DebtScenarioServiceTest {
    private final DebtScenarioService service = new DebtScenarioService();

    @Test void extraRepaymentReducesInterestAndTerm() {
        var result = service.compare(new DebtScenarioService.Input(100000, 12, 10000, 20000, 50000, 10000));
        assertEquals(11, result.baseline().months());
        assertTrue(result.interestSavedMinor() > 0);
        assertTrue(result.monthsSaved() > 0);
        assertEquals(30000, result.remainingCashMinor());
    }

    @Test void warnsWhenReserveBreached() {
        var result = service.compare(new DebtScenarioService.Input(100000, 12, 10000, 20000, 25000, 10000));
        assertEquals(5000, result.reserveShortfallMinor());
        assertFalse(result.warnings().isEmpty());
    }

    @Test void rejectsInsufficientRepayment() {
        assertThrows(IllegalArgumentException.class, () -> service.compare(
                new DebtScenarioService.Input(100000, 12, 500, 0, 20000, 10000)));
    }

    @Test void supportsImmediateFullPayoff() {
        var result = service.compare(new DebtScenarioService.Input(100000, 0, 10000, 100000, 100000, 0));
        assertEquals(0, result.withExtraPayment().months());
        assertEquals(0, result.interestSavedMinor());
    }
}
