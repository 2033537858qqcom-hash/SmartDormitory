package com.dormitory.common;

import java.time.Instant;

public interface SessionStore {
    void save(String tokenHash, Long userId, Instant expiresAt);
    Long findUser(String tokenHash, Instant now);
    void delete(String tokenHash);
    void deleteUser(Long userId);
    void deleteExpired(Instant now);
}
