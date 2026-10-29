package com.dormitory.common;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import static org.junit.jupiter.api.Assertions.*;

class SessionPersistenceTest {
    @Test
    void sharedStorePreservesSessionsAcrossServiceRestartAndSupportsRevocation() {
        SessionStore store = new TestSessionStore();
        SessionService beforeRestart = new SessionService(store);
        String token = beforeRestart.create(7L);
        SessionService afterRestart = new SessionService(store);
        assertEquals(7L, afterRestart.getUserId(token));
        afterRestart.revokeUser(7L);
        assertNull(beforeRestart.getUserId(token));
    }

    static class TestSessionStore implements SessionStore {
        record Entry(Long userId, Instant expires) {}
        final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();
        public void save(String hash, Long userId, Instant expires) { entries.put(hash, new Entry(userId, expires)); }
        public Long findUser(String hash, Instant now) {
            Entry entry = entries.get(hash);
            return entry != null && entry.expires().isAfter(now) ? entry.userId() : null;
        }
        public void delete(String hash) { entries.remove(hash); }
        public void deleteUser(Long userId) { entries.entrySet().removeIf(e -> e.getValue().userId().equals(userId)); }
        public void deleteExpired(Instant now) { entries.entrySet().removeIf(e -> !e.getValue().expires().isAfter(now)); }
    }
}
