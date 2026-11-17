package com.householdfinance;

import com.householdfinance.entity.*;
import com.householdfinance.repository.*;
import jakarta.servlet.http.Cookie;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={
    "workspace.password=synthetic-consumer-workspace-password",
    "spring.datasource.url=jdbc:h2:mem:consumer-evidence;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
class MonthlySummaryControllerTest {
    @Autowired MockMvc mvc;
    @Autowired AccountRepository accounts;
    @Autowired TransactionRepository transactions;

    Cookie unlock() throws Exception {
        var response=mvc.perform(post("/api/workspace/unlock").header("X-Workspace-Request","1")
            .contentType("application/json").content("{\"password\":\"synthetic-consumer-workspace-password\"}"))
            .andExpect(status().isNoContent()).andReturn().getResponse();
        return new Cookie("workspace_session",response.getHeader("Set-Cookie").split(";",2)[0].split("=",2)[1]);
    }

    @Test void neitherEmptyNorPopulatedLegacyRecordsManufactureActualsOrSpendability() throws Exception {
        Cookie session=unlock();
        assertUnknown(session);
        accounts.save(new AccountEntity("synthetic-summary","Synthetic cash","Current Account",10000));
        transactions.save(new TransactionEntity("synthetic-actual",LocalDate.now(),"Synthetic legacy activity",
            500,"Synthetic",TransactionType.ACTUAL,null));
        assertUnknown(session);
    }

    void assertUnknown(Cookie session) throws Exception {
        mvc.perform(get("/reports/monthly-overview").cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.month").value(YearMonth.now(ZoneId.of("Europe/Dublin")).toString()))
            .andExpect(jsonPath("$.status").value("UNAVAILABLE"))
            .andExpect(jsonPath("$.actualInflow").value(nullValue()))
            .andExpect(jsonPath("$.actualOutflow").value(nullValue()))
            .andExpect(jsonPath("$.startingBalance").value(nullValue()))
            .andExpect(jsonPath("$.committedOutgoings").value(nullValue()))
            .andExpect(jsonPath("$.projectedEndBalance").value(nullValue()))
            .andExpect(jsonPath("$.allowableSpend").value(nullValue()))
            .andExpect(jsonPath("$.recentTransactions",hasSize(0)));
    }
}
