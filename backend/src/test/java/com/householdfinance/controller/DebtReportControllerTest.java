package com.householdfinance.controller;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest @AutoConfigureMockMvc
class DebtReportControllerTest {
    @Autowired MockMvc mvc;
    @Autowired com.householdfinance.service.CashPositionService cash;
    final String input = """
        {"balanceMinor":100000,"annualRatePercent":12,"monthlyPaymentMinor":10000,"extraPaymentMinor":20000,"liquidCashMinor":50000,"minimumReserveMinor":40000}
        """;
    @Test void exportsReproducibleInputsAndRecomputedResults() throws Exception {
        mvc.perform(post("/api/debt-scenarios/report").contentType("application/json").content(input))
            .andExpect(status().isOk()).andExpect(jsonPath("$.schemaVersion").value("debt-comparison-report-v1"))
            .andExpect(jsonPath("$.calculationVersion").value("debt-amortisation-v1"))
            .andExpect(jsonPath("$.currency").value("EUR")).andExpect(jsonPath("$.generatedAt").exists())
            .andExpect(jsonPath("$.input.balanceMinor").value(100000))
            .andExpect(jsonPath("$.result.baseline.interestMinor").value(5898))
            .andExpect(jsonPath("$.result.reserveShortfallMinor").value(10000))
            .andExpect(jsonPath("$.result.assumptions").isNotEmpty()).andExpect(jsonPath("$.result.warnings").isNotEmpty());
    }
    @Test void preservesExactRateInExportAndCanReproduceReport() throws Exception {
        String exact = input.replace("100000", "6000").replace("10000", "6000").replace("20000", "0")
            .replace("\"annualRatePercent\":12", "\"annualRatePercent\":\"0.69999999999999999\"");
        mvc.perform(post("/api/debt-scenarios/report").contentType("application/json").content(exact))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.input.annualRatePercent").value("0.69999999999999999"))
            .andExpect(jsonPath("$.result.baseline.interestMinor").value(3));
    }
    @Test void embedsVerifiedCashEvidenceAndRejectsMismatchedClaims() throws Exception {
        var snapshot = cash.save(new com.householdfinance.service.CashPositionService.Input(
            java.time.LocalDate.now(java.time.ZoneId.of("Europe/Dublin")), "Test statement", "REVIEWED",
            java.util.List.of(new com.householdfinance.service.CashPositionService.Line("Current", 50000)), java.util.List.of(), 40000));
        String linked = input.strip().replace("}", ",\"cashPositionSnapshotId\":\"" + snapshot.id() + "\"}");
        mvc.perform(post("/api/debt-scenarios/report").contentType("application/json").content(linked))
            .andExpect(status().isOk()).andExpect(jsonPath("$.cashEvidence.sourceNote").value("Test statement"))
            .andExpect(jsonPath("$.input.cashPositionSnapshotId").value(snapshot.id()));
        mvc.perform(post("/api/debt-scenarios/report").contentType("application/json").content(linked.replace("50000", "60000")))
            .andExpect(status().isBadRequest());
    }
    @Test void reportUsesSameStrictBoundaryAsComparison() throws Exception {
        for (var body : new String[] {"{}", input.replace("100000", "null"), input.replace("100000", "1.9")})
            mvc.perform(post("/api/debt-scenarios/report").contentType("application/json").content(body)).andExpect(status().isBadRequest());
    }
}
