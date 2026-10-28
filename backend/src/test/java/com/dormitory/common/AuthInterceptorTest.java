package com.dormitory.common;

import com.dormitory.entity.SysUser;
import com.dormitory.mapper.SysUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthInterceptorTest {
    private final SysUserMapper users = mock(SysUserMapper.class);
    private final SessionService sessions = new SessionService();
    private AuthInterceptor interceptor;
    private SysUser user;

    @BeforeEach
    void setup() {
        interceptor = new AuthInterceptor(sessions, users);
        user = new SysUser();
        user.setId(7L);
        user.setRole("STUDENT");
        user.setStatus(1);
    }

    @Test
    void rejectsMissingForgedRevokedAndDisabledCredentials() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/users");
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(401, response.getStatus());
        request.addHeader("Authorization", "SMART-DORM-TOKEN-7-123456");
        assertFalse(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));

        String token = sessions.create(7L);
        request = new MockHttpServletRequest("GET", "/api/auth/users");
        request.addHeader("Authorization", token);
        when(users.selectById(7L)).thenReturn(user);
        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
        assertEquals(7L, request.getAttribute("currentUserId"));
        user.setStatus(0);
        assertFalse(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
        user.setStatus(1);
        sessions.revoke(token);
        assertFalse(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
    }

    @Test
    void optionsDoesNotRequireCredentials() throws Exception {
        assertTrue(interceptor.preHandle(new MockHttpServletRequest("OPTIONS", "/api/auth/users"),
                new MockHttpServletResponse(), new Object()));
    }

    @Test
    void revokingAllSessionsDoesNotAffectOtherUsers() {
        String first = sessions.create(7L);
        String second = sessions.create(7L);
        String other = sessions.create(8L);
        assertNotEquals(first, second);
        sessions.revokeUser(7L);
        assertNull(sessions.getUserId(first));
        assertNull(sessions.getUserId(second));
        assertEquals(8L, sessions.getUserId(other));
    }
}
