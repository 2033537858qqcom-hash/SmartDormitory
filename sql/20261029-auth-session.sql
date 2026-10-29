-- 可重复执行的会话持久化增量；先选择并备份目标数据库。
CREATE TABLE IF NOT EXISTS auth_session (
    token_hash CHAR(64) NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    KEY idx_auth_session_user (user_id),
    KEY idx_auth_session_expiry (expires_at)
) ENGINE=InnoDB COMMENT='登录会话，仅保存令牌哈希';
