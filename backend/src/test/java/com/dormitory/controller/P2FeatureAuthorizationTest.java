package com.dormitory.controller;

import com.dormitory.common.AuthInterceptor;
import com.dormitory.common.SessionService;
import com.dormitory.entity.RepairOrder;
import com.dormitory.entity.SysUser;
import com.dormitory.mapper.SysUserMapper;
import com.dormitory.service.SmartRepairService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class P2FeatureAuthorizationTest {
    private final SysUserMapper users = mock(SysUserMapper.class);
    private final SessionService sessions = new SessionService();
    private AuthInterceptor interceptor;
    private SysUser counselor;

    @BeforeEach
    void setup() {
        interceptor = new AuthInterceptor(sessions, users);
        counselor = new SysUser();
        counselor.setId(29L);
        counselor.setRole("COUNSELOR");
        counselor.setStatus(1);
    }

    @Test
    void counselorCanReadScoresAndDisciplineButCannotWrite() throws Exception {
        when(users.selectById(29L)).thenReturn(counselor);
        String token = sessions.create(29L);

        for (String path : new String[]{
                "/api/affair/evaluation", "/api/affair/hygiene", "/api/affair/hygiene-trend",
                "/api/affair/discipline", "/api/dorm/rooms", "/api/dorm/buildings",
                "/api/utility/bills", "/api/dashboard/overview"
        }) {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", path);
            req.addHeader("Authorization", token);
            assertTrue(interceptor.preHandle(req, new MockHttpServletResponse(), new Object()), path);
        }

        for (String path : new String[]{
                "/api/affair/hygiene", "/api/affair/discipline", "/api/repair/submit",
                "/api/utility/pay-or-recharge", "/api/utility/meter-entry", "/api/dorm/assign-bed"
        }) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", path);
            req.addHeader("Authorization", token);
            MockHttpServletResponse resp = new MockHttpServletResponse();
            assertFalse(interceptor.preHandle(req, resp, new Object()), "POST " + path);
            assertEquals(403, resp.getStatus(), path);
        }
    }

    @Test
    void repairSlaOverdueWhenPastDueAndStillOpen() {
        assertEquals(4, SmartRepairService.slaHours("HIGH"));
        assertEquals(24, SmartRepairService.slaHours("NORMAL"));
        assertEquals(48, SmartRepairService.slaHours("LOW"));

        LocalDateTime now = LocalDateTime.of(2026, 10, 28, 12, 0);
        assertEquals(now.plusHours(4), SmartRepairService.computeDueAt(now, "HIGH"));

        RepairOrder open = new RepairOrder();
        open.setStatus("ASSIGNED");
        open.setDueAt(now.minusMinutes(1));
        assertTrue(SmartRepairService.isOverdue(open, now));

        RepairOrder done = new RepairOrder();
        done.setStatus("COMPLETED");
        done.setDueAt(now.minusHours(2));
        assertFalse(SmartRepairService.isOverdue(done, now));

        RepairOrder future = new RepairOrder();
        future.setStatus("PROCESSING");
        future.setDueAt(now.plusHours(1));
        assertFalse(SmartRepairService.isOverdue(future, now));
    }
}
