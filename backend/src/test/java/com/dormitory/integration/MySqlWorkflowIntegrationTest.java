package com.dormitory.integration;

import com.dormitory.common.Result;
import com.dormitory.controller.DormAffairController;
import com.dormitory.entity.DormAdjustApply;
import com.dormitory.service.SmartUtilityService;
import com.dormitory.service.SmartAllocationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"app.repair-sla-check-ms=3600000", "app.demo-enabled=true"})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "DORM_INTEGRATION_TEST", matches = "true")
class MySqlWorkflowIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired SmartUtilityService utility;
    @Autowired SmartAllocationService allocation;
    @Autowired DormAffairController affair;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @BeforeEach
    void refuseNonTestDatabase() {
        String name = jdbc.queryForObject("SELECT DATABASE()", String.class);
        assertNotNull(name);
        assertTrue(name.matches("codex_dorm_it_[a-f0-9]+"), "Integration tests require an isolated test database");
    }

    @Test
    void concurrentRetriesCreateOnlyOneRecharge() throws Exception {
        BigDecimal before = jdbc.queryForObject("SELECT electric_balance FROM dorm_room WHERE id=1", BigDecimal.class);
        String requestId = UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Map<String, Object>> call = () -> { start.await(); return utility.processPayment(1L, null, new BigDecimal("10.00"), BigDecimal.ZERO, 10L, requestId); };
            Future<Map<String, Object>> one = pool.submit(call), two = pool.submit(call);
            start.countDown();
            assertEquals(one.get(20, TimeUnit.SECONDS).get("txnNo"), two.get(20, TimeUnit.SECONDS).get("txnNo"));
            assertEquals(0, before.add(new BigDecimal("10.00")).compareTo(jdbc.queryForObject("SELECT electric_balance FROM dorm_room WHERE id=1", BigDecimal.class)));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM utility_payment_log WHERE txn_no=?", Integer.class, "DEMO-" + requestId));
        } finally { pool.shutdownNow(); }
    }

    @Test
    void concurrentTransfersToSameBedHaveOneWinner() throws Exception {
        jdbc.update("INSERT INTO dorm_adjust_apply (student_id,apply_type,old_room_id,old_bed_no,target_room_id,target_bed_no,reason,status) VALUES (10,'ADJUST',1,1,2,4,'test','PENDING'),(11,'ADJUST',1,2,2,4,'test','PENDING')");
        List<Long> ids = jdbc.queryForList("SELECT id FROM dorm_adjust_apply WHERE reason='test' ORDER BY id DESC LIMIT 2", Long.class);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Result<DormAdjustApply>> first = () -> { start.await(); return affair.reviewAdjustApply(Map.of("id", ids.get(0), "status", "APPROVED"), admin()); };
            Callable<Result<DormAdjustApply>> second = () -> { start.await(); return affair.reviewAdjustApply(Map.of("id", ids.get(1), "status", "APPROVED"), admin()); };
            Future<Result<DormAdjustApply>> one = pool.submit(first), two = pool.submit(second);
            start.countDown();
            int successes = (one.get(20, TimeUnit.SECONDS).getCode() == 200 ? 1 : 0) + (two.get(20, TimeUnit.SECONDS).getCode() == 200 ? 1 : 0);
            assertEquals(1, successes);
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dorm_bed WHERE room_id=2 AND bed_no=4 AND student_id IN (10,11)", Integer.class));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dorm_bed WHERE room_id=1 AND student_id IN (10,11) AND status=1", Integer.class));
        } finally { pool.shutdownNow(); }
    }

    @Test
    void allocationWritesOnlyUnoccupiedBedsAndReportsExplanations() {
        Map<String, Object> result = allocation.runSmartAllocation(2L, true, null, "HABIT_FIRST", 1L);
        assertEquals(8, result.get("allocatedStudentCount"));
        assertNotNull(result.get("scoreNote"));
        assertEquals(8, jdbc.queryForObject("SELECT COUNT(*) FROM dorm_bed WHERE room_id IN (5,6) AND student_id BETWEEN 21 AND 28 AND status=1", Integer.class));
    }

    @Test
    void checkoutRecordsHandoverBeforeReleasingBed() {
        jdbc.update("INSERT INTO dorm_adjust_apply (student_id,apply_type,old_room_id,old_bed_no,reason,status) VALUES (12,'CHECKOUT',1,3,'checkout-test','PENDING')");
        Long id = jdbc.queryForObject("SELECT id FROM dorm_adjust_apply WHERE reason='checkout-test' ORDER BY id DESC LIMIT 1", Long.class);
        assertNotEquals(200, affair.reviewAdjustApply(Map.of("id", id, "status", "APPROVED"), admin()).getCode());
        var handover = Map.of("keyReturned", true, "facilitiesChecked", true, "roomCleared", true, "note", "床铺完好");
        assertEquals(200, affair.reviewAdjustApply(Map.of("id", id, "status", "APPROVED", "handover", handover), admin()).getCode());
        assertNull(jdbc.queryForObject("SELECT student_id FROM dorm_bed WHERE room_id=1 AND bed_no=3", Long.class));
        assertTrue(jdbc.queryForObject("SELECT checkout_checklist FROM dorm_adjust_apply WHERE id=?", String.class, id).contains("床铺完好"));
    }

    private MockHttpServletRequest admin() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("currentUserRole", "ADMIN"); request.setAttribute("currentUserId", 1L); return request;
    }

    @Test
    void studentAndManagerCanCompleteResidenceRequestThroughHttp() throws Exception {
        String studentToken = login("20230105");
        String managerToken = login("dm01");
        var payload = Map.of("requestType", "HOLIDAY", "reason", "留校学习", "fromDate", java.time.LocalDate.now().plusDays(1).toString(), "toDate", java.time.LocalDate.now().plusDays(3).toString());
        JsonNode submitted = sendJson("/api/residence/requests", studentToken, payload);
        assertEquals(200, submitted.get("code").asInt());
        long id = submitted.path("data").path("id").asLong();
        assertTrue(id > 0);
        assertEquals(14, submitted.path("data").path("studentId").asInt());
        mvc.perform(post("/api/residence/review").header("Authorization", studentToken).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("id", id, "status", "APPROVED")))).andExpect(status().isForbidden());
        JsonNode reviewed = sendJson("/api/residence/review", managerToken, Map.of("id", id, "status", "APPROVED", "comment", "已核实"));
        assertEquals(200, reviewed.get("code").asInt());
        assertEquals("APPROVED", reviewed.path("data").path("status").asText());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM user_message WHERE user_id=14 AND msg_type='RESIDENCE' AND biz_id=?", Integer.class, String.valueOf(id)));
    }

    private String login(String username) throws Exception {
        String response = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", username, "password", "123456"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        JsonNode body = json.readTree(response);
        assertEquals(200, body.path("code").asInt());
        return body.path("data").path("token").asText();
    }

    private JsonNode sendJson(String path, String token, Object payload) throws Exception {
        String response = mvc.perform(post(path).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(payload))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return json.readTree(response);
    }
}
