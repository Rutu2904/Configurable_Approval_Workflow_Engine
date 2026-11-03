package com.techpulse.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WorkflowFlowIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    // Tokens stored across tests
    private static String aliceToken;
    private static String bobToken;
    private static String carolToken;
    private static String adminToken;

    // Helper to login and get token
    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("token").asText();
    }

    @Test
    @Order(1)
    void loginAllUsers() throws Exception {
        aliceToken = login("alice");
        bobToken = login("bob");
        carolToken = login("carol");
        adminToken = login("admin");

        Assertions.assertNotNull(aliceToken);
        Assertions.assertNotNull(bobToken);
        Assertions.assertNotNull(carolToken);
        Assertions.assertNotNull(adminToken);
    }

    @Test
    @Order(2)
    void createLeaveRequest() throws Exception {
        // Alice creates a LEAVE request
        mockMvc.perform(post("/requests")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"LEAVE\",\"title\":\"Vacation\",\"description\":\"Trip\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.currentStep.stepOrder").value(1));
    }

    @Test
    @Order(3)
    void carolCannotApproveStep1() throws Exception {
        // Carol has HR role, step 1 needs MANAGER -> 403
        mockMvc.perform(post("/requests/1/approve")
                        .header("Authorization", "Bearer " + carolToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comments\":\"trying\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(4)
    void aliceCannotApproveOwnRequest() throws Exception {
        // Alice is the creator -> 403
        mockMvc.perform(post("/requests/1/approve")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comments\":\"self approve\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(5)
    void bobApprovesStep1() throws Exception {
        // Bob has MANAGER role -> approve step 1, moves to step 2
        mockMvc.perform(post("/requests/1/approve")
                        .header("Authorization", "Bearer " + bobToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comments\":\"looks good\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.currentStep.stepOrder").value(2));
    }

    @Test
    @Order(6)
    void carolApprovesStep2() throws Exception {
        // Carol has HR role -> final approval
        mockMvc.perform(post("/requests/1/approve")
                        .header("Authorization", "Bearer " + carolToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comments\":\"approved by HR\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.currentStep").isEmpty());
    }

    @Test
    @Order(7)
    void approveAgain409() throws Exception {
        // Already approved -> 409
        mockMvc.perform(post("/requests/1/approve")
                        .header("Authorization", "Bearer " + bobToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comments\":\"again\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @Order(8)
    void historyHas3Entries() throws Exception {
        // CREATED + APPROVED + APPROVED = 3 entries
        mockMvc.perform(get("/requests/history/1")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    @Order(9)
    void expenseAndAdminOverride() throws Exception {
        // Alice creates EXPENSE request
        mockMvc.perform(post("/requests")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"EXPENSE\",\"title\":\"Office supplies\",\"description\":\"Pens\"}"))
                .andExpect(status().isCreated());

        // Admin overrides with rejection
        mockMvc.perform(post("/requests/2/override")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approve\":false,\"comments\":\"budget freeze\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    @Order(10)
    void unknownType422() throws Exception {
        mockMvc.perform(post("/requests")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"FOO\",\"title\":\"Bad\",\"description\":\"x\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @Order(11)
    void missingTitle400() throws Exception {
        mockMvc.perform(post("/requests")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"LEAVE\",\"title\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(12)
    void noToken401() throws Exception {
        mockMvc.perform(get("/requests/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(13)
    void bobCannotReadAliceHistory() throws Exception {
        // Bob is not admin and not the creator -> 403
        mockMvc.perform(get("/requests/history/1")
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(14)
    void unknownId404() throws Exception {
        mockMvc.perform(get("/requests/999")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(15)
    void badLogin401() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(16)
    void swaggerDocs200() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());
    }
}

