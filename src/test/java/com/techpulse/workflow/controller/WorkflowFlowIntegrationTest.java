package com.techpulse.workflow.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class WorkflowFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String login(String username, String password) throws Exception {
        String body = String.format("{\"username\":\"%s\",\"password\":\"%s\"}", username, password);
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("token").asText();
    }

    @Test
    void fullScenarioWorkflowFlow() throws Exception {
        String aliceToken = login("alice", "password123");
        String bobToken = login("bob", "password123");
        String carolToken = login("carol", "password123");
        String adminToken = login("admin", "password123");

        // 1. Create LEAVE request
        String createLeaveBody = "{\"type\":\"LEAVE\",\"title\":\"Trip\",\"description\":\"Vacation\"}";
        MvcResult createResult = mockMvc.perform(post("/requests")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createLeaveBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.currentStep.stepOrder").value(1))
                .andExpect(jsonPath("$.currentStep.role").value("MANAGER"))
                .andReturn();

        JsonNode createdNode = objectMapper.readTree(createResult.getResponse().getContentAsString());
        long leaveId = createdNode.get("id").asLong();

        // 2. Wrong roles get 403 (alice is requester/not approver, carol is HR/not MANAGER)
        mockMvc.perform(post("/requests/" + leaveId + "/approve")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/requests/" + leaveId + "/approve")
                        .header("Authorization", "Bearer " + carolToken))
                .andExpect(status().isForbidden());

        // 3. Bob (MANAGER) approves -> moves to step 2 (HR)
        mockMvc.perform(post("/requests/" + leaveId + "/approve")
                        .header("Authorization", "Bearer " + bobToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comments\":\"Manager approved\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.currentStep.stepOrder").value(2))
                .andExpect(jsonPath("$.currentStep.role").value("HR"));

        // 4. Carol (HR) approves -> APPROVED, currentStep null
        mockMvc.perform(post("/requests/" + leaveId + "/approve")
                        .header("Authorization", "Bearer " + carolToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.currentStep").doesNotExist());

        // 5. Bob approves again -> 409
        mockMvc.perform(post("/requests/" + leaveId + "/approve")
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isConflict());

        // 6. History has 3 entries (CREATED, APPROVED, APPROVED)
        MvcResult historyResult = mockMvc.perform(get("/requests/history/" + leaveId)
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode historyArray = objectMapper.readTree(historyResult.getResponse().getContentAsString());
        assertThat(historyArray.size()).isEqualTo(3);

        // 7. EXPENSE create + admin override reject
        String createExpenseBody = "{\"type\":\"EXPENSE\",\"title\":\"Taxi\",\"description\":\"Travel\"}";
        MvcResult expenseResult = mockMvc.perform(post("/requests")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createExpenseBody))
                .andExpect(status().isCreated())
                .andReturn();
        long expenseId = objectMapper.readTree(expenseResult.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/requests/" + expenseId + "/override")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approve\":false,\"comments\":\"Denied by admin\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.currentStep").doesNotExist());

        // 8. Unknown type -> 422
        mockMvc.perform(post("/requests")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"FOO\",\"title\":\"Title\"}"))
                .andExpect(status().isUnprocessableEntity());

        // 9. Validation error -> 400
        mockMvc.perform(post("/requests")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"\",\"title\":\"\"}"))
                .andExpect(status().isBadRequest());

        // 10. No token -> 401
        mockMvc.perform(get("/requests/" + leaveId))
                .andExpect(status().isUnauthorized());

        // 11. Bob reading history -> 403
        mockMvc.perform(get("/requests/history/" + leaveId)
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isForbidden());

        // 12. GET unknown id -> 404
        mockMvc.perform(get("/requests/999999")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isNotFound());

        // 13. Bad login -> 401
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"wrongpassword\"}"))
                .andExpect(status().isUnauthorized());
    }
}
