package com.householdfinance.controller;

import com.householdfinance.repository.CashPositionSnapshotRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:cash-position-tests;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class CashPositionControllerTest {
    @Autowired private MockMvc mvc;
    @Autowired private CashPositionSnapshotRepository repository;

    @BeforeEach void clear() { repository.deleteAll(); }

    private String payload(String date, String balance, String commitment, String verification) {
        return "{\"asOfDate\":\"" + date
                + "\",\"sourceNote\":\"Account statements checked manually\","
                + "\"verification\":\"" + verification + "\","
                + "\"cashBalances\":[{\"label\":\"Current account\",\"amountMinor\":" + balance + "},"
                + "{\"label\":\"Savings\",\"amountMinor\":200000}],"
                + "\"upcomingCommitments\":[{\"label\":\"Rent\",\"amountMinor\":" + commitment + "}],"
                + "\"minimumReserveMinor\":150000}";
    }

    private String today() { return LocalDate.now(ZoneId.of("Europe/Dublin")).toString(); }

    private void rejected(String json) throws Exception {
        mvc.perform(post("/api/cash-position/snapshots")
                .contentType("application/json").content(json))
                .andExpect(status().isBadRequest());
    }

    @Test void returnsNoContentBeforeAnyEvidenceExists() throws Exception {
        mvc.perform(get("/api/cash-position/latest")).andExpect(status().isNoContent());
    }

    @Test void savesExactDerivedPositionAndCanReadItBack() throws Exception {
        mvc.perform(post("/api/cash-position/snapshots")
                .contentType("application/json").content(payload(today(), "500000", "120000", "REVIEWED")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.liquidCashMinor").value(700000))
                .andExpect(jsonPath("$.committedMinor").value(120000))
                .andExpect(jsonPath("$.availableAfterCommitmentsMinor").value(580000))
                .andExpect(jsonPath("$.discretionaryAfterReserveMinor").value(430000))
                .andExpect(jsonPath("$.verification").value("REVIEWED"))
                .andExpect(jsonPath("$.cashBalances[0].label").value("Current account"))
                .andExpect(jsonPath("$.sourceNote").value("Account statements checked manually"));
        mvc.perform(get("/api/cash-position/latest")).andExpect(status().isOk())
                .andExpect(jsonPath("$.availableAfterCommitmentsMinor").value(580000))
                .andExpect(jsonPath("$.upcomingCommitments[0].label").value("Rent"));
    }

    @Test void flagsStaleEstimatesAndCashShortfallWithoutHidingNegativeLiquidity() throws Exception {
        String old = LocalDate.now(ZoneId.of("Europe/Dublin")).minusDays(40).toString();
        mvc.perform(post("/api/cash-position/snapshots")
                .contentType("application/json").content(payload(old, "10000", "350000", "ESTIMATED")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stale").value(true))
                .andExpect(jsonPath("$.availableAfterCommitmentsMinor").value(-140000))
                .andExpect(jsonPath("$.discretionaryAfterReserveMinor").value(0))
                .andExpect(jsonPath("$.warnings[0]", containsString("30 days")));
    }

    @Test void rejectsInvalidEvidenceAndDoesNotPersistIt() throws Exception {
        rejected(payload(today(), "10000.9", "1000", "REVIEWED"));
        rejected(payload(today(), "-1", "1000", "REVIEWED"));
        rejected(payload(today(), "10000", "0.5", "REVIEWED"));
        rejected(payload(today(), "10000", "1000", "UNVERIFIED"));
        rejected(payload("2025-02-30", "10000", "1000", "REVIEWED"));
        rejected(payload(LocalDate.now(ZoneId.of("Europe/Dublin")).plusDays(1).toString(), "10000", "1000", "REVIEWED"));
        rejected("{\"asOfDate\":\"" + today() + "\",\"verification\":\"REVIEWED\",\"cashBalances\":[]}");
        rejected(payload(today(), "9007199254740992", "1000", "REVIEWED"));
        mvc.perform(get("/api/cash-position/latest")).andExpect(status().isNoContent());
    }
}
