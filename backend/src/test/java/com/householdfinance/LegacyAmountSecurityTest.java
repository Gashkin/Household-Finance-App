package com.householdfinance;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import com.householdfinance.repository.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:legacy-amounts;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc(addFilters=false)
@org.junit.jupiter.api.extension.ExtendWith(com.householdfinance.access.OwnerFixture.class)
class LegacyAmountSecurityTest {
    @Autowired MockMvc http;
    @Autowired AccountRepository accounts;
    @Autowired MonthlyCommitmentRepository commitments;

    @Test void missingNullFractionalAndUnrepresentableAmountsNeverPersistAsZeroOrRoundedMoney() throws Exception {
        for(String fragment:new String[]{"",",\"balance\":null",",\"balance\":1.005",",\"balance\":90071992547409.91",",\"balance\":1e100"})
            http.perform(post("/planner/accounts").contentType("application/json")
                .content("{\"id\":\"invalid\",\"name\":\"Synthetic cash\",\"type\":\"Bank\""+fragment+"}"))
                .andExpect(status().isBadRequest());
        for(String value:new String[]{"null","-1","0.001","\"NaN\"","90071992547409.91"})
            http.perform(post("/planner/commitments").contentType("application/json")
                .content("{\"id\":\"invalid\",\"title\":\"Synthetic rent\",\"dueDate\":\"Recurring\",\"amount\":"+value+"}"))
                .andExpect(status().isBadRequest());
        assertEquals(0,accounts.count());assertEquals(0,commitments.count());
    }

    @Test void explicitZeroSignedBalancesAndCentAmountsRemainSupported() throws Exception {
        http.perform(post("/planner/accounts").contentType("application/json")
            .content("{\"id\":\"valid\",\"name\":\"Synthetic cash\",\"type\":\"Bank\",\"balance\":\"-10.01\"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.balance").value(-10.01));
        http.perform(post("/planner/commitments").contentType("application/json")
            .content("{\"id\":\"valid\",\"title\":\"Synthetic zero\",\"dueDate\":\"Recurring\",\"amount\":\"0.00\"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.amount").value(0));
        accounts.deleteAll();commitments.deleteAll();
    }
}
