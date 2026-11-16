package com.householdfinance;

import com.householdfinance.controller.PlannerController;
import com.householdfinance.service.PlannerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Objects;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc(addFilters=false)
@WebMvcTest(PlannerController.class)
class PlannerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PlannerService plannerService;

    @Test
    void plannerEndpointReturnsSampleData() throws Exception {
        var sampleData = new PlannerService.PlannerData(
                List.of(new PlannerService.Account("A1", "Checking", "Bank", 4120.75)),
                List.of(new PlannerService.Category("C1", "Housing", "$1,200")),
                List.of(new PlannerService.Commitment("M1", "Rent", 1200.0, "2026-07-01", "Housing", null))
        );

        given(plannerService.getPlannerData()).willReturn(sampleData);

        mockMvc.perform(get("/planner"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {
                          "accounts":[{"id":"A1","name":"Checking","type":"Bank","balance":4120.75}],
                          "categories":[{"id":"C1","name":"Housing","budget":"$1,200"}],
                                                                                                        "commitments":[{"id":"M1","title":"Rent","amount":1200.0,"dueDate":"2026-07-01","category":"Housing"}]
                        }
                        """));
    }

                @Test
                void createAccountReturnsCreatedAccount() throws Exception {
                                var request = new PlannerService.AccountUpsertRequest("A4", "Cash", "Cash", new java.math.BigDecimal("150.0"));
                                var result = new PlannerService.Account("A4", "Cash", "Cash", 150.0);

                                given(plannerService.createAccount(request)).willReturn(result);

                                mockMvc.perform(post("/planner/accounts")
                                                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                                                                                                .content("""
                                                                                                                                {
                                                                                                                                        "id":"A4",
                                                                                                                                        "name":"Cash",
                                                                                                                                        "type":"Cash",
                                                                                                                                        "balance":150.0
                                                                                                                                }
                                                                                                                                """))
                                                                .andExpect(status().isCreated())
                                                                .andExpect(content().json("""
                                                                                                {
                                                                                                        "id":"A4",
                                                                                                        "name":"Cash",
                                                                                                        "type":"Cash",
                                                                                                        "balance":150.0
                                                                                                }
                                                                                                """));
                }

                @Test
                void updateCategoryReturnsUpdatedCategory() throws Exception {
                                var request = new PlannerService.CategoryUpsertRequest("ignored", "Food", "$500");
                                var result = new PlannerService.Category("C2", "Food", "$500");

                                given(plannerService.updateCategory("C2", request)).willReturn(result);

                                mockMvc.perform(put("/planner/categories/C2")
                                                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                                                                                                .content("""
                                                                                                                                {
                                                                                                                                        "id":"ignored",
                                                                                                                                        "name":"Food",
                                                                                                                                        "budget":"$500"
                                                                                                                                }
                                                                                                                                """))
                                                                .andExpect(status().isOk())
                                                                .andExpect(content().json("""
                                                                                                {
                                                                                                        "id":"C2",
                                                                                                        "name":"Food",
                                                                                                        "budget":"$500"
                                                                                                }
                                                                                                """));
                }

                @Test
                void deleteCommitmentReturnsNoContent() throws Exception {
                                willDoNothing().given(plannerService).deleteCommitment("M2");

                                mockMvc.perform(delete("/planner/commitments/M2"))
                                                                .andExpect(status().isNoContent());
                }

                @Test
                void invalidPlannerRequestReturnsBadRequest() throws Exception {
                                given(plannerService.createCategory(new PlannerService.CategoryUpsertRequest("", "", "")))
                                                                .willThrow(new IllegalArgumentException("Category id is required."));

                                mockMvc.perform(post("/planner/categories")
                                                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                                                                                                .content("""
                                                                                                                                {
                                                                                                                                        "id":"",
                                                                                                                                        "name":"",
                                                                                                                                        "budget":""
                                                                                                                                }
                                                                                                                                """))
                                                                .andExpect(status().isBadRequest())
                                                                .andExpect(content().string("Category id is required."));
                }
}
