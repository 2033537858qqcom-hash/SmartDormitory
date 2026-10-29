package com.dormitory.common;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class JdbcSessionStore implements SessionStore {
    private final JdbcTemplate jdbc;

    @Override
    public void save(String tokenHash, Long userId, Instant expiresAt) {
        jdbc.update("INSERT INTO auth_session (token_hash, user_id, expires_at) VALUES (?, ?, ?)", tokenHash, userId, Timestamp.from(expiresAt));
    }

    @Override
    public Long findUser(String tokenHash, Instant now) {
        List<Long> users = jdbc.query("SELECT user_id FROM auth_session WHERE token_hash = ? AND expires_at > ?",
                (row, index) -> row.getLong(1), tokenHash, Timestamp.from(now));
        return users.isEmpty() ? null : users.get(0);
    }

    @Override public void delete(String tokenHash) { jdbc.update("DELETE FROM auth_session WHERE token_hash = ?", tokenHash); }
    @Override public void deleteUser(Long userId) { jdbc.update("DELETE FROM auth_session WHERE user_id = ?", userId); }
    @Override public void deleteExpired(Instant now) { jdbc.update("DELETE FROM auth_session WHERE expires_at <= ?", Timestamp.from(now)); }
}
