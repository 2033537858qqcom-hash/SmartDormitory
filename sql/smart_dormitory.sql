-- =========================================================================
-- 《基于Java的智慧宿舍管理系统》 数据库初始化脚本 (MySQL 8.0)
-- Database: smart_dormitory
-- =========================================================================

DROP DATABASE IF EXISTS `smart_dormitory`;
CREATE DATABASE `smart_dormitory` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `smart_dormitory`;

-- -------------------------------------------------------------------------
-- 1. 系统用户表 (sys_user)
-- 角色 role: ADMIN-系统管理员, DORM_MANAGER-宿管员, REPAIRMAN-维修工, STUDENT-学生
-- -------------------------------------------------------------------------
CREATE TABLE `sys_user` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `username` VARCHAR(50) NOT NULL UNIQUE COMMENT '登录账号(学号/工号)',
    `password` VARCHAR(100) NOT NULL DEFAULT '$2b$10$JOARIO1GOwvHQaXkicLM6Ok9ONNiQ68Z5Tn5ZNULTkdi//6ls6r2i' COMMENT '登录密码(BCrypt)',
    `real_name` VARCHAR(50) NOT NULL COMMENT '真实姓名',
    `role` VARCHAR(20) NOT NULL COMMENT '角色: ADMIN, DORM_MANAGER, REPAIRMAN, STUDENT',
    `gender` TINYINT DEFAULT 1 COMMENT '性别: 1-男, 2-女',
    `phone` VARCHAR(20) DEFAULT NULL COMMENT '联系电话',
    `email` VARCHAR(60) DEFAULT NULL COMMENT '电子邮箱',
    `avatar` VARCHAR(255) DEFAULT NULL COMMENT '头像URL',
    `college` VARCHAR(60) DEFAULT NULL COMMENT '所属学院(学生专属)',
    `major` VARCHAR(60) DEFAULT NULL COMMENT '专业名称(学生专属)',
    `class_name` VARCHAR(50) DEFAULT NULL COMMENT '行政班级(学生专属)',
    `grade` VARCHAR(20) DEFAULT NULL COMMENT '入学年级(如2023级)',
    `repair_specialty` VARCHAR(30) DEFAULT NULL COMMENT '维修工种(维修工专属: ELECTRIC-电工, WATER-水暖工, WOOD-木工, NETWORK-网络空调)',
    `status` TINYINT DEFAULT 1 COMMENT '状态: 1-正常, 0-禁用',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB COMMENT='系统用户表';

-- -------------------------------------------------------------------------
-- 2. 宿舍楼栋表 (dorm_building)
-- -------------------------------------------------------------------------
CREATE TABLE `dorm_building` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '楼栋ID',
    `building_code` VARCHAR(20) NOT NULL UNIQUE COMMENT '楼栋编号(如 A1, B2)',
    `building_name` VARCHAR(50) NOT NULL COMMENT '楼栋名称(如 紫荆公寓1号楼)',
    `gender_type` TINYINT NOT NULL DEFAULT 1 COMMENT '性别限制: 1-男生公寓, 2-女生公寓',
    `total_floors` INT NOT NULL DEFAULT 6 COMMENT '总楼层数',
    `manager_id` BIGINT DEFAULT NULL COMMENT '负责宿管员ID(关联sys_user)',
    `location_desc` VARCHAR(150) DEFAULT NULL COMMENT '地理位置描述',
    `remark` VARCHAR(255) DEFAULT NULL COMMENT '备注说明',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) ENGINE=InnoDB COMMENT='宿舍楼栋表';

-- -------------------------------------------------------------------------
-- 3. 宿舍房间表 (dorm_room)
-- -------------------------------------------------------------------------
CREATE TABLE `dorm_room` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '房间ID',
    `building_id` BIGINT NOT NULL COMMENT '所属楼栋ID',
    `floor_no` INT NOT NULL COMMENT '所在楼层',
    `room_no` VARCHAR(20) NOT NULL COMMENT '房间号(如 301)',
    `capacity` INT NOT NULL DEFAULT 4 COMMENT '床位容量(4人间/6人间)',
    `occupied_count` INT NOT NULL DEFAULT 0 COMMENT '当前已入住人数',
    `electric_balance` DECIMAL(10, 2) NOT NULL DEFAULT 100.00 COMMENT '电费账户余额(元)',
    `water_balance` DECIMAL(10, 2) NOT NULL DEFAULT 50.00 COMMENT '水费账户余额(元)',
    `harmony_score` DECIMAL(5, 2) DEFAULT 0.00 COMMENT '智能分寝室友契合度评分(0-100)',
    `civilized_score` DECIMAL(5, 2) DEFAULT 90.00 COMMENT '文明寝室综合量化得分',
    `star_level` INT DEFAULT 4 COMMENT '文明星级(1-5星)',
    `status` TINYINT DEFAULT 1 COMMENT '状态: 1-正常可用, 2-维修停用',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY `uk_building_room` (`building_id`, `room_no`)
) ENGINE=InnoDB COMMENT='宿舍房间表';

-- -------------------------------------------------------------------------
-- 4. 宿舍床位表 (dorm_bed)
-- -------------------------------------------------------------------------
CREATE TABLE `dorm_bed` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '床位ID',
    `room_id` BIGINT NOT NULL COMMENT '所属房间ID',
    `bed_no` INT NOT NULL COMMENT '床位编号(1~6号床)',
    `student_id` BIGINT DEFAULT NULL COMMENT '入住学生ID(关联sys_user.id, 空表示未入住)',
    `check_in_date` DATE DEFAULT NULL COMMENT '入住日期',
    `status` TINYINT DEFAULT 0 COMMENT '状态: 0-空闲, 1-已入住, 2-锁定/损坏',
    UNIQUE KEY `uk_room_bed` (`room_id`, `bed_no`)
) ENGINE=InnoDB COMMENT='宿舍床位表';

-- -------------------------------------------------------------------------
-- 5. 学生生活习惯画像表 (student_habit_profile) -> 支撑【智能分寝算法】
-- -------------------------------------------------------------------------
CREATE TABLE `student_habit_profile` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `student_id` BIGINT NOT NULL UNIQUE COMMENT '学生ID(关联sys_user.id)',
    `schedule_score` INT NOT NULL DEFAULT 3 COMMENT '作息时间(1:早睡22点前 ~ 5:晚睡24点后)',
    `sleep_depth_score` INT NOT NULL DEFAULT 3 COMMENT '睡眠质量(1:深睡不怕吵 ~ 5:浅眠易醒需绝对安静)',
    `ac_temp_score` INT NOT NULL DEFAULT 3 COMMENT '空调偏好(1:喜冷20℃ ~ 5:怕冷27℃+)',
    `cleanliness_score` INT NOT NULL DEFAULT 3 COMMENT '卫生习惯(1:随性 ~ 5:重度洁癖每日清扫)',
    `atmosphere_score` INT NOT NULL DEFAULT 3 COMMENT '氛围偏好(1:安静自习室型 ~ 5:电竞娱乐社交型)',
    `odor_sensitive_score` INT NOT NULL DEFAULT 5 COMMENT '烟味/异味敏感度(1:不敏感 ~ 5:极度敏感禁烟)',
    `hobby_tags` VARCHAR(120) DEFAULT NULL COMMENT '个人兴趣标签(如 考研,篮球,吉他,二次元)',
    `is_allocated` TINYINT DEFAULT 0 COMMENT '是否已分配床位: 0-待分配, 1-已分配',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '画像更新时间'
) ENGINE=InnoDB COMMENT='学生六维生活习惯画像表';

-- -------------------------------------------------------------------------
-- 6. 智能分寝算法执行批次记录表 (dorm_allocation_log)
-- -------------------------------------------------------------------------
CREATE TABLE `dorm_allocation_log` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '批次ID',
    `batch_no` VARCHAR(40) NOT NULL COMMENT '算法执行批次号',
    `building_id` BIGINT NOT NULL COMMENT '目标分配楼栋ID',
    `allocated_student_count` INT NOT NULL COMMENT '本次成功分配学生数',
    `allocated_room_count` INT NOT NULL COMMENT '涉及寝室数量',
    `avg_harmony_score` DECIMAL(5, 2) NOT NULL COMMENT '全体平均契合度得分',
    `algorithm_strategy` VARCHAR(60) DEFAULT 'WEIGHTED_EUCLIDEAN_KMEANS' COMMENT '采用算法策略',
    `detail_json` TEXT DEFAULT NULL COMMENT '分配结果快照(JSON)',
    `operator_id` BIGINT DEFAULT NULL COMMENT '执行管理员ID',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '执行时间'
) ENGINE=InnoDB COMMENT='智能分寝算法执行记录表';

-- -------------------------------------------------------------------------
-- 7. 调宿与退宿申请表 (dorm_adjust_apply)
-- -------------------------------------------------------------------------
CREATE TABLE `dorm_adjust_apply` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '申请ID',
    `student_id` BIGINT NOT NULL COMMENT '申请学生ID',
    `apply_type` VARCHAR(20) NOT NULL DEFAULT 'ADJUST' COMMENT '类型: ADJUST-调换宿舍, CHECKOUT-退宿离校',
    `old_room_id` BIGINT NOT NULL COMMENT '原房间ID',
    `old_bed_no` INT NOT NULL COMMENT '原床位号',
    `target_room_id` BIGINT DEFAULT NULL COMMENT '目标房间ID(退宿时为空)',
    `target_bed_no` INT DEFAULT NULL COMMENT '目标床位号',
    `reason` VARCHAR(255) NOT NULL COMMENT '申请原因說明',
    `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING-待审批, APPROVED-已通过, REJECTED-已驳回',
    `reviewer_id` BIGINT DEFAULT NULL COMMENT '审批人ID',
    `review_comment` VARCHAR(200) DEFAULT NULL COMMENT '审批意见',
    `review_time` DATETIME DEFAULT NULL COMMENT '审批时间',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间'
) ENGINE=InnoDB COMMENT='调宿与退宿申请表';

-- -------------------------------------------------------------------------
-- 8. 智能报修工单表 (repair_order) -> 支撑【智能优先级识别与负载均衡派单】
-- -------------------------------------------------------------------------
CREATE TABLE `residence_request` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `student_id` BIGINT NOT NULL,
    `room_id` BIGINT NOT NULL,
    `request_type` VARCHAR(20) NOT NULL COMMENT 'CHECKIN/HOLIDAY',
    `from_date` DATE DEFAULT NULL,
    `to_date` DATE DEFAULT NULL,
    `reason` VARCHAR(500) NOT NULL,
    `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    `reviewer_id` BIGINT DEFAULT NULL,
    `review_comment` VARCHAR(500) DEFAULT NULL,
    `created_at` DATETIME NOT NULL,
    `reviewed_at` DATETIME DEFAULT NULL,
    KEY `idx_residence_student` (`student_id`, `status`),
    KEY `idx_residence_room` (`room_id`, `status`)
) ENGINE=InnoDB COMMENT='入住和假期留宿申请';

CREATE TABLE `repair_order` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '工单ID',
    `order_no` VARCHAR(30) NOT NULL UNIQUE COMMENT '报修单号',
    `student_id` BIGINT NOT NULL COMMENT '报修学生ID',
    `building_id` BIGINT NOT NULL COMMENT '所属楼栋ID',
    `room_id` BIGINT NOT NULL COMMENT '所属房间ID',
    `repair_type` VARCHAR(20) NOT NULL COMMENT '故障分类: ELECTRIC-电力照明, WATER-水暖卫浴, WOOD-门窗床桌, NETWORK-网络空调',
    `title` VARCHAR(80) NOT NULL COMMENT '故障简述标题',
    `description` VARCHAR(500) NOT NULL COMMENT '故障详细描述',
    `priority` VARCHAR(15) NOT NULL DEFAULT 'NORMAL' COMMENT '智能识别紧急度: HIGH-高危紧急, NORMAL-普通, LOW-低优',
    `assigned_worker_id` BIGINT DEFAULT NULL COMMENT '智能调度指派的维修工ID',
    `dispatch_remark` VARCHAR(150) DEFAULT NULL COMMENT '智能调度说明(如: 匹配电工+当前待办最少)',
    `status` VARCHAR(20) NOT NULL DEFAULT 'ASSIGNED' COMMENT '状态: PENDING-待派单, ASSIGNED-已指派待维修, PROCESSING-维修中, COMPLETED-已完成, RATED-已评价',
    `repair_result` VARCHAR(255) DEFAULT NULL COMMENT '维修处理结果说明',
    `rating` INT DEFAULT NULL COMMENT '学生满意度评分(1-5星)',
    `rating_comment` VARCHAR(200) DEFAULT NULL COMMENT '学生评价内容',
    `finish_time` DATETIME DEFAULT NULL COMMENT '完成时间',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '报修时间',
    `photo_urls` TEXT DEFAULT NULL COMMENT '故障图片(JSON数组，本地路径或dataURL演示)',
    `due_at` DATETIME DEFAULT NULL COMMENT 'SLA截止时间(高危4h/普通24h/低优48h)',
    `sla_reminder_sent_at` DATETIME DEFAULT NULL COMMENT '临近截止提醒时间',
    `sla_escalated_at` DATETIME DEFAULT NULL COMMENT '超时升级通知时间'
) ENGINE=InnoDB COMMENT='智能报修工单表';

-- -------------------------------------------------------------------------
-- 9. 水电抄表与智能账单表 (utility_bill) -> 支撑【阶梯计价与突增能耗预警】
-- -------------------------------------------------------------------------
CREATE TABLE `utility_bill` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '账单ID',
    `bill_month` VARCHAR(10) NOT NULL COMMENT '账单月份(如 2026-09)',
    `building_id` BIGINT NOT NULL COMMENT '楼栋ID',
    `room_id` BIGINT NOT NULL COMMENT '房间ID',
    `electric_used` DECIMAL(8, 2) NOT NULL DEFAULT 0.00 COMMENT '本月用电量(度/kWh)',
    `electric_cost` DECIMAL(8, 2) NOT NULL DEFAULT 0.00 COMMENT '阶梯核算电费(元)',
    `water_used` DECIMAL(8, 2) NOT NULL DEFAULT 0.00 COMMENT '本月用水量(吨/m³)',
    `water_cost` DECIMAL(8, 2) NOT NULL DEFAULT 0.00 COMMENT '阶梯核算水费(元)',
    `total_cost` DECIMAL(8, 2) NOT NULL DEFAULT 0.00 COMMENT '本月应缴总金额(元)',
    `abnormal_flag` TINYINT DEFAULT 0 COMMENT '智能能耗异常标识: 0-正常, 1-突增异常预警',
    `abnormal_reason` VARCHAR(200) DEFAULT NULL COMMENT '异常预警原因(如 超出楼栋均值1.6倍，疑似违规电器或漏水)',
    `anomaly_status` VARCHAR(20) DEFAULT NULL COMMENT 'PENDING/INVESTIGATING/RESOLVED',
    `anomaly_note` VARCHAR(500) DEFAULT NULL COMMENT '异常排查记录',
    `anomaly_repair_id` BIGINT DEFAULT NULL COMMENT '关联排查工单',
    `anomaly_updated_by` BIGINT DEFAULT NULL COMMENT '更新人',
    `anomaly_updated_at` DATETIME DEFAULT NULL COMMENT '更新时间',
    `pay_status` TINYINT DEFAULT 0 COMMENT '缴费状态: 0-未缴清, 1-已缴清',
    `pay_time` DATETIME DEFAULT NULL COMMENT '缴费时间',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '出账时间',
    UNIQUE KEY `uk_month_room` (`bill_month`, `room_id`)
) ENGINE=InnoDB COMMENT='水电费阶梯计价与能耗预警表';

-- -------------------------------------------------------------------------
-- 10. 卫生检查评比表 (hygiene_inspection) -> 支撑【多维文明寝室量化评比】
-- -------------------------------------------------------------------------
CREATE TABLE `hygiene_inspection` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '检查ID',
    `check_week` VARCHAR(20) NOT NULL COMMENT '检查周次(如 第7周)',
    `check_date` DATE NOT NULL COMMENT '检查日期',
    `building_id` BIGINT NOT NULL COMMENT '楼栋ID',
    `room_id` BIGINT NOT NULL COMMENT '房间ID',
    `floor_score` INT NOT NULL DEFAULT 25 COMMENT '地面阳台整洁分(满分25)',
    `bed_score` INT NOT NULL DEFAULT 25 COMMENT '床铺被褥内务分(满分25)',
    `desk_score` INT NOT NULL DEFAULT 25 COMMENT '书桌物品摆放分(满分25)',
    `safety_score` INT NOT NULL DEFAULT 25 COMMENT '用电与消防安全分(满分25)',
    `total_score` INT NOT NULL DEFAULT 100 COMMENT '卫生总分(0-100)',
    `grade` VARCHAR(10) NOT NULL DEFAULT '优秀' COMMENT '评级: 优秀, 良好, 合格, 不合格',
    `remark` VARCHAR(200) DEFAULT NULL COMMENT '扣分说明或表扬备注',
    `inspector_id` BIGINT DEFAULT NULL COMMENT '检查宿管员ID',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '录入时间',
    `photo_urls` TEXT DEFAULT NULL COMMENT '现场检查照片(JSON数组，本地路径或dataURL演示)'
) ENGINE=InnoDB COMMENT='宿舍卫生检查评比表';

-- -------------------------------------------------------------------------
-- 11. 晚归与违纪记录表 (discipline_record)
-- -------------------------------------------------------------------------
CREATE TABLE `discipline_record` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '记录ID',
    `student_id` BIGINT NOT NULL COMMENT '违纪学生ID',
    `room_id` BIGINT NOT NULL COMMENT '所属房间ID',
    `record_type` VARCHAR(20) NOT NULL COMMENT '类型: LATE_RETURN-晚归, NO_RETURN-夜不归宿, ILLEGAL_APPLIANCE-违规电器, NOISE-喧哗扰民',
    `deduct_score` INT NOT NULL DEFAULT 5 COMMENT '扣除文明寝室量化分值',
    `occur_time` DATETIME NOT NULL COMMENT '发生时间',
    `description` VARCHAR(255) NOT NULL COMMENT '事件详细说明',
    `status` TINYINT DEFAULT 1 COMMENT '状态: 1-已生效, 2-申诉撤销, 3-申诉待审',
    `recorder_id` BIGINT DEFAULT NULL COMMENT '登记人ID',
    `appeal_proof_type` VARCHAR(50) DEFAULT NULL COMMENT '申诉证明类型',
    `appeal_witness` VARCHAR(50) DEFAULT NULL COMMENT '证明人',
    `appeal_attachments` TEXT DEFAULT NULL COMMENT '申诉附件占位(JSON数组)',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '登记时间'
) ENGINE=InnoDB COMMENT='学生晚归与违纪记录表';

-- -------------------------------------------------------------------------
-- 12. 外来访客登记表 (visitor_record)
-- -------------------------------------------------------------------------
CREATE TABLE `visitor_record` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '访客ID',
    `visitor_name` VARCHAR(50) NOT NULL COMMENT '访客姓名',
    `visitor_phone` VARCHAR(20) NOT NULL COMMENT '访客电话',
    `id_card_mask` VARCHAR(30) DEFAULT NULL COMMENT '身份证号(脱敏)',
    `visit_student_id` BIGINT NOT NULL COMMENT '被访学生ID',
    `building_id` BIGINT NOT NULL COMMENT '访问楼栋ID',
    `room_no` VARCHAR(20) NOT NULL COMMENT '访问房间号',
    `relation` VARCHAR(30) NOT NULL DEFAULT '家长' COMMENT '与被访人关系(家长/同学/快递维修)',
    `visit_reason` VARCHAR(150) NOT NULL COMMENT '来访事由',
    `enter_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '进入时间',
    `leave_time` DATETIME DEFAULT NULL COMMENT '离开时间',
    `status` TINYINT DEFAULT 0 COMMENT '状态: 0-在访中, 1-已签离'
) ENGINE=InnoDB COMMENT='外来访客登记表';

-- -------------------------------------------------------------------------
-- 13. 系统公告通知表 (sys_notice)
-- -------------------------------------------------------------------------
CREATE TABLE `sys_notice` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '公告ID',
    `title` VARCHAR(100) NOT NULL COMMENT '公告标题',
    `content` TEXT NOT NULL COMMENT '公告正文内容',
    `notice_type` VARCHAR(20) NOT NULL DEFAULT 'SYSTEM' COMMENT '类型: SYSTEM-园区通知, HYGIENE-卫生红黑榜, SAFETY-安全预警',
    `target_role` VARCHAR(20) DEFAULT 'ALL' COMMENT '目标受众: ALL-全体, STUDENT-学生, DORM_MANAGER-宿管',
    `publisher_name` VARCHAR(50) DEFAULT '后勤宿管中心' COMMENT '发布部门/人',
    `is_top` TINYINT DEFAULT 0 COMMENT '是否置顶: 0-否, 1-是',
    `view_count` INT DEFAULT 0 COMMENT '阅读次数',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间'
) ENGINE=InnoDB COMMENT='系统通知公告表';


-- =========================================================================
-- 初始化仿真业务数据（确保答辩演示效果饱满、图表丰富）
-- =========================================================================

-- 1. 插入管理员、宿管员、维修工账号 (演示明文口令 123456，库内存 BCrypt)
INSERT INTO `sys_user` (`id`, `username`, `password`, `real_name`, `role`, `gender`, `phone`, `email`, `repair_specialty`) VALUES
(1, 'admin', '$2b$10$JOARIO1GOwvHQaXkicLM6Ok9ONNiQ68Z5Tn5ZNULTkdi//6ls6r2i', '林志远(后勤主任)', 'ADMIN', 1, '13800138000', 'admin@edu.cn', NULL),
(2, 'dm01', '$2b$10$JOARIO1GOwvHQaXkicLM6Ok9ONNiQ68Z5Tn5ZNULTkdi//6ls6r2i', '张桂芳(紫荆1号楼宿管)', 'DORM_MANAGER', 2, '13910002001', 'dm01@edu.cn', NULL),
(3, 'dm02', '$2b$10$JOARIO1GOwvHQaXkicLM6Ok9ONNiQ68Z5Tn5ZNULTkdi//6ls6r2i', '王建国(紫荆2号楼宿管)', 'DORM_MANAGER', 1, '13910002002', 'dm02@edu.cn', NULL),
(4, 'dm03', '$2b$10$JOARIO1GOwvHQaXkicLM6Ok9ONNiQ68Z5Tn5ZNULTkdi//6ls6r2i', '李淑芬(丁香1号楼宿管)', 'DORM_MANAGER', 2, '13910002003', 'dm03@edu.cn', NULL),
(5, 'worker01', '$2b$10$JOARIO1GOwvHQaXkicLM6Ok9ONNiQ68Z5Tn5ZNULTkdi//6ls6r2i', '赵铁柱(高级电工)', 'REPAIRMAN', 1, '13766661001', 'w01@edu.cn', 'ELECTRIC'),
(6, 'worker02', '$2b$10$JOARIO1GOwvHQaXkicLM6Ok9ONNiQ68Z5Tn5ZNULTkdi//6ls6r2i', '孙大伟(水暖工程师)', 'REPAIRMAN', 1, '13766661002', 'w02@edu.cn', 'WATER'),
(7, 'worker03', '$2b$10$JOARIO1GOwvHQaXkicLM6Ok9ONNiQ68Z5Tn5ZNULTkdi//6ls6r2i', '周木生(木工师傅)', 'REPAIRMAN', 1, '13766661003', 'w03@edu.cn', 'WOOD'),
(8, 'worker04', '$2b$10$JOARIO1GOwvHQaXkicLM6Ok9ONNiQ68Z5Tn5ZNULTkdi//6ls6r2i', '吴网通(网络与空调工)', 'REPAIRMAN', 1, '13766661004', 'w04@edu.cn', 'NETWORK'),
(9, 'worker05', '$2b$10$JOARIO1GOwvHQaXkicLM6Ok9ONNiQ68Z5Tn5ZNULTkdi//6ls6r2i', '郑光明(值班电工)', 'REPAIRMAN', 1, '13766661005', 'w05@edu.cn', 'ELECTRIC'),
(29, 'counselor01', '$2b$10$JOARIO1GOwvHQaXkicLM6Ok9ONNiQ68Z5Tn5ZNULTkdi//6ls6r2i', '陈晓雯(计科辅导员)', 'COUNSELOR', 2, '13688880001', 'counselor01@edu.cn', NULL);

-- 2. 插入学生账号 (10~25: 已入住学生与待智能分寝新生)
INSERT INTO `sys_user` (`id`, `username`, `password`, `real_name`, `role`, `gender`, `phone`, `college`, `major`, `class_name`, `grade`) VALUES
-- 已入住 紫荆1号楼 301 室 (学霸早睡型寝室)
(10, '20230101', '$2b$10$JOARIO1GOwvHQaXkicLM6Ok9ONNiQ68Z5Tn5ZNULTkdi//6ls6r2i', '陈嘉豪', 'STUDENT', 1, '15811110001', '计算机科学与技术学院', '软件工程', '软工2301班', '2023级'),
(11, '20230102', '$2b$10$JOARIO1GOwvHQaXkicLM6Ok9ONNiQ68Z5Tn5ZNULTkdi//6ls6r2i', '林宇轩', 'STUDENT', 1, '15811110002', '计算机科学与技术学院', '软件工程', '软工2301班', '2023级'),
(12, '20230103', '$2b$10$JOARIO1GOwvHQaXkicLM6Ok9ONNiQ68Z5Tn5ZNULTkdi//6ls6r2i', '张明哲', 'STUDENT', 1, '15811110003', '计算机科学与技术学院', '软件工程', '软工2301班', '2023级'),
(13, '20230104', '$2b$10$JOARIO1GOwvHQaXkicLM6Ok9ONNiQ68Z5Tn5ZNULTkdi//6ls6r2i', '黄浩然', 'STUDENT', 1, '15811110004', '计算机科学与技术学院', '软件工程', '软工2301班', '2023级'),
-- 已入住 紫荆1号楼 302 室 (电竞活跃型寝室)
(14, '20230105', '$2b$10$JOARIO1GOwvHQaXkicLM6Ok9ONNiQ68Z5Tn5ZNULTkdi//6ls6r2i', '刘子墨', 'STUDENT', 1, '15811110005', '计算机科学与技术学院', '计算机科学', '计科2301班', '2023级'),
(15, '20230106', '$2b$10$JOARIO1GOwvHQaXkicLM6Ok9ONNiQ68Z5Tn5ZNULTkdi//6ls6r2i', '徐梓轩', 'STUDENT', 1, '15811110006', '计算机科学与技术学院', '计算机科学', '计科2301班', '2023级'),
(16, '20230107', '$2b$10$JOARIO1GOwvHQaXkicLM6Ok9ONNiQ68Z5Tn5ZNULTkdi//6ls6r2i', '马天佑', 'STUDENT', 1, '15811110007', '计算机科学与技术学院', '计算机科学', '计科2301班', '2023级'),
-- 已入住 丁香1号楼 201 室 (女生文明示范寝室)
(17, '20230201', '$2b$10$JOARIO1GOwvHQaXkicLM6Ok9ONNiQ68Z5Tn5ZNULTkdi//6ls6r2i', '苏婉清', 'STUDENT', 2, '15822220001', '经济与管理学院', '会计学', '会计2301班', '2023级'),
(18, '20230202', '$2b$10$JOARIO1GOwvHQaXkicLM6Ok9ONNiQ68Z5Tn5ZNULTkdi//6ls6r2i', '沈星辰', 'STUDENT', 2, '15822220002', '经济与管理学院', '会计学', '会计2301班', '2023级'),
(19, '20230203', '$2b$10$JOARIO1GOwvHQaXkicLM6Ok9ONNiQ68Z5Tn5ZNULTkdi//6ls6r2i', '宋雨琦', 'STUDENT', 2, '15822220003', '经济与管理学院', '财务管理', '财管2301班', '2023级'),
(20, '20230204', '$2b$10$JOARIO1GOwvHQaXkicLM6Ok9ONNiQ68Z5Tn5ZNULTkdi//6ls6r2i', '叶知秋', 'STUDENT', 2, '15822220004', '经济与管理学院', '财务管理', '财管2301班', '2023级'),
-- 待分配床位的 8 名新生 (用于演示【一键智能算法分寝】功能！分为明显的两类人群：4名早睡考研型、4名晚睡电竞型)
(21, '20260101', '$2b$10$JOARIO1GOwvHQaXkicLM6Ok9ONNiQ68Z5Tn5ZNULTkdi//6ls6r2i', '陆景行', 'STUDENT', 1, '15933330001', '人工智能学院', '人工智能', 'AI2601班', '2026级'),
(22, '20260102', '$2b$10$JOARIO1GOwvHQaXkicLM6Ok9ONNiQ68Z5Tn5ZNULTkdi//6ls6r2i', '顾言深', 'STUDENT', 1, '15933330002', '人工智能学院', '人工智能', 'AI2601班', '2026级'),
(23, '20260103', '$2b$10$JOARIO1GOwvHQaXkicLM6Ok9ONNiQ68Z5Tn5ZNULTkdi//6ls6r2i', '江屿白', 'STUDENT', 1, '15933330003', '人工智能学院', '人工智能', 'AI2601班', '2026级'),
(24, '20260104', '$2b$10$JOARIO1GOwvHQaXkicLM6Ok9ONNiQ68Z5Tn5ZNULTkdi//6ls6r2i', '周叙白', 'STUDENT', 1, '15933330004', '人工智能学院', '人工智能', 'AI2601班', '2026级'),
(25, '20260105', '$2b$10$JOARIO1GOwvHQaXkicLM6Ok9ONNiQ68Z5Tn5ZNULTkdi//6ls6r2i', '韩烁', 'STUDENT', 1, '15933330005', '人工智能学院', '智能科学', '智科2601班', '2026级'),
(26, '20260106', '$2b$10$JOARIO1GOwvHQaXkicLM6Ok9ONNiQ68Z5Tn5ZNULTkdi//6ls6r2i', '谢危', 'STUDENT', 1, '15933330006', '人工智能学院', '智能科学', '智科2601班', '2026级'),
(27, '20260107', '$2b$10$JOARIO1GOwvHQaXkicLM6Ok9ONNiQ68Z5Tn5ZNULTkdi//6ls6r2i', '萧蘅', 'STUDENT', 1, '15933330007', '人工智能学院', '智能科学', '智科2601班', '2026级'),
(28, '20260108', '$2b$10$JOARIO1GOwvHQaXkicLM6Ok9ONNiQ68Z5Tn5ZNULTkdi//6ls6r2i', '范闲', 'STUDENT', 1, '15933330008', '人工智能学院', '智能科学', '智科2601班', '2026级');

-- 3. 插入楼栋数据
INSERT INTO `dorm_building` (`id`, `building_code`, `building_name`, `gender_type`, `total_floors`, `manager_id`, `location_desc`, `remark`) VALUES
(1, 'A1', '紫荆公寓1号楼', 1, 6, 2, '东区运动场北侧，毗邻第一食堂', '标准四人间上床下桌，配备独立卫浴与空调'),
(2, 'A2', '紫荆公寓2号楼', 1, 6, 3, '东区运动场东侧，毗邻计算机学院楼', '新生智慧示范公寓，配备智能电控表'),
(3, 'B1', '丁香公寓1号楼', 2, 6, 4, '西区湖畔南侧，毗邻图书馆与第二食堂', '女生精品四人间，门禁人脸识别覆盖');

-- 4. 插入宿舍房间数据
INSERT INTO `dorm_room` (`id`, `building_id`, `floor_no`, `room_no`, `capacity`, `occupied_count`, `electric_balance`, `water_balance`, `harmony_score`, `civilized_score`, `star_level`, `status`) VALUES
(1, 1, 3, '301', 4, 4, 142.50, 68.00, 95.50, 96.50, 5, 1),
(2, 1, 3, '302', 4, 3, 18.20, 32.50, 88.00, 74.00, 3, 1),
(3, 1, 3, '303', 4, 0, 100.00, 50.00, 0.00, 90.00, 4, 1),
(4, 1, 3, '304', 4, 0, 100.00, 50.00, 0.00, 90.00, 4, 1),
(5, 2, 2, '201', 4, 0, 100.00, 50.00, 0.00, 90.00, 4, 1),
(6, 2, 2, '202', 4, 0, 100.00, 50.00, 0.00, 90.00, 4, 1),
(7, 3, 2, '201', 4, 4, 186.00, 82.00, 94.00, 98.00, 5, 1),
(8, 3, 2, '202', 4, 0, 100.00, 50.00, 0.00, 90.00, 4, 1);

-- 5. 插入床位数据
INSERT INTO `dorm_bed` (`room_id`, `bed_no`, `student_id`, `check_in_date`, `status`) VALUES
-- A1-301 (4人满)
(1, 1, 10, '2023-09-01', 1), (1, 2, 11, '2023-09-01', 1), (1, 3, 12, '2023-09-01', 1), (1, 4, 13, '2023-09-01', 1),
-- A1-302 (3人入住，4号床空余，可供调宿申请演示)
(2, 1, 14, '2023-09-01', 1), (2, 2, 15, '2023-09-01', 1), (2, 3, 16, '2023-09-01', 1), (2, 4, NULL, NULL, 0),
-- A1-303 (空房，供智能分寝演示)
(3, 1, NULL, NULL, 0), (3, 2, NULL, NULL, 0), (3, 3, NULL, NULL, 0), (3, 4, NULL, NULL, 0),
-- A1-304 (空房，供智能分寝演示)
(4, 1, NULL, NULL, 0), (4, 2, NULL, NULL, 0), (4, 3, NULL, NULL, 0), (4, 4, NULL, NULL, 0),
-- A2-201 (空房)
(5, 1, NULL, NULL, 0), (5, 2, NULL, NULL, 0), (5, 3, NULL, NULL, 0), (5, 4, NULL, NULL, 0),
-- A2-202 (空房)
(6, 1, NULL, NULL, 0), (6, 2, NULL, NULL, 0), (6, 3, NULL, NULL, 0), (6, 4, NULL, NULL, 0),
-- B1-201 (女生4人满)
(7, 1, 17, '2023-09-01', 1), (7, 2, 18, '2023-09-01', 1), (7, 3, 19, '2023-09-01', 1), (7, 4, 20, '2023-09-01', 1),
-- B1-202 (女生空房)
(8, 1, NULL, NULL, 0), (8, 2, NULL, NULL, 0), (8, 3, NULL, NULL, 0), (8, 4, NULL, NULL, 0);

-- 6. 插入学生六维生活习惯画像数据
INSERT INTO `student_habit_profile` (`student_id`, `schedule_score`, `sleep_depth_score`, `ac_temp_score`, `cleanliness_score`, `atmosphere_score`, `odor_sensitive_score`, `hobby_tags`, `is_allocated`) VALUES
-- A1-301 已入住四位同学（高度一致的规律作息自习型）
(10, 1, 4, 3, 5, 1, 5, 'ACM竞赛,早起晨跑,图书馆', 1),
(11, 1, 4, 3, 4, 1, 5, '考研数学,安静阅读,羽毛球', 1),
(12, 2, 3, 3, 5, 2, 5, '开源项目,英语六级,摄影', 1),
(13, 1, 4, 2, 4, 1, 5, '算法刷题,早睡养生,慢跑', 1),
-- A1-302 已入住三位同学（偏晚睡电竞社交型）
(14, 5, 1, 1, 2, 5, 2, '英雄联盟,夜宵局,街舞', 1),
(15, 5, 2, 1, 3, 5, 3, '无畏契约,篮球,说唱音乐', 1),
(16, 4, 1, 2, 2, 4, 2, '主机游戏,动漫,桌游聚会', 1),
-- B1-201 已入住四位女生
(17, 2, 3, 4, 5, 2, 5, '考公备考,插花,烘焙', 1),
(18, 2, 4, 4, 5, 2, 5, 'CPA备考,瑜伽,手账', 1),
(19, 1, 3, 4, 4, 2, 5, '古筝,汉服,图书馆自习', 1),
(20, 2, 3, 3, 5, 1, 5, '金融建模,绘画,早睡', 1),
-- 8名待分配新生（21,23,25,27 是早睡安静学霸型；22,24,26,28 是熬夜开黑电竞型，故意交错学号以体现智能分寝优势！）
(21, 1, 4, 3, 5, 1, 5, '数学建模,早起背单词,安静自习', 0),
(22, 5, 1, 1, 2, 5, 2, '熬夜开黑,王者荣耀,吉他社', 0),
(23, 1, 5, 3, 5, 1, 5, '深度学习科研,重度洁癖,早睡', 0),
(24, 5, 2, 1, 2, 5, 1, 'CS2电竞,夜猫子,篮球爱好者', 0),
(25, 2, 4, 3, 4, 1, 5, '考研保研党,喜静,规律作息', 0),
(26, 4, 1, 2, 3, 5, 2, '乐队贝斯,深夜看球赛,社交达人', 0),
(27, 1, 4, 3, 5, 2, 5, '围棋,古典音乐,每天打扫卫生', 0),
(28, 5, 1, 1, 2, 4, 2, 'Steam全成就党,火锅局,不怕吵', 0);

-- 7. 插入智能分寝历史批次日志
INSERT INTO `dorm_allocation_log` (`batch_no`, `building_id`, `allocated_student_count`, `allocated_room_count`, `avg_harmony_score`, `algorithm_strategy`, `detail_json`, `operator_id`, `create_time`) VALUES
('BATCH-20230901-001', 1, 7, 2, 91.75, '加权欧氏距离+贪心聚类分簇算法', '[{"roomNo":"301","students":["陈嘉豪","林宇轩","张明哲","黄浩然"],"harmonyScore":95.5},{"roomNo":"302","students":["刘子墨","徐梓轩","马天佑"],"harmonyScore":88.0}]', 1, '2023-09-01 10:30:00');

-- 8. 插入调宿与退宿申请数据
INSERT INTO `dorm_adjust_apply` (`student_id`, `apply_type`, `old_room_id`, `old_bed_no`, `target_room_id`, `target_bed_no`, `reason`, `status`, `reviewer_id`, `review_comment`, `review_time`, `create_time`) VALUES
(16, 'ADJUST', 2, 3, 3, 1, '近期准备跨考研究生，原302寝室室友睡眠较晚，申请调入安静空寝或早睡寝室备考。', 'PENDING', NULL, NULL, NULL, '2026-10-25 14:20:00'),
(12, 'ADJUST', 1, 3, 2, 4, '因与计科班同学合作大创项目，曾申请调往302，后协商在实验室办公即可。', 'APPROVED', 1, '经核实双方辅导员同意，予以备案通过。', '2026-09-18 16:00:00', '2026-09-16 09:15:00');

-- 9. 插入智能报修工单数据
INSERT INTO `repair_order` (`order_no`, `student_id`, `building_id`, `room_id`, `repair_type`, `title`, `description`, `priority`, `assigned_worker_id`, `dispatch_remark`, `status`, `repair_result`, `rating`, `rating_comment`, `finish_time`, `create_time`) VALUES
('REP-20261026-001', 14, 1, 2, 'ELECTRIC', '浴室插座跳闸且伴有焦糊味', '昨晚使用吹风机时插座突然火花跳闸，空气开关推不上去，疑似线路短路，急需处理！', 'HIGH', 5, '智能识别高危词[跳闸,短路] -> 提权为HIGH；匹配ELECTRIC工种且负载最小 -> 赵铁柱', 'ASSIGNED', NULL, NULL, NULL, NULL, '2026-10-26 21:15:00'),
('REP-20261025-002', 10, 1, 1, 'WATER', '阳台洗手池下水管轻微渗水', '洗漱时发现洗手盆下方U型管接口处有水滴渗出，用盆接着，希望师傅顺路拧紧或更换垫圈。', 'NORMAL', 6, '智能分类WATER工种 + 最小待办队列匹配 -> 孙大伟', 'PROCESSING', '已到达现场，正在更换老化硅胶密封圈', NULL, NULL, NULL, '2026-10-25 10:40:00'),
('REP-20261020-003', 17, 3, 7, 'WOOD', '2号床铺护栏螺丝松动异响', '翻身时上铺铁艺护栏晃动明显，需要加固膨胀螺丝。', 'NORMAL', 7, '智能分类WOOD工种 + 负载均衡指派 -> 周木生', 'RATED', '已更换高强度内六角螺栓并加固全寝室床架', 5, '周师傅来得非常快，还顺便帮我们修了阳台门合页，五星好评！', '2026-10-20 16:30:00', '2026-10-20 11:05:00'),
('REP-20261018-004', 11, 1, 1, 'NETWORK', '空调制冷效果减弱且内机滴水', '开26度风量很小，且室内机右侧导风板偶尔滴水。', 'NORMAL', 8, '智能分类NETWORK空调工种 + 负载均衡指派 -> 吴网通', 'RATED', '已清洗空调过滤网并疏通冷凝水排水管', 5, '清洗后风力很大，不再滴水了，感谢！', '2026-10-18 17:50:00', '2026-10-18 13:20:00');

-- 10. 插入水电抄表与智能预警账单数据
INSERT INTO `utility_bill` (`bill_month`, `building_id`, `room_id`, `electric_used`, `electric_cost`, `water_used`, `water_cost`, `total_cost`, `abnormal_flag`, `abnormal_reason`, `pay_status`, `pay_time`) VALUES
('2026-08', 1, 1, 68.50, 29.10, 10.20, 18.60, 47.70, 0, NULL, 1, '2026-09-03 12:10:00'),
('2026-08', 1, 2, 112.00, 56.40, 12.50, 25.50, 81.90, 0, NULL, 1, '2026-09-04 19:20:00'),
('2026-08', 3, 7, 62.00, 25.20, 11.00, 21.00, 46.20, 0, NULL, 1, '2026-09-02 15:00:00'),
('2026-09', 1, 1, 54.00, 20.40, 9.50, 16.50, 36.90, 0, NULL, 1, '2026-10-02 10:00:00'),
('2026-09', 1, 2, 198.50, 123.80, 14.00, 30.00, 153.80, 1, '用电量(198.5kWh)超出本楼栋当月均值(82.4kWh)的2.4倍，且环比上月激增77.2%，疑似存在违规大功率电器或空调未关！', 0, NULL),
('2026-09', 3, 7, 51.20, 18.72, 10.40, 19.20, 37.92, 0, NULL, 1, '2026-10-01 20:30:00'),
('2026-10', 1, 1, 48.00, 16.80, 8.80, 14.40, 31.20, 0, NULL, 1, '2026-10-26 09:00:00'),
('2026-10', 1, 2, 175.00, 105.00, 24.50, 61.50, 166.50, 1, '用电量与用水量双项超标预警：用水24.5吨超出均值2.1倍，请宿管核查卫浴是否漏水！', 0, NULL),
('2026-10', 3, 7, 45.00, 15.00, 9.20, 15.60, 30.60, 0, NULL, 1, '2026-10-26 11:45:00');

-- 11. 插入卫生检查记录数据
INSERT INTO `hygiene_inspection` (`check_week`, `check_date`, `building_id`, `room_id`, `floor_score`, `bed_score`, `desk_score`, `safety_score`, `total_score`, `grade`, `remark`, `inspector_id`) VALUES
('第6周', '2026-10-12', 1, 1, 24, 25, 24, 25, 98, '优秀', '桌面书籍摆放整齐，无违章电器，授予流动红旗', 2),
('第6周', '2026-10-12', 1, 2, 18, 20, 16, 20, 74, '合格', '阳台有空饮料瓶堆积，2号床插线板未固定在桌面', 2),
('第6周', '2026-10-12', 3, 7, 25, 25, 25, 24, 99, '优秀', '全楼标杆示范寝室，地面一尘不染', 4),
('第7周', '2026-10-19', 1, 1, 25, 24, 24, 25, 98, '优秀', '保持一贯高水准，卫生极佳', 2),
('第7周', '2026-10-19', 1, 2, 19, 19, 18, 18, 74, '合格', '已清理阳台杂物，但地面仍有外卖包装袋未及时倾倒', 2),
('第7周', '2026-10-19', 3, 7, 24, 25, 25, 25, 99, '优秀', '内务整洁，空气清新', 4);

-- 12. 插入晚归与违纪记录数据
INSERT INTO `discipline_record` (`student_id`, `room_id`, `record_type`, `deduct_score`, `occur_time`, `description`, `status`, `recorder_id`) VALUES
(14, 2, 'LATE_RETURN', 5, '2026-10-15 23:48:00', '超过门禁时间(23:00)晚归，自述在剧本杀店聚会错过末班校车', 1, 2),
(15, 2, 'ILLEGAL_APPLIANCE', 10, '2026-10-21 15:30:00', '安全巡查发现桌下存放1200W电煮锅一个，已暂扣代管并批评教育', 1, 2),
(16, 2, 'LATE_RETURN', 5, '2026-10-24 23:32:00', '[23:00人脸门禁考勤稽核] 超过门禁时间晚归 | [学生申诉] 当晚在计算机学院机房参加ACM程序设计集训，附指导教师签字证明，申请核销扣分', 3, 2);

-- 13. 插入访客登记数据
INSERT INTO `visitor_record` (`visitor_name`, `visitor_phone`, `id_card_mask`, `visit_student_id`, `building_id`, `room_no`, `relation`, `visit_reason`, `enter_time`, `leave_time`, `status`) VALUES
('陈建民', '13609871234', '330106********1512', 10, 1, '301', '家长', '父母周末来校探望并送换季衣物被褥', '2026-10-24 10:15:00', '2026-10-24 11:50:00', 1),
('李师傅(顺丰大件)', '18911223344', '410102********331X', 14, 1, '302', '快递物流', '派送人体工学椅大件上楼安装', '2026-10-27 16:30:00', NULL, 0);

-- 14. 插入系统通知公告
INSERT INTO `sys_notice` (`title`, `content`, `notice_type`, `target_role`, `publisher_name`, `is_top`, `view_count`, `create_time`) VALUES
('关于开展2026年秋季学期“五星文明寝室”量化评比与安全用电专项检查的通知', '全体住宿同学：\n为营造安全、整洁、和谐的智慧公寓环境，系统将于每月1日自动汇总【卫生检查(50%) + 违纪晚归记录(30%) + 水电节约表现(20%)】进行量化评比。请各寝室严禁使用热得快、电煮锅等大功率违规电器，智慧水电监测模块已开启24小时突增能耗预警。', 'SAFETY', 'ALL', '校后勤保障部·宿管中心', 1, 342, '2026-10-10 09:00:00'),
('2026级新生“六维生活习惯画像”问卷采集与智能分寝系统开放公告', '欢迎新同学入住智慧公寓！请尚未分配床位的同学登录个人工作台，如实填写《六维生活习惯画像问卷》（含作息时间、睡眠深浅、空调偏好、卫生标准、氛围偏好、异味敏感度）。后勤中心将通过多维余弦/欧氏聚类算法为您匹配生活习惯最契合的室友！', 'SYSTEM', 'STUDENT', '学生工作处', 1, 518, '2026-10-15 14:00:00'),
('第十四周全校文明示范寝室红榜公示（紫荆1-301、丁香1-201上榜）', '经过本阶段多维量化考核，紫荆公寓1号楼301室（综合得分96.5分）、丁香公寓1号楼201室（综合得分98.0分）获评五星级文明示范寝室，特此通报表扬并奖励免费电费额度50度！', 'HYGIENE', 'ALL', '紫荆园区管委会', 0, 215, '2026-10-22 17:30:00');


-- -------------------------------------------------------------------------
-- 14. 站内消息 (user_message) -> P1 轻量通知中心
-- -------------------------------------------------------------------------
CREATE TABLE `user_message` (
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

-- -------------------------------------------------------------------------
-- 15. 水电演示缴费流水 (utility_payment_log) -> P2 模拟核销凭证
-- -------------------------------------------------------------------------
CREATE TABLE `utility_payment_log` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '流水ID',
    `txn_no` VARCHAR(40) NOT NULL COMMENT '演示流水号 DEMO-...',
    `room_id` BIGINT NOT NULL COMMENT '寝室ID',
    `bill_id` BIGINT DEFAULT NULL COMMENT '关联账单ID(缴费时)',
    `operator_id` BIGINT DEFAULT NULL COMMENT '操作人用户ID',
    `action_type` VARCHAR(20) NOT NULL COMMENT 'RECHARGE / PAY_BILL',
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
) ENGINE=InnoDB COMMENT='水电演示模拟缴费流水';
