package com.example.copilot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "copilot.auth.mode=mock",
        "copilot.auth.bootstrap-admin-id=admin-001",
        "copilot.persistence=memory"
})
@AutoConfigureMockMvc
class PlatformSecurityIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void firstLoginCreatesDefaultCustomerServiceAccount() throws Exception {
        String token = login("agent-001", "客服甲");

        mvc.perform(get("/api/v1/me").header("X-Session-Token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.name").value("客服甲"))
                .andExpect(jsonPath("$.roles[0]").value("customer_service"));

        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/admin/menu-order").header("X-Session-Token", token))
                .andExpect(status().isForbidden());
    }

    @Test
    void roleChangesApplyImmediatelyAndDisabledAccountInvalidatesSessions() throws Exception {
        String adminToken = login("admin-001", "管理员");
        String agentToken = login("agent-002", "客服乙");

        mvc.perform(put("/api/v1/admin/accounts/agent-002/roles")
                        .header("X-Session-Token", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"knowledge_admin\"]}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/admin/menu-order").header("X-Session-Token", agentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(4))
                .andExpect(jsonPath("$.items[0].moduleCode").value("overview"))
                .andExpect(jsonPath("$.items[1].moduleCode").value("feedback"))
                .andExpect(jsonPath("$.items[2].moduleCode").value("knowledge"))
                .andExpect(jsonPath("$.items[3].moduleCode").value("taxonomy"));

        mvc.perform(get("/api/v1/admin/audit").header("X-Session-Token", agentToken))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/admin/accounts/agent-002/disable")
                        .header("X-Session-Token", adminToken))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/me").header("X-Session-Token", agentToken))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/mock-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"employeeId\":\"agent-002\",\"name\":\"客服乙\"}"))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/admin/audit").header("X-Session-Token", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].action").value("LOGIN_DENIED"))
                .andExpect(jsonPath("$.items[0].result").value("failure"));
    }

    @Test
    void menuOrderMustContainAllTenItemsAndSecurityActionsAreAudited() throws Exception {
        String adminToken = login("admin-001", "管理员");
        String completeOrder = """
                {"moduleCodes":["settings","audit","accounts","taxonomy","schedules",
                "announcements","knowledge","feedback","status_requests","overview"]}
                """;

        mvc.perform(put("/api/v1/admin/menu-order")
                        .header("X-Session-Token", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(completeOrder))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(10))
                .andExpect(jsonPath("$.items[0].moduleCode").value("settings"));

        mvc.perform(put("/api/v1/admin/menu-order")
                        .header("X-Session-Token", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"moduleCodes\":[\"overview\"]}"))
                .andExpect(status().isUnprocessableEntity());

        String auditJson = mvc.perform(get("/api/v1/admin/audit")
                        .header("X-Session-Token", adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode actions = objectMapper.readTree(auditJson).path("items");
        assertThat(actions.toString()).contains("LOGIN", "ACCOUNT_CREATED", "MENU_ORDER_UPDATED");
    }

    @Test
    void protectedEndpointsRequireSessionWhileQueryRemainsPublic() throws Exception {
        mvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/menu-order")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"锁\",\"limit\":4,\"offset\":0}"))
                .andExpect(status().isOk());
    }

    @Test
    void backendEnforcesModulePermissionsBeyondMenuVisibility() throws Exception {
        String adminToken = login("admin-001", "管理员");
        String agentToken = login("rbac-agent", "普通客服");
        mvc.perform(get("/api/v1/admin/knowledge").header("X-Session-Token", agentToken))
                .andExpect(status().isForbidden());

        mvc.perform(put("/api/v1/admin/accounts/rbac-agent/roles")
                        .header("X-Session-Token", adminToken).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"knowledge_admin\"]}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/knowledge").header("X-Session-Token", agentToken))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/announcements").header("X-Session-Token", agentToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/accounts").header("X-Session-Token", agentToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminBrowserRouteIsServedBySpringBoot() throws Exception {
        mvc.perform(get("/admin"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }

    @Test
    void invalidRoleAndUnknownAccountReturnBusinessErrors() throws Exception {
        String adminToken = login("admin-001", "管理员");

        mvc.perform(put("/api/v1/admin/accounts/missing-user/roles")
                        .header("X-Session-Token", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"knowledge_admin\"]}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("account_not_found"));

        mvc.perform(put("/api/v1/admin/accounts/admin-001/roles")
                        .header("X-Session-Token", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"unknown_role\"]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("invalid_role"));
    }

    private String login(String employeeId, String name) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/mock-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(employeeId, name))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("sessionToken").asText();
    }

    private record LoginRequest(String employeeId, String name) {}
}
