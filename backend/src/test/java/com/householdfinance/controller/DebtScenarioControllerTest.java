package com.householdfinance.controller;

import com.householdfinance.service.DebtScenarioService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DebtScenarioControllerTest {
    @Autowired MockMvc mvc;

    private void rejected(String json) throws Exception {
        mvc.perform(post("/api/debt-scenarios/compare")
            .contentType("application/json").content(json))
            .andExpect(status().isBadRequest());
    }

    @Test void rejectsMissingFields() throws Exception {
        rejected("{\"balanceMinor\":100000,\"monthlyPaymentMinor\":10000}");
    }

    @Test void rejectsNullFields() throws Exception {
        rejected("{\"balanceMinor\":100000,\"annualRatePercent\":null,\"monthlyPaymentMinor\":10000,\"extraPaymentMinor\":0,\"liquidCashMinor\":0,\"minimumReserveMinor\":0}");
    }

    @Test void rejectsFractionalMinorUnits() throws Exception {
        rejected("{\"balanceMinor\":100000.9,\"annualRatePercent\":12,\"monthlyPaymentMinor\":10000,\"extraPaymentMinor\":-0.5,\"liquidCashMinor\":50000,\"minimumReserveMinor\":10000}");
    }
    @Test void preservesExactDecimalRateAtHttpBoundary() throws Exception {
        mvc.perform(post("/api/debt-scenarios/compare")
            .contentType("application/json")
            .content("{\"balanceMinor\":6000,\"annualRatePercent\":0.69999999999999999,\"monthlyPaymentMinor\":6000,\"extraPaymentMinor\":0,\"liquidCashMinor\":0,\"minimumReserveMinor\":0}"))
            .andExpect(status().isOk())
            .andExpect(MockMvcResultMatchers.jsonPath("$.baseline.interestMinor").value(3));
    }
}

