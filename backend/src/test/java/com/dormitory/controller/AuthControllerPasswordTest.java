package com.dormitory.controller;

import cn.hutool.crypto.digest.BCrypt;
import cn.hutool.crypto.digest.DigestUtil;
import com.dormitory.entity.SysUser;
import com.dormitory.mapper.SysUserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerPasswordTest {
    @Mock SysUserMapper users;
    @InjectMocks AuthController controller;

    private SysUser user(String password) {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setUsername("admin");
        user.setRole("ADMIN");
        user.setStatus(1);
        user.setPassword(password);
        return user;
    }

    @Test
    void legacyPlaintextAndMd5AccountsUpgradeOnSuccessfulLogin() {
        for (String old : new String[]{"secret", DigestUtil.md5Hex("secret")}) {
            SysUser user = user(old);
            when(users.selectOne(any())).thenReturn(user);
            AtomicReference<String> stored = new AtomicReference<>();
            doAnswer(invocation -> { stored.set(((SysUser) invocation.getArgument(0)).getPassword()); return 1; })
                    .when(users).updateById(any(SysUser.class));
            assertEquals(200, controller.login(Map.of("username", "admin", "password", "secret")).getCode());
            assertNotNull(stored.get());
            assertTrue(BCrypt.checkpw("secret", stored.get()));
        }
    }

    @Test
    void bcryptLoginWorksAndWrongPasswordDoesNotUpgrade() {
        when(users.selectOne(any())).thenReturn(user(BCrypt.hashpw("secret")));
        assertEquals(500, controller.login(Map.of("username", "admin", "password", "wrong")).getCode());
        verify(users, never()).updateById(any(SysUser.class));
        assertEquals(200, controller.login(Map.of("username", "admin", "password", "secret")).getCode());
    }

    @Test
    void newlyCreatedAndResetPasswordsAreHashed() {
        AtomicReference<String> stored = new AtomicReference<>();
        doAnswer(invocation -> { stored.set(((SysUser) invocation.getArgument(0)).getPassword()); return 1; })
                .when(users).insert(any(SysUser.class));
        SysUser newUser = user("secret");
        newUser.setId(null);
        controller.saveOrUpdateUser(newUser);
        assertNotNull(stored.get());
        assertTrue(BCrypt.checkpw("secret", stored.get()));

        when(users.selectById(1L)).thenReturn(user("old"));
        doAnswer(invocation -> { stored.set(((SysUser) invocation.getArgument(0)).getPassword()); return 1; })
                .when(users).updateById(any(SysUser.class));
        controller.resetPassword(1L);
        assertTrue(BCrypt.checkpw("123456", stored.get()));
    }

    @Test
    void changingPasswordVerifiesBcryptAndStoresNewHash() {
        when(users.selectById(1L)).thenReturn(user(BCrypt.hashpw("old")));
        AtomicReference<String> stored = new AtomicReference<>();
        doAnswer(invocation -> { stored.set(((SysUser) invocation.getArgument(0)).getPassword()); return 1; })
                .when(users).updateById(any(SysUser.class));
        assertEquals(200, controller.changePassword(Map.of("userId", 1, "oldPassword", "old", "newPassword", "new-secret")).getCode());
        assertNotNull(stored.get());
        assertTrue(BCrypt.checkpw("new-secret", stored.get()));
    }
}
