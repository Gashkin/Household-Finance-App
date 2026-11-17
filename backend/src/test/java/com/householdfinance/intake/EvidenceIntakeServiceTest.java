package com.householdfinance.intake;

import com.householdfinance.service.CashPositionService;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@org.junit.jupiter.api.extension.ExtendWith(com.householdfinance.access.OwnerFixture.class)
class EvidenceIntakeServiceTest {
    final CashPositionService cash = mock(CashPositionService.class);
    final EvidenceModelGateway model = mock(EvidenceModelGateway.class);
    final MutableClock clock = new MutableClock();
    final EvidenceIntakeService service = new EvidenceIntakeService(cash, model, clock);
    static class MutableClock extends Clock {
        Instant instant = Instant.parse("2026-11-14T12:00:00Z");
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return instant; }
    }
    CashPositionService.Snapshot snapshot() {
        return new CashPositionService.Snapshot("saved", LocalDate.of(2026,11,14), clock.instant(), "Source", "REVIEWED",
            List.of(), List.of(), 100000, 10000, 90000, 40000, 50000, false, List.of());
    }
    EvidenceIntakeService.Approval approval() {
        return new EvidenceIntakeService.Approval(LocalDate.of(2026,11,14), 40000, true,
            List.of(new EvidenceIntakeService.Row(1, "CASH", "Corrected account", 100000), new EvidenceIntakeService.Row(2, "COMMITMENT", "Electricity", 10000)));
    }
    @Test void localDraftCitesSourceAndCannotWriteFinancialFacts() {
        var draft = service.create("Current account: EUR 1,000.00\nElectricity bill due: €100.00", "Statement", false);
        assertEquals("LOCAL", draft.method()); assertEquals(2, draft.proposals().size());
        assertEquals("1000.00", draft.proposals().getFirst().amountEuro());
        assertEquals("Current account: EUR 1,000.00", draft.proposals().getFirst().quote());
        assertEquals("COMMITMENT", draft.proposals().get(1).kind());
        verifyNoInteractions(cash, model);
    }
    @Test void reviewedCorrectionIsIdempotentAndDifferentRetryIsRejected() {
        var draft = service.create("Account: EUR 999\nBill: EUR 100", "Statement", false);
        when(cash.save(any())).thenReturn(snapshot());
        assertEquals("saved", service.approve(draft.id(), approval()).id());
        assertEquals("saved", service.approve(draft.id(), approval()).id());
        verify(cash, times(1)).save(argThat(input -> input.cashBalances().getFirst().amountMinor() == 100000
            && input.sourceNote().contains("Statement") && !input.sourceNote().contains("EUR 999")));
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> service.approve(draft.id(),
            new EvidenceIntakeService.Approval(approval().asOfDate(), 1, true, approval().rows())));
    }
    @Test void unacknowledgedExpiredAndDiscardedDraftsCannotApprove() {
        var draft = service.create("Account: EUR 10", "Source", false);
        assertThrows(IllegalArgumentException.class, () -> service.approve(draft.id(), new EvidenceIntakeService.Approval(approval().asOfDate(), 0, false, approval().rows())));
        clock.instant = clock.instant.plusSeconds(601); service.purge();
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> service.approve(draft.id(), approval()));
        var other = service.create("Account: EUR 10", "Source", false); service.discard(other.id());
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> service.approve(other.id(), approval()));
        verifyNoInteractions(cash);
    }
    @Test void modelOutputMustMatchItsSourceAndFailureFallsBackLocally() throws Exception {
        when(model.extract(anyString(), eq(true))).thenReturn(Optional.of(List.of(new EvidenceModelGateway.Candidate(1,"CASH","Account","999"))));
        var draft = service.create("Account: EUR 10", "Source", true);
        assertEquals("LOCAL", draft.method()); assertEquals("10", draft.proposals().getFirst().amountEuro());
        assertTrue(draft.warnings().stream().anyMatch(w -> w.contains("source checks")));
        when(model.extract(anyString(), eq(true))).thenReturn(Optional.of(List.of(new EvidenceModelGateway.Candidate(1,"CASH","Account","10"))));
        assertEquals("MODEL", service.create("Account: EUR 10", "Source", true).method()); verifyNoInteractions(cash);
    }
    @Test void malformedOrOversizedSourceAndFractionalCentsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.create("x".repeat(4001), "Source", false));
        assertThrows(IllegalArgumentException.class, () -> service.create("", "Source", false));
        assertThrows(IllegalArgumentException.class, () -> EvidenceIntakeService.minor("1.001"));
        assertThrows(IllegalArgumentException.class, () -> EvidenceIntakeService.minor("-1"));
        assertTrue(service.create("Account: EUR 1.001", "Source", false).proposals().isEmpty());
    }
    @Test void negativeBalancesAndCreditLimitsAreNeverPositiveCashSuggestions() throws Exception {
        String source = "Overdrawn: -€100\nAccount: (EUR 200)\nAccount: EUR 300 DR\nCredit limit: EUR 400\nSalary: EUR 500";
        assertTrue(service.create(source, "Source", false).proposals().isEmpty());
        when(model.extract(anyString(), eq(true))).thenReturn(Optional.of(List.of(new EvidenceModelGateway.Candidate(4,"CASH","Credit limit","400"))));
        var rejected = service.create(source, "Source", true);
        assertEquals("LOCAL", rejected.method()); assertTrue(rejected.proposals().isEmpty());
    }
    @Test void capacityAndRepeatedFactsAreBounded() {
        var draft = service.create("Account: EUR 10", "Source", false);
        var row = new EvidenceIntakeService.Row(1, "CASH", "Account", 1000);
        assertThrows(IllegalArgumentException.class, () -> service.approve(draft.id(), new EvidenceIntakeService.Approval(approval().asOfDate(), 0, true, List.of(row,row))));
        for (int i=1;i<32;i++) service.create("Account: EUR 10", "Source", false);
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> service.create("Account: EUR 10", "Source", false));
    }
}
