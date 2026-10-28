package com.dormitory.common;

import org.springframework.stereotype.Service;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SessionService {
    private static final long LIFETIME_SECONDS = 8 * 60 * 60;
    private final SecureRandom random = new SecureRandom();
    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();

    public String create(Long userId) {
        Instant now = Instant.now();
        sessions.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
        byte[] bytes = new byte[32];
        String token;
        do {
            random.nextBytes(bytes);
            token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        } while (sessions.putIfAbsent(token, new Session(userId, now.plusSeconds(LIFETIME_SECONDS))) != null);
        return token;
    }

    public Long getUserId(String token) {
        if (token == null) return null;
        Session session = sessions.get(token);
        if (session == null) return null;
        if (!session.expiresAt().isAfter(Instant.now())) {
            sessions.remove(token, session);
            return null;
        }
        return session.userId();
    }

    public void revoke(String token) {
        if (token != null) sessions.remove(token);
    }

    public void revokeUser(Long userId) {
        sessions.entrySet().removeIf(entry -> entry.getValue().userId().equals(userId));
    }

    private record Session(Long userId, Instant expiresAt) {}
}
