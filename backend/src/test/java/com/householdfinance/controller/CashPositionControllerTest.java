package com.householdfinance.controller;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest @AutoConfigureMockMvc
class CashPositionControllerTest {
    @Autowired MockMvc mvc;
    final String valid = """
        {"asOf":"2026-11-14","through":"2026-11-20","balances":[{"name":"Current","balanceMinor":100000,"asOf":"2026-11-14","source":"Bank app"}],"commitments":[{"name":"Bill","amountMinor":30000,"dueOn":"2026-11-20"}]}
        """;
    @Test void calculatesUsingRealService() throws Exception {
        mvc.perform(post("/api/cash-position/calculate").contentType("application/json").content(valid))
            .andExpect(status().isOk()).andExpect(jsonPath("$.availableCashMinor").value(70000));
    }
    @Test void rejectsFractionalMissingNullAndMalformedData() throws Exception {
        for (var json : new String[] {valid.replace("100000", "1.5"), valid.replace("100000", "null"), valid.replace("100000", "\"100000\""), valid.replace("2026-11-20", "bad-date"), "{}", "null", valid.replace("\"commitments\":[{\"name\":\"Bill\",\"amountMinor\":30000,\"dueOn\":\"2026-11-20\"}]", "\"commitments\":[null]"), valid.replace("30000", "-1")})
            mvc.perform(post("/api/cash-position/calculate").contentType("application/json").content(json)).andExpect(status().isBadRequest());
    }
}
