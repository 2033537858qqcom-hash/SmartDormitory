package com.dormitory.common;

import cn.hutool.crypto.digest.DigestUtil;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

@Service
public class SessionService {
    private static final long LIFETIME_SECONDS = 8 * 60 * 60;
    private final SecureRandom random = new SecureRandom();
    private final SessionStore store;

    public SessionService(SessionStore store) { this.store = store; }

    public String create(Long userId) {
        Instant now = Instant.now();
        store.deleteExpired(now);
        byte[] bytes = new byte[32];
        while (true) {
            random.nextBytes(bytes);
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            try {
                store.save(DigestUtil.sha256Hex(token), userId, now.plusSeconds(LIFETIME_SECONDS));
                return token;
            } catch (DuplicateKeyException ignored) {
                // Generate a fresh token if the hash already exists.
            }
        }
    }

    public Long getUserId(String token) {
        if (token == null || token.isBlank()) return null;
        return store.findUser(DigestUtil.sha256Hex(token), Instant.now());
    }

    public void revoke(String token) {
        if (token != null && !token.isBlank()) store.delete(DigestUtil.sha256Hex(token));
    }

    public void revokeUser(Long userId) { if (userId != null) store.deleteUser(userId); }
}
