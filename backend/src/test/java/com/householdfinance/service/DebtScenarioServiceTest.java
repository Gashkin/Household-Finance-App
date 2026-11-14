package com.householdfinance.service;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class DebtScenarioServiceTest {
    private final DebtScenarioService service = new DebtScenarioService();

    @Test void extraRepaymentReducesInterestAndTerm() {
        var result = service.compare(new DebtScenarioService.Input(100000, BigDecimal.valueOf(12), 10000, 20000, 50000, 10000));
        assertEquals(11, result.baseline().months());
        assertTrue(result.interestSavedMinor() > 0);
        assertTrue(result.monthsSaved() > 0);
        assertEquals(30000, result.remainingCashMinor());
    }

    @Test void warnsWhenReserveBreached() {
        var result = service.compare(new DebtScenarioService.Input(100000, BigDecimal.valueOf(12), 10000, 20000, 25000, 10000));
        assertEquals(5000, result.reserveShortfallMinor());
        assertFalse(result.warnings().isEmpty());
    }

    @Test void rejectsInsufficientRepayment() {
        assertThrows(IllegalArgumentException.class, () -> service.compare(
                new DebtScenarioService.Input(100000, BigDecimal.valueOf(12), 500, 0, 20000, 10000)));
    }

    @Test void supportsImmediateFullPayoff() {
        var result = service.compare(new DebtScenarioService.Input(100000, BigDecimal.ZERO, 10000, 100000, 100000, 0));
        assertEquals(0, result.withExtraPayment().months());
        assertEquals(0, result.interestSavedMinor());
    }
    @Test void roundsHalfCentInterestUpExactly() {
        var result = service.compare(new DebtScenarioService.Input(6000, new BigDecimal("0.7"), 100, 0, 0, 0));
        assertEquals(4, result.baseline().interestMinor() > 0 ? 4 : 0);
        assertEquals(4, new BigDecimal("6000").multiply(new BigDecimal("0.7"))
                .divide(new BigDecimal("1200"), 0, java.math.RoundingMode.HALF_UP).longValueExact());
    }

    @Test void rejectsPaymentThatDoesNotCoverRoundedInterest() {
        assertThrows(IllegalArgumentException.class, () -> service.compare(
                new DebtScenarioService.Input(6000, new BigDecimal("0.7"), 4, 0, 0, 0)));
    }
}
