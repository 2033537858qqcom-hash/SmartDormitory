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

## P2 增量（已有库可执行）

```sql
ALTER TABLE hygiene_inspection
  ADD COLUMN photo_urls TEXT DEFAULT NULL COMMENT '现场检查照片(JSON数组)' AFTER create_time;

ALTER TABLE repair_order
  ADD COLUMN due_at DATETIME DEFAULT NULL COMMENT 'SLA截止时间' AFTER photo_urls;

CREATE TABLE IF NOT EXISTS `utility_payment_log` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `txn_no` VARCHAR(40) NOT NULL,
    `room_id` BIGINT NOT NULL,
    `bill_id` BIGINT DEFAULT NULL,
    `operator_id` BIGINT DEFAULT NULL,
    `action_type` VARCHAR(20) NOT NULL,
    `electric_delta` DECIMAL(10, 2) DEFAULT 0.00,
    `water_delta` DECIMAL(10, 2) DEFAULT 0.00,
    `electric_before` DECIMAL(10, 2) DEFAULT 0.00,
    `water_before` DECIMAL(10, 2) DEFAULT 0.00,
    `electric_after` DECIMAL(10, 2) DEFAULT 0.00,
    `water_after` DECIMAL(10, 2) DEFAULT 0.00,
    `channel` VARCHAR(20) NOT NULL DEFAULT 'DEMO',
    `remark` VARCHAR(200) DEFAULT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_txn` (`txn_no`),
    KEY `idx_room` (`room_id`)
) ENGINE=InnoDB COMMENT='水电演示模拟缴费流水';

-- 辅导员演示账号（密码同其它种子：明文 123456 的 BCrypt）
INSERT INTO sys_user (id, username, password, real_name, role, gender, phone, email, status)
SELECT 29, 'counselor01', '$2b$10$JOARIO1GOwvHQaXkicLM6Ok9ONNiQ68Z5Tn5ZNULTkdi//6ls6r2i',
       '陈晓雯(计科辅导员)', 'COUNSELOR', 2, '13688880001', 'counselor01@edu.cn', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'counselor01');
```

### P2 说明

- **P2-1** 卫生检查可附现场照片；`GET /api/affair/hygiene-trend?roomId=` 返回寝室近 N 次总分序列供折线趋势。
- **P2-2** 角色 `COUNSELOR`：只读查看评分/违纪/房态/水电账单；菜单与路由裁剪；演示账号 `counselor01`。
- **P2-3** 报修创建时写入 `due_at`（HIGH 4h / NORMAL 24h / LOW 48h）；列表返回 `overdue` 徽章字段。
- **P2-4** `pay-or-recharge` 写入 `utility_payment_log` 并返回 `txnNo` + `demoSimulated`；界面强标注「演示模拟」。
- **P2-5** 学生端仅为 Vite H5（小程序形态 UI），非微信小程序/非 Uni-app；勿宣称可在微信开发者工具打开。

### 仍未做（真正超出演示范围）

- 微信/支付宝真支付网关、校园卡对接
- 实体闸机 / 人脸门禁硬件 SDK
- 完整 uni-app / 微信原生小程序工程迁移

## 报修 SLA 提醒增量

本次更新的四项增量也整理在 [`20261028-workflow-upgrade.sql`](./20261028-workflow-upgrade.sql) 中。已有库先备份并确认之前的 P0/P1/P2 增量已执行，再选择目标数据库并运行该脚本一次；不要在已执行下面各段增量的库上重复运行。新库使用初始化脚本时无需执行。

已有库在备份后执行以下语句一次，新库使用初始化脚本时无需重复执行：

```sql
ALTER TABLE repair_order
  ADD COLUMN sla_reminder_sent_at DATETIME DEFAULT NULL COMMENT '临近截止提醒时间',
  ADD COLUMN sla_escalated_at DATETIME DEFAULT NULL COMMENT '超时升级通知时间';
```

服务默认每五分钟检查一次；到期前一小时通知维修人员，超时后通知维修人员和楼栋宿管（无宿管时通知管理员）。消息与标记在同一事务中保存。

## 能耗异常排查增量

已有库在备份后执行一次：

```sql
ALTER TABLE utility_bill
  ADD COLUMN anomaly_status VARCHAR(20) DEFAULT NULL,
  ADD COLUMN anomaly_note VARCHAR(500) DEFAULT NULL,
  ADD COLUMN anomaly_repair_id BIGINT DEFAULT NULL,
  ADD COLUMN anomaly_updated_by BIGINT DEFAULT NULL,
  ADD COLUMN anomaly_updated_at DATETIME DEFAULT NULL;
```

## 入住和假期留宿申请增量

已有库执行：

```sql
CREATE TABLE IF NOT EXISTS residence_request (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  student_id BIGINT NOT NULL, room_id BIGINT NOT NULL,
  request_type VARCHAR(20) NOT NULL, from_date DATE DEFAULT NULL, to_date DATE DEFAULT NULL,
  reason VARCHAR(500) NOT NULL, status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  reviewer_id BIGINT DEFAULT NULL, review_comment VARCHAR(500) DEFAULT NULL,
  created_at DATETIME NOT NULL, reviewed_at DATETIME DEFAULT NULL,
  KEY idx_residence_student (student_id, status), KEY idx_residence_room (room_id, status)
) ENGINE=InnoDB COMMENT='入住和假期留宿申请';
```

退宿交接清单（已有库执行一次）：

```sql
ALTER TABLE dorm_adjust_apply ADD COLUMN checkout_checklist TEXT DEFAULT NULL COMMENT '退宿交接检查记录(JSON)';
```

## 会话持久化增量（启动新版后端之前）

请先备份数据库，然后选择目标库，执行 `sql/20261029-auth-session.sql`。该脚本只创建 `auth_session` 表（可重复执行），不会删除原有业务数据。新安装的初始化脚本已包含该表；原有内存会话不能迁移，升级后用户需要重新登录一次。不要为升级已有库重新导入包含 `DROP DATABASE` 的初始化脚本。
