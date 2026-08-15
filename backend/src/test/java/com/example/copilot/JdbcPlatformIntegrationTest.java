package com.example.copilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import com.example.copilot.operations.StatusOvertimeWorker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

@SpringBootTest(properties = {
        "copilot.auth.mode=mock",
        "copilot.auth.bootstrap-admin-id=jdbc-admin-test",
        "copilot.persistence=jdbc"
})
@AutoConfigureMockMvc
@Transactional
@DirtiesContext
@EnabledIfEnvironmentVariable(named = "RUN_JDBC_TESTS", matches = "true")
class JdbcPlatformIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcClient jdbc;
    @Autowired StatusOvertimeWorker overtimeWorker;

    @Test
    void identitySessionMenuAndAuditUsePostgres() throws Exception {
        String token = login("jdbc-admin-test", "数据库管理员");

        mvc.perform(get("/api/v1/admin/menu-order").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(10));

        assertThat(jdbc.sql("SELECT status FROM users WHERE dingtalk_user_id = 'jdbc-admin-test'")
                .query(String.class).single()).isEqualTo("active");
        assertThat(jdbc.sql("SELECT r.code FROM roles r JOIN user_roles ur ON ur.role_id = r.id JOIN users u ON u.id = ur.user_id WHERE u.dingtalk_user_id = 'jdbc-admin-test'")
                .query(String.class).single()).isEqualTo("system_admin");
        assertThat(jdbc.sql("SELECT login_method FROM user_sessions s JOIN users u ON u.id = s.user_id WHERE u.dingtalk_user_id = 'jdbc-admin-test'")
                .query(String.class).single()).isEqualTo("mock");
        assertThat(jdbc.sql("SELECT status_code FROM employee_current_statuses c JOIN users u ON u.id=c.user_id WHERE u.dingtalk_user_id='jdbc-admin-test'")
                .query(String.class).single()).isEqualTo("working");
        assertThat(jdbc.sql("SELECT count(*) FROM audit_logs a JOIN users u ON u.id = a.actor_user_id WHERE u.dingtalk_user_id = 'jdbc-admin-test'")
                .query(Long.class).single()).isGreaterThanOrEqualTo(2L);
    }

    @Test
    void queryMetricsAndFeedbackUsePostgresContracts() throws Exception {
        String session = "jdbc-query-session-" + System.nanoTime();
        String query = "阶段二查询-" + System.nanoTime();
        String body = objectMapper.writeValueAsString(new QueryRequest(query, 4, 0, "query"));
        mvc.perform(post("/api/v1/query").header("X-Anonymous-Session", session)
                        .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/query").header("X-Anonymous-Session", session)
                        .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());

        String feedback = objectMapper.writeValueAsString(new FeedbackRequest(query, "no_match", "需要补充", false));
        mvc.perform(post("/api/v1/feedback").header("X-Anonymous-Session", session)
                        .contentType(MediaType.APPLICATION_JSON).content(feedback))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("accepted"));
        mvc.perform(post("/api/v1/feedback").header("X-Anonymous-Session", session)
                        .contentType(MediaType.APPLICATION_JSON).content(feedback))
                .andExpect(status().isOk()).andExpect(jsonPath("$.confirmationRequired").value(true));

        assertThat(jdbc.sql("SELECT count(*) FROM query_events WHERE normalized_query=:query")
                .param("query", query.toLowerCase()).query(Long.class).single()).isEqualTo(2L);
        assertThat(jdbc.sql("SELECT count(*) FROM query_events WHERE normalized_query=:query AND is_repeat_within_10s")
                .param("query", query.toLowerCase()).query(Long.class).single()).isEqualTo(1L);
        assertThat(jdbc.sql("SELECT count(*) FROM feedback_reports WHERE query_text=:query")
                .param("query", query).query(Long.class).single()).isEqualTo(1L);
    }

    @Test
    void knowledgeVersionRollbackAndImportPreflightAreTransactional() throws Exception {
        String token = login("jdbc-admin-test", "数据库管理员");
        String question = "阶段三知识-" + System.nanoTime();
        String createBody = objectMapper.writeValueAsString(new KnowledgeRequest(
                "客服中心知识库/测试/阶段三", question, java.util.List.of(question + "怎么处理"),
                java.util.List.of("阶段三"), "第一版原始答案", "首次发布"));
        String created = mvc.perform(post("/api/v1/admin/knowledge").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(createBody))
                .andExpect(status().isOk()).andExpect(jsonPath("$.knowledge.knowledge.currentVersion").value(1))
                .andReturn().getResponse().getContentAsString();
        String sourceKey = objectMapper.readTree(created).path("knowledge").path("knowledge").path("sourceKey").asText();

        String updateBody = objectMapper.writeValueAsString(new KnowledgeRequest(
                "客服中心知识库/测试/阶段三", question, java.util.List.of(), java.util.List.of("阶段三"),
                "第二版原始答案", "更新答案"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/admin/knowledge/{key}", sourceKey)
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(updateBody))
                .andExpect(status().isOk()).andExpect(jsonPath("$.knowledge.knowledge.currentVersion").value(2));
        mvc.perform(post("/api/v1/admin/knowledge/{key}/rollback", sourceKey).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":1,\"reason\":\"验收回滚\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.knowledge.knowledge.currentVersion").value(3));
        mvc.perform(get("/api/v1/admin/knowledge/{key}/diff", sourceKey).header("Authorization", "Bearer " + token)
                        .param("from", "1").param("to", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.before.originalAnswer").value("第一版原始答案"))
                .andExpect(jsonPath("$.after.originalAnswer").value("第二版原始答案"));
        assertThat(jdbc.sql("SELECT count(*) FROM knowledge_versions v JOIN knowledge_entries e ON e.id=v.knowledge_entry_id WHERE e.source_key=:key")
                .param("key", sourceKey).query(Long.class).single()).isEqualTo(3L);

        String scheduled = "{\"category\":\"客服中心知识库/测试/阶段三\",\"standardQuestion\":\"" + question
                + "\",\"userQuestions\":[],\"keywords\":[],\"originalAnswer\":\"未来答案\",\"reason\":\"预约发布\",\"validFrom\":\"2099-01-01T00:00:00Z\"}";
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/admin/knowledge/{key}", sourceKey)
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(scheduled))
                .andExpect(status().isOk()).andExpect(jsonPath("$.knowledge.knowledge.currentVersion").value(3));
        assertThat(jdbc.sql("SELECT count(*) FROM knowledge_publication_schedule s JOIN knowledge_versions v ON v.id=s.knowledge_version_id JOIN knowledge_entries e ON e.id=v.knowledge_entry_id WHERE e.source_key=:key AND s.publication_status='scheduled'")
                .param("key", sourceKey).query(Long.class).single()).isEqualTo(1L);

        String duplicateImport = "[" + createBody + "," + createBody + "]";
        mvc.perform(post("/api/v1/admin/knowledge-transfer/import").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(duplicateImport))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error").value("file_duplicate_unique_keys"));
        mvc.perform(get("/api/v1/admin/knowledge-transfer/export").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.startsWith("﻿source_key,standard_question,category")));
        assertThat(jdbc.sql("SELECT count(*) FROM audit_logs WHERE module_code='knowledge' AND action='export' AND target_id='current'")
                .query(Long.class).single()).isEqualTo(1L);
    }

    @Test
    void operationsQueuesCapacityHistoryAndOvertimeFollowContracts() throws Exception {
        String admin = login("jdbc-admin-test", "数据库管理员");
        String first = login("phase4-first", "客服甲");
        String second = login("phase4-second", "客服乙");
        String third = login("phase4-third", "客服丙");
        String fourth = login("phase4-fourth", "客服丁");
        java.time.LocalDate monday=java.time.LocalDate.now().with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        String scheduleResponse=mvc.perform(post("/api/v1/admin/schedules").header("Authorization","Bearer "+admin).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("weekStart",monday.toString()))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long scheduleId=objectMapper.readTree(scheduleResponse).path("id").asLong();
        String firstAssignment=mvc.perform(post("/api/v1/admin/schedules/{id}/assignments",scheduleId).header("Authorization","Bearer "+admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\""+monday+"\",\"shiftCode\":\"early\",\"startsAt\":\"07:00\",\"endsAt\":\"14:30\",\"employeeId\":\"phase4-first\",\"dispatcher\":true}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long assignmentId=objectMapper.readTree(firstAssignment).path("id").asLong();
        mvc.perform(post("/api/v1/admin/schedules/{id}/assignments",scheduleId).header("Authorization","Bearer "+admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\""+monday+"\",\"shiftCode\":\"early\",\"startsAt\":\"07:00\",\"endsAt\":\"14:30\",\"employeeId\":\"phase4-second\",\"dispatcher\":false}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/schedules/assignments/{id}/substitutions",assignmentId).header("Authorization","Bearer "+admin).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("employeeId","phase4-fourth","startsAt",java.time.Instant.now().minusSeconds(60),"endsAt",java.time.Instant.now().plusSeconds(3600),"reason","联调代班"))))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/schedules/{id}/publish",scheduleId).header("Authorization","Bearer "+admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.scheduleStatus").value("published"));
        mvc.perform(get("/api/v1/schedules/current"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.assignments.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.assignments[?(@.employeeId == 'phase4-fourth')]").exists());
        mvc.perform(post("/api/v1/admin/schedules/{id}/copy",scheduleId).header("Authorization","Bearer "+admin).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("weekStart",monday.plusWeeks(1).toString()))))
                .andExpect(status().isOk());
        long firstRequest = statusRequest(first, "short_break", 10);
        long secondRequest = statusRequest(second, "short_break", 10);
        long thirdRequest = statusRequest(third, "short_break", 10);
        long longRequest = statusRequest(fourth, "long_break", 20);

        approve(admin, firstRequest, false, "批准");
        approve(admin, secondRequest, false, "批准");
        mvc.perform(post("/api/v1/admin/status/requests/{id}/approve", thirdRequest).header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"allowOverCapacity\":false,\"reason\":\"普通批准\"}"))
                .andExpect(status().isUnprocessableEntity());
        approve(admin, thirdRequest, true, "高峰期超额批准");
        assertThat(jdbc.sql("SELECT is_over_capacity FROM status_requests WHERE id=:id").param("id", thirdRequest).query(Boolean.class).single()).isTrue();
        assertThat(jdbc.sql("SELECT count(*) FROM status_requests WHERE queue_name='long_break' AND request_status='pending'").query(Long.class).single()).isEqualTo(1L);

        mvc.perform(post("/api/v1/admin/status/arrange").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"employeeId\":\"phase4-first\",\"statusCode\":\"meeting\",\"durationMinutes\":30}"))
                .andExpect(status().isOk());
        assertThat(jdbc.sql("SELECT request_status FROM status_requests WHERE id=:id").param("id", firstRequest).query(String.class).single()).isEqualTo("ended");
        assertThat(jdbc.sql("SELECT status_code FROM employee_current_statuses c JOIN users u ON u.id=c.user_id WHERE u.dingtalk_user_id='phase4-first'").query(String.class).single()).isEqualTo("meeting");
        jdbc.sql("UPDATE employee_current_statuses SET expected_end_at=now()-interval '1 minute' WHERE user_id=(SELECT id FROM users WHERE dingtalk_user_id='phase4-first')").update();
        overtimeWorker.markOvertime();
        assertThat(jdbc.sql("SELECT status_code||':'||is_overtime FROM employee_current_statuses c JOIN users u ON u.id=c.user_id WHERE u.dingtalk_user_id='phase4-first'").query(String.class).single()).isEqualTo("meeting:true");

        String announcement=mvc.perform(post("/api/v1/admin/announcements").header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"阶段四公告\",\"content\":\"运营联调\",\"contentFormat\":\"basic_rich_text\",\"pinned\":true,\"images\":[{\"filename\":\"notice.png\",\"mimeType\":\"image/png\",\"base64Data\":\"aGVsbG8=\"}]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.publicationStatus").value("published"))
                .andExpect(jsonPath("$.images.length()").value(1)).andReturn().getResponse().getContentAsString();
        long imageId=objectMapper.readTree(announcement).path("images").get(0).path("id").asLong();
        mvc.perform(get("/api/v1/announcement-images/{id}",imageId)).andExpect(status().isOk()).andExpect(content().bytes("hello".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        long announcementId=objectMapper.readTree(announcement).path("id").asLong();
        mvc.perform(post("/api/v1/admin/announcements/{id}/withdraw",announcementId).header("Authorization","Bearer "+admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.publicationStatus").value("withdrawn"));
        mvc.perform(get("/api/v1/status/dashboard").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.longBreakQueue[0].id").value(longRequest));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/admin/settings/knowledge.expiry_warning_days")
                        .header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"14\",\"reason\":\"阶段五上线验收\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.currentVersion").value(2));
        assertThat(jdbc.sql("SELECT count(*) FROM audit_logs WHERE module_code='settings' AND target_id='knowledge.expiry_warning_days'").query(Long.class).single()).isEqualTo(1L);
    }

    private long statusRequest(String token,String code,int minutes) throws Exception {
        String response=mvc.perform(post("/api/v1/status/requests").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"statusCode\":\""+code+"\",\"durationMinutes\":"+minutes+"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("id").asLong();
    }
    private void approve(String admin,long id,boolean over,String reason)throws Exception{
        mvc.perform(post("/api/v1/admin/status/requests/{id}/approve",id).header("Authorization","Bearer "+admin).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("allowOverCapacity",over,"reason",reason))))
                .andExpect(status().isOk());
    }

    private String login(String employeeId, String name) throws Exception {
        String response = mvc.perform(post("/api/v1/auth/mock-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(employeeId, name))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("sessionToken").asText();
    }

    private record LoginRequest(String employeeId, String name) {}
    private record QueryRequest(String query, int limit, int offset, String requestKind) {}
    private record FeedbackRequest(String query, String type, String detail, boolean confirmDuplicate) {}
    private record KnowledgeRequest(String category, String standardQuestion, java.util.List<String> userQuestions,
                                    java.util.List<String> keywords, String originalAnswer, String reason) {}
}
