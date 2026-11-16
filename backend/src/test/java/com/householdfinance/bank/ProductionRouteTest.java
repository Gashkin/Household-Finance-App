package com.householdfinance.bank;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.householdfinance.HouseholdFinanceApplication;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
@SpringBootTest(classes={HouseholdFinanceApplication.class,ProductionBrowserApplication.Fixture.class},properties={"workspace.password=synthetic-route-password", "spring.datasource.url=jdbc:h2:mem:production-routes;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
@EnabledOnOs(OS.LINUX)
class ProductionRouteTest {
  @Autowired MockMvc mvc;
  @Autowired SyntheticProvider provider;
  @Test void staleSandboxClientsCannotOperateProductionEvenWithValidWorkspaceConsent() throws Exception {
    mvc.perform(get("/api/banking/status")).andExpect(status().isUnauthorized());
    var login=mvc.perform(post("/api/workspace/unlock").header("X-Workspace-Request","1").contentType("application/json").content("{\"password\":\"synthetic-route-password\"}")).andExpect(status().isNoContent()).andReturn().getResponse();
    var cookie=new Cookie("workspace_session",login.getHeader("Set-Cookie").split(";",2)[0].split("=",2)[1]);
    mvc.perform(get("/api/banking/status").cookie(cookie)).andExpect(status().isOk()).andExpect(jsonPath("$.environment").value("PRODUCTION"));
    mvc.perform(get("/api/bank-sandbox/status").cookie(cookie)).andExpect(status().isBadRequest());
    mvc.perform(post("/api/bank-sandbox/start").cookie(cookie).header("X-Workspace-Request","1").contentType("application/json").content("{\"name\":\"AIB\",\"country\":\"IE\",\"acknowledged\":true}")).andExpect(status().isBadRequest());
    assertEquals("",provider.authorization);
    provider.mode="malformed-session";
    mvc.perform(post("/api/banking/start").cookie(cookie).header("X-Workspace-Request","1").contentType("application/json").content("{\"name\":\"AIB\",\"country\":\"IE\",\"acknowledged\":true}")).andExpect(status().isOk());
    mvc.perform(post("/api/banking/complete").cookie(cookie).header("X-Workspace-Request","1").contentType("application/json").content("{\"state\":\""+provider.state+"\",\"code\":\"synthetic-code\"}")).andExpect(status().isBadRequest());
    mvc.perform(post("/api/banking/forget").cookie(cookie).header("X-Workspace-Request","1").contentType("application/json").content("{\"acknowledged\":true}")).andExpect(status().isBadRequest());
    mvc.perform(post("/api/banking/external-revocation").cookie(cookie).header("X-Workspace-Request","1").contentType("application/json").content("{\"acknowledged\":false}")).andExpect(status().isBadRequest());
    mvc.perform(post("/api/banking/external-revocation").cookie(cookie).header("X-Workspace-Request","1").contentType("application/json").content("{\"acknowledged\":true}")).andExpect(status().isOk()).andExpect(jsonPath("$.state").value("DISCONNECTED"));
  }
}
