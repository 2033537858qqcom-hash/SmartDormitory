-- =========================================================================
-- 《智慧宿舍管理系统》 数据库增量升级补丁 (适用于已有旧库无损升级)
-- 说明：如果已有数据库 smart_dormitory 中保留了业务数据，无需删除重建，
--      直接在 smart_dormitory 库中执行本脚本即可补充所有缺失的字段与数据表。
-- =========================================================================

USE `smart_dormitory`;

-- -------------------------------------------------------------------------
-- 1. 调宿与退宿申请表 (dorm_adjust_apply)：补充退宿交接检查记录
-- -------------------------------------------------------------------------
ALTER TABLE `dorm_adjust_apply`
  ADD COLUMN `checkout_checklist` TEXT DEFAULT NULL COMMENT '退宿交接检查记录(JSON)';

-- -------------------------------------------------------------------------
-- 2. 报修工单表 (repair_order)：补充故障图片、SLA截止时间及告警字段
-- -------------------------------------------------------------------------
ALTER TABLE `repair_order`
  ADD COLUMN `photo_urls` TEXT DEFAULT NULL COMMENT '故障图片(JSON数组，本地路径或dataURL演示)' AFTER `create_time`,
  ADD COLUMN `due_at` DATETIME DEFAULT NULL COMMENT 'SLA截止时间(高危4h/普通24h/低优48h)' AFTER `photo_urls`,
  ADD COLUMN `sla_reminder_sent_at` DATETIME DEFAULT NULL COMMENT '临近截止提醒时间' AFTER `due_at`,
  ADD COLUMN `sla_escalated_at` DATETIME DEFAULT NULL COMMENT '超时升级通知时间' AFTER `sla_reminder_sent_at`;

-- -------------------------------------------------------------------------
-- 3. 水电账单表 (utility_bill)：补充能耗异常排查闭环字段
-- -------------------------------------------------------------------------
ALTER TABLE `utility_bill`
  ADD COLUMN `anomaly_status` VARCHAR(20) DEFAULT NULL COMMENT '异常状态: PENDING/INVESTIGATING/RESOLVED',
  ADD COLUMN `anomaly_note` VARCHAR(500) DEFAULT NULL COMMENT '异常排查记录',
  ADD COLUMN `anomaly_repair_id` BIGINT DEFAULT NULL COMMENT '关联排查工单ID',
  ADD COLUMN `anomaly_updated_by` BIGINT DEFAULT NULL COMMENT '排查更新人ID',
  ADD COLUMN `anomaly_updated_at` DATETIME DEFAULT NULL COMMENT '排查更新时间';

-- -------------------------------------------------------------------------
-- 4. 卫生检查评比表 (hygiene_inspection)：补充现场检查照片字段
-- -------------------------------------------------------------------------
ALTER TABLE `hygiene_inspection`
  ADD COLUMN `photo_urls` TEXT DEFAULT NULL COMMENT '现场检查照片(JSON数组，本地路径或dataURL演示)' AFTER `create_time`;

-- -------------------------------------------------------------------------
-- 5. 违纪记录表 (discipline_record)：补充申诉材料与证明字段
-- -------------------------------------------------------------------------
ALTER TABLE `discipline_record`
  ADD COLUMN `appeal_proof_type` VARCHAR(50) DEFAULT NULL COMMENT '申诉证明类型' AFTER `recorder_id`,
  ADD COLUMN `appeal_witness` VARCHAR(50) DEFAULT NULL COMMENT '证明人' AFTER `appeal_proof_type`,
  ADD COLUMN `appeal_attachments` TEXT DEFAULT NULL COMMENT '申诉附件占位(JSON数组)' AFTER `appeal_witness`;

-- -------------------------------------------------------------------------
-- 6. 新增：站内消息表 (user_message)
-- -------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `user_message` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '消息ID',
    `user_id` BIGINT NOT NULL COMMENT '接收用户ID',
    `title` VARCHAR(100) NOT NULL COMMENT '标题',
    `content` VARCHAR(500) NOT NULL COMMENT '正文',
    `msg_type` VARCHAR(20) NOT NULL DEFAULT 'SYSTEM' COMMENT '消息类型: REPAIR/APPEAL/ADJUST/UTILITY/VISITOR/SYSTEM',
    `biz_id` VARCHAR(50) DEFAULT NULL COMMENT '关联业务ID',
    `read_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '0未读 1已读',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY `idx_user_read` (`user_id`, `read_flag`)
) ENGINE=InnoDB COMMENT='站内消息通知表';

-- -------------------------------------------------------------------------
-- 7. 新增：入住与假期留宿申请表 (residence_request)
-- -------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `residence_request` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '申请ID',
    `student_id` BIGINT NOT NULL COMMENT '申请学生ID',
    `room_id` BIGINT NOT NULL COMMENT '房间ID',
    `request_type` VARCHAR(20) NOT NULL COMMENT 'CHECK_IN-入住, VACATION_STAY-假期留宿',
    `from_date` DATE DEFAULT NULL COMMENT '开始日期',
    `to_date` DATE DEFAULT NULL COMMENT '结束日期',
    `reason` VARCHAR(500) NOT NULL COMMENT '申请理由',
    `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/APPROVED/REJECTED',
    `reviewer_id` BIGINT DEFAULT NULL COMMENT '审批人ID',
    `review_comment` VARCHAR(500) DEFAULT NULL COMMENT '审批意见',
    `created_at` DATETIME NOT NULL COMMENT '创建时间',
    `reviewed_at` DATETIME DEFAULT NULL COMMENT '审批时间',
    KEY `idx_residence_student` (`student_id`, `status`),
    KEY `idx_residence_room` (`room_id`, `status`)
) ENGINE=InnoDB COMMENT='入住和假期留宿申请表';

-- -------------------------------------------------------------------------
-- 8. 新增：水电演示模拟缴费流水表 (utility_payment_log)
-- -------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `utility_payment_log` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '流水ID',
    `txn_no` VARCHAR(40) NOT NULL COMMENT '演示流水号 DEMO-...',
    `room_id` BIGINT NOT NULL COMMENT '寝室ID',
    `bill_id` BIGINT DEFAULT NULL COMMENT '关联账单ID(缴费时)',
    `operator_id` BIGINT DEFAULT NULL COMMENT '操作人用户ID',
    `action_type` VARCHAR(20) NOT NULL COMMENT '操作类型: RECHARGE / PAY_BILL',
    `electric_delta` DECIMAL(10, 2) DEFAULT 0.00 COMMENT '电费余额变动',
    `water_delta` DECIMAL(10, 2) DEFAULT 0.00 COMMENT '水费余额变动',
    `electric_before` DECIMAL(10, 2) DEFAULT 0.00 COMMENT '操作前电费余额',
    `water_before` DECIMAL(10, 2) DEFAULT 0.00 COMMENT '操作前水费余额',
    `electric_after` DECIMAL(10, 2) DEFAULT 0.00 COMMENT '操作后电费余额',
    `water_after` DECIMAL(10, 2) DEFAULT 0.00 COMMENT '操作后水费余额',
    `channel` VARCHAR(20) NOT NULL DEFAULT 'DEMO' COMMENT '固定 DEMO：非真实支付',
    `remark` VARCHAR(200) DEFAULT NULL COMMENT '说明',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY `uk_txn` (`txn_no`),
    KEY `idx_room` (`room_id`)
) ENGINE=InnoDB COMMENT='水电演示模拟缴费流水表';

-- -------------------------------------------------------------------------
-- 9. 补充辅导员演示账号 (若已存在则忽略)
-- -------------------------------------------------------------------------
INSERT INTO `sys_user` (`id`, `username`, `password`, `real_name`, `role`, `gender`, `phone`, `email`, `status`)
SELECT 29, 'counselor01', '$2b$10$JOARIO1GOwvHQaXkicLM6Ok9ONNiQ68Z5Tn5ZNULTkdi//6ls6r2i',
       '陈晓雯(计科辅导员)', 'COUNSELOR', 2, '13688880001', 'counselor01@edu.cn', 1
WHERE NOT EXISTS (SELECT 1 FROM `sys_user` WHERE `username` = 'counselor01');
