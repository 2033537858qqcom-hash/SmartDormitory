package com.dormitory.integration;

import com.dormitory.common.Result;
import com.dormitory.controller.DormAffairController;
import com.dormitory.entity.DormAdjustApply;
import com.dormitory.service.SmartUtilityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "app.repair-sla-check-ms=3600000")
@EnabledIfEnvironmentVariable(named = "DORM_INTEGRATION_TEST", matches = "true")
class MySqlWorkflowIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired SmartUtilityService utility;
    @Autowired DormAffairController affair;

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

    private MockHttpServletRequest admin() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("currentUserRole", "ADMIN"); request.setAttribute("currentUserId", 1L); return request;
    }
}
