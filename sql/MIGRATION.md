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


## P1 增量（已有库可执行）

```sql
ALTER TABLE repair_order
  ADD COLUMN photo_urls TEXT DEFAULT NULL COMMENT '故障图片(JSON数组，本地路径或dataURL演示)' AFTER create_time;

CREATE TABLE IF NOT EXISTS `user_message` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '消息ID',
    `user_id` BIGINT NOT NULL COMMENT '接收用户ID',
    `title` VARCHAR(100) NOT NULL COMMENT '标题',
    `content` VARCHAR(500) NOT NULL COMMENT '正文',
    `msg_type` VARCHAR(20) NOT NULL DEFAULT 'SYSTEM' COMMENT 'REPAIR/APPEAL/ADJUST/UTILITY/VISITOR/SYSTEM',
    `biz_id` VARCHAR(50) DEFAULT NULL COMMENT '关联业务ID',
    `read_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '0未读 1已读',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY `idx_user_read` (`user_id`, `read_flag`)
) ENGINE=InnoDB COMMENT='站内消息';
```

### 报修图片存储说明（演示）

- 学生端可压缩后上传至 `POST /api/upload/image`（`biz=repair`），文件落盘到后端工作目录 `uploads/repair/`，URL 形如 `/uploads/repair/<file>`。
- 亦可在提交报修时直接传 `photoUrls` JSON 数组（含 dataURL 或相对路径），不必上 OSS。
- 静态映射见 `WebMvcConfig` 的 `/uploads/**`。

### P2 暂缓（本迭代不实现）

- 卫生检查现场照片与历史趋势（P2-1）
- 辅导员/学院只读角色（P2-2）
- 维修工单 SLA / 催单（P2-3）
- 缴费流水号与「演示模拟扣款」强标注（P2-4，界面已有扣余额核销语义，未单独做流水表）
- 学生端真小程序迁移（P2-5，保持 H5 小程序形态 UI）
