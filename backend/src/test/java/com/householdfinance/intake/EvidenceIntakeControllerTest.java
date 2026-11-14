package com.householdfinance.intake;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.householdfinance.repository.CashPositionSnapshotRepository;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"evidence.intake.access-key=synthetic-test-access-0000","evidence.ai.enabled=false"}) @AutoConfigureMockMvc(addFilters=false)
class EvidenceIntakeControllerTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper mapper; @Autowired CashPositionSnapshotRepository repository;
    @Autowired org.springframework.web.context.WebApplicationContext context;
    @Autowired IntakeAccessFilter intake;
    @org.junit.jupiter.api.BeforeEach void retainIntakeBoundary() {
        mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context).addFilters(intake).build();
    }
    final String key = "synthetic-test-access-0000";
    @Test void unauthorizedDraftsAreBlockedAndCapabilitiesDoNotRevealKey() throws Exception {
        mvc.perform(post("/api/evidence-intake/drafts").contentType("application/json").content("{}"))
            .andExpect(status().isUnauthorized()).andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get("/api/evidence-intake/session").header("X-Intake-Key", "wrong")).andExpect(status().isUnauthorized());
        var json = mvc.perform(get("/api/evidence-intake/capabilities")).andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(true)).andReturn().getResponse().getContentAsString();
        assertFalse(json.contains(key));
    }
    @Test void draftReviewApprovalCreatesOneLinkedSnapshotWithoutRawSourcePersistence() throws Exception {
        long before = repository.count();
        String source = "Account: EUR 1,000.00\nBill due: EUR 100.00\nPRIVATE SOURCE TOKEN";
        String body = mapper.writeValueAsString(java.util.Map.of("source",source,"reference","Demo statement","allowModel",false));
        String id = mapper.readTree(mvc.perform(post("/api/evidence-intake/drafts").header("X-Intake-Key",key).contentType("application/json").content(body))
            .andExpect(status().isOk()).andExpect(jsonPath("$.proposals[0].lineNumber").value(1)).andReturn().getResponse().getContentAsString()).get("id").asText();
        assertEquals(before, repository.count());
        String approval = "{\"asOfDate\":\""+LocalDate.now(ZoneId.of("Europe/Dublin"))+"\",\"minimumReserveEuro\":\"400\",\"acknowledged\":true,\"rows\":[{\"lineNumber\":1,\"kind\":\"CASH\",\"label\":\"Reviewed account\",\"amountEuro\":\"1000\"},{\"lineNumber\":2,\"kind\":\"COMMITMENT\",\"label\":\"Bill\",\"amountEuro\":\"100\"}]}";
        String saved = mvc.perform(post("/api/evidence-intake/drafts/"+id+"/approve").header("X-Intake-Key",key).contentType("application/json").content(approval))
            .andExpect(status().isOk()).andExpect(jsonPath("$.availableAfterCommitmentsMinor").value(90000)).andReturn().getResponse().getContentAsString();
        String retry = mvc.perform(post("/api/evidence-intake/drafts/"+id+"/approve").header("X-Intake-Key",key).contentType("application/json").content(approval)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(mapper.readTree(saved).get("id"), mapper.readTree(retry).get("id")); assertEquals(before+1, repository.count());
        assertFalse(saved.contains("PRIVATE SOURCE TOKEN")); assertFalse(saved.contains("EUR 1,000"));
        String snapshot = mapper.readTree(saved).get("id").asText();
        String compare = "{\"balanceMinor\":100000,\"annualRatePercent\":\"12\",\"monthlyPaymentMinor\":10000,\"extraPaymentMinor\":20000,\"liquidCashMinor\":90000,\"minimumReserveMinor\":40000,\"cashPositionSnapshotId\":\""+snapshot+"\"}";
        mvc.perform(post("/api/debt-scenarios/report").contentType("application/json").content(compare)).andExpect(status().isOk()).andExpect(jsonPath("$.cashEvidence.sourceNote").value(org.hamcrest.Matchers.containsString("Demo statement")));
    }
    @Test void malformedApprovalCannotBypassReviewAndDiscardDeletesDraft() throws Exception {
        String body = "{\"source\":\"Account EUR 10\",\"reference\":\"Demo\",\"allowModel\":false}";
        String id = mapper.readTree(mvc.perform(post("/api/evidence-intake/drafts").header("X-Intake-Key",key).contentType("application/json").content(body)).andReturn().getResponse().getContentAsString()).get("id").asText();
        mvc.perform(post("/api/evidence-intake/drafts/"+id+"/approve").header("X-Intake-Key",key).contentType("application/json").content("{\"rows\":[null]}" )).andExpect(status().isBadRequest());
        mvc.perform(delete("/api/evidence-intake/drafts/"+id).header("X-Intake-Key",key)).andExpect(status().isNoContent());
        mvc.perform(post("/api/evidence-intake/drafts").header("X-Intake-Key",key).contentType("application/json").content("{\"source\":null}" )).andExpect(status().isBadRequest());
    }
}
