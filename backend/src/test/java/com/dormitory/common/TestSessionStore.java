package com.dormitory.common;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

public class TestSessionStore implements SessionStore {
    record Entry(Long userId, Instant expires) {}
    final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();
    public void save(String hash, Long userId, Instant expires) { entries.put(hash, new Entry(userId, expires)); }
    public Long findUser(String hash, Instant now) { Entry e = entries.get(hash); return e != null && e.expires().isAfter(now) ? e.userId() : null; }
    public void delete(String hash) { entries.remove(hash); }
    public void deleteUser(Long userId) { entries.entrySet().removeIf(e -> e.getValue().userId().equals(userId)); }
    public void deleteExpired(Instant now) { entries.entrySet().removeIf(e -> !e.getValue().expires().isAfter(now)); }
}
