-- 先备份，并选择需要升级的数据库。仅从本次功能更新之前的结构执行一次。
-- 此脚本不会删除数据库或现有业务数据。
ALTER TABLE repair_order
  ADD COLUMN sla_reminder_sent_at DATETIME DEFAULT NULL COMMENT '临近截止提醒时间',
  ADD COLUMN sla_escalated_at DATETIME DEFAULT NULL COMMENT '超时升级通知时间';

ALTER TABLE utility_bill
  ADD COLUMN anomaly_status VARCHAR(20) DEFAULT NULL,
  ADD COLUMN anomaly_note VARCHAR(500) DEFAULT NULL,
  ADD COLUMN anomaly_repair_id BIGINT DEFAULT NULL,
  ADD COLUMN anomaly_updated_by BIGINT DEFAULT NULL,
  ADD COLUMN anomaly_updated_at DATETIME DEFAULT NULL;

ALTER TABLE dorm_adjust_apply
  ADD COLUMN checkout_checklist TEXT DEFAULT NULL COMMENT '退宿交接检查记录(JSON)';

CREATE TABLE IF NOT EXISTS residence_request (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  student_id BIGINT NOT NULL, room_id BIGINT NOT NULL,
  request_type VARCHAR(20) NOT NULL, from_date DATE DEFAULT NULL, to_date DATE DEFAULT NULL,
  reason VARCHAR(500) NOT NULL, status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  reviewer_id BIGINT DEFAULT NULL, review_comment VARCHAR(500) DEFAULT NULL,
  created_at DATETIME NOT NULL, reviewed_at DATETIME DEFAULT NULL,
  KEY idx_residence_student (student_id, status), KEY idx_residence_room (room_id, status)
) ENGINE=InnoDB COMMENT='入住和假期留宿申请';
