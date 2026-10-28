# 数据库说明（演示 dump）

本仓库仍使用单文件 `smart_dormitory.sql` 初始化（含 `DROP DATABASE`，**仅限本地/空库**）。

## 种子密码

- 演示账号明文口令仍为 `123456`（见根目录 README）。
- 种子中的 `sys_user.password` 已改为 **BCrypt** 哈希，与 `AuthController` 登录校验/升级路径一致。
- 若你本地库仍是旧明文种子，可任选其一：
  1. 重新导入本 dump；或
  2. 登录一次后由后端自动升级为 BCrypt；或
  3. 执行：

```sql
UPDATE sys_user SET password = '$2b$10$JOARIO1GOwvHQaXkicLM6Ok9ONNiQ68Z5Tn5ZNULTkdi//6ls6r2i';
```

（上述哈希对应明文 `123456`。）

## 后续（未做）

完整 Flyway/Liquibase、外键与 schema/seed 拆分未引入，以免大改演示导入流程。

## P0 增量（已有库可执行）

```sql
ALTER TABLE discipline_record
  ADD COLUMN appeal_proof_type VARCHAR(50) DEFAULT NULL COMMENT '申诉证明类型' AFTER recorder_id,
  ADD COLUMN appeal_witness VARCHAR(50) DEFAULT NULL COMMENT '证明人' AFTER appeal_proof_type,
  ADD COLUMN appeal_attachments TEXT DEFAULT NULL COMMENT '申诉附件占位(JSON数组)' AFTER appeal_witness;
```
