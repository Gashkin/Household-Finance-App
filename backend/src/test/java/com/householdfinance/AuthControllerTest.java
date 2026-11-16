package com.householdfinance;

import com.householdfinance.controller.AuthController;
import com.householdfinance.service.AuthContextService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc(addFilters=false)
@WebMvcTest(AuthController.class)
@org.springframework.boot.test.mock.mockito.MockBean(com.householdfinance.access.AccessService.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthContextService authContextService;

    @Test
    void authEndpointReturnsCurrentRole() throws Exception {
        given(authContextService.currentRole()).willReturn("planner");

        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isOk())
                .andExpect(content().string("planner"));
    }
}
