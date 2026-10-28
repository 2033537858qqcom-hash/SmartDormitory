package com.dormitory.controller;

import com.dormitory.common.AuthInterceptor;
import com.dormitory.common.SessionService;
import com.dormitory.common.WebMvcConfig;
import com.dormitory.entity.SysUser;
import com.dormitory.mapper.*;
import com.dormitory.service.SmartEvaluationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExportController.class)
@ContextConfiguration(classes = ExportAuthenticationTest.TestConfig.class)
class ExportAuthenticationTest {
    @Configuration
    @EnableAutoConfiguration
    @Import({ExportController.class, AuthInterceptor.class, SessionService.class, WebMvcConfig.class})
    static class TestConfig {}
    @Autowired MockMvc mvc;
    @Autowired SessionService sessions;
    @MockBean SysUserMapper users;
    @MockBean StudentHabitProfileMapper profiles;
    @MockBean DormBedMapper beds;
    @MockBean DormRoomMapper rooms;
    @MockBean DormBuildingMapper buildings;
    @MockBean UtilityBillMapper bills;
    @MockBean SmartEvaluationService evaluation;

    @Test
    void exportsRequireAValidSession() throws Exception {
        mvc.perform(get("/api/export/students")).andExpect(status().isUnauthorized());
        SysUser user = new SysUser();
        user.setId(1L);
        user.setRole("ADMIN");
        user.setStatus(1);
        when(users.selectById(1L)).thenReturn(user);
        mvc.perform(get("/api/export/students").header("Authorization", sessions.create(1L)))
                .andExpect(status().isOk());
    }
}
