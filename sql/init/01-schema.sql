-- ============================================================
-- 建筑学院设备借用系统 - 数据库初始化脚本
-- 数据库: device_borrow
-- ============================================================

CREATE DATABASE IF NOT EXISTS `device_borrow`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE `device_borrow`;

-- -----------------------------------------------------------
-- 1. 用户表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_user` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(50) NOT NULL COMMENT 'CAS学工号/本地登录名',
  `real_name` varchar(50) DEFAULT NULL COMMENT '真实姓名',
  `user_type` tinyint DEFAULT '0' COMMENT '0学生 1教师 2实验室管理员 3系统管理员',
  `department` varchar(100) DEFAULT NULL COMMENT '学院/部门名称(deptName)',
  `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
  `phone` varchar(20) DEFAULT NULL COMMENT '手机号',
  `password` varchar(200) DEFAULT '' COMMENT 'BCrypt密码（仅本地用户）',
  `auth_source` char(1) DEFAULT 'C' COMMENT 'C:CAS L:本地',
  `status` tinyint DEFAULT '1' COMMENT '1正常 0禁用',

  -- === CAS 系统返回字段（仅 auth_source=C 时有值） ===
  `cas_uuid` varchar(64) DEFAULT NULL COMMENT 'CAS系统UUID（全局唯一标识）',
  `acc_no` int DEFAULT NULL COMMENT 'CAS账号编号(accNo)',
  `class_name` varchar(100) DEFAULT NULL COMMENT '班级名称（学生：className；教师：同deptName）',
  `class_id` bigint DEFAULT NULL COMMENT '班级ID(classId)',
  `dept_id` bigint DEFAULT NULL COMMENT '学院/部门ID(deptId)',
  `ident` int DEFAULT NULL COMMENT 'CAS身份标识: 257学生 259教师',
  `card_no` varchar(50) DEFAULT NULL COMMENT '一卡通号(cardNo)',
  `sex` tinyint DEFAULT 0 COMMENT '性别: 0未知 1男 2女',
  `expired_date` int DEFAULT NULL COMMENT 'CAS账号过期日期(expiredDate, 格式yyyyMMdd)',
  `last_cas_login` datetime DEFAULT NULL COMMENT '最近一次CAS登录时间',

  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  UNIQUE KEY `uk_cas_uuid` (`cas_uuid`),
  KEY `idx_user_type` (`user_type`),
  KEY `idx_auth_source` (`auth_source`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- -----------------------------------------------------------
-- 2. 设备分类表（树形业务分类）
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `device_category` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(50) NOT NULL COMMENT '分类名称',
  `code` varchar(50) DEFAULT NULL COMMENT '分类编码（如 COMPUTER, CAMERA）',
  `parent_id` bigint DEFAULT '0' COMMENT '父分类ID（0=一级分类）',
  `level` tinyint DEFAULT '1' COMMENT '层级: 1一级 2二级 3三级',
  `sort` int DEFAULT '0' COMMENT '排序',
  `status` tinyint DEFAULT '1' COMMENT '1启用 0禁用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_parent` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备分类表';

-- -----------------------------------------------------------
-- 3. 设备/资产表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `device` (
  -- === 核心业务字段 ===
  `id` bigint NOT NULL AUTO_INCREMENT,
  `asset_no` varchar(50) DEFAULT NULL COMMENT '资产编号（学校统一编码）',
  `name` varchar(200) NOT NULL COMMENT '设备/资产名称',
  `model` varchar(200) DEFAULT NULL COMMENT '型号/品牌',
  `specs` text COMMENT '规格描述',
  `category_id` bigint DEFAULT NULL COMMENT '业务分类ID（关联device_category）',
  `location` varchar(500) DEFAULT NULL COMMENT '存放地名称',
  `laboratory_id` bigint DEFAULT NULL COMMENT '所属实验室ID',
  `department` varchar(100) DEFAULT NULL COMMENT '使用单位',
  `custodian` varchar(50) DEFAULT NULL COMMENT '使用人姓名',
  `total_qty` int NOT NULL DEFAULT '1' COMMENT '数量',
  `available_qty` int NOT NULL DEFAULT '1' COMMENT '当前可借数量',
  `default_approver_id` bigint DEFAULT NULL COMMENT '默认审批人ID（设备使用人，LAB_ADMIN可修改）',
  `unit_price` decimal(12,2) DEFAULT NULL COMMENT '单价(元)',
  `total_amount` decimal(12,2) DEFAULT NULL COMMENT '金额(元)',
  `status` tinyint DEFAULT '1' COMMENT '【废弃】旧状态字段，V3起使用borrow_status+device_status',
  `borrow_type` tinyint DEFAULT '2' COMMENT '借用类型: 1可现场借用 2可借出（默认）',
  `borrow_status` tinyint DEFAULT '1' COMMENT '借还状态: 1可借用 2借用中 3不可借 4逾期',
  `device_status` tinyint DEFAULT '1' COMMENT '设备物理状态: 1正常 2待维修 3维修中 4待报废 5已报废',
  `description` text COMMENT '设备描述/备注',
  `cover_image` varchar(500) DEFAULT NULL COMMENT '封面图URL',
  `create_by` bigint DEFAULT NULL COMMENT '创建人ID',

  -- === 资产导入字段 ===
  `gb_category_name` varchar(100) DEFAULT NULL COMMENT '国标分类名',
  `gb_category_code` varchar(50) DEFAULT NULL COMMENT '国标分类号',
  `edu_category_name` varchar(100) DEFAULT NULL COMMENT '教育分类名',
  `edu_category_code` varchar(50) DEFAULT NULL COMMENT '教育分类号',
  `purchase_date` date DEFAULT NULL COMMENT '购置日期',
  `manufacturer` varchar(200) DEFAULT NULL COMMENT '厂家/产地',
  `supplier` varchar(200) DEFAULT NULL COMMENT '供货商及电话',
  `invoice_no` varchar(100) DEFAULT NULL COMMENT '发票号',
  `contract_no` varchar(100) DEFAULT NULL COMMENT '合同号',
  `warranty_period` int DEFAULT NULL COMMENT '保修期限(月)',
  `import_batch_id` varchar(50) DEFAULT NULL COMMENT '导入批次号',

  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_asset_no` (`asset_no`),
  KEY `idx_category` (`category_id`),
  KEY `idx_status` (`status`),
  KEY `idx_location` (`location`(100)),
  KEY `idx_name` (`name`(50)),
  KEY `idx_borrow_type` (`borrow_type`),
  KEY `idx_borrow_status` (`borrow_status`),
  KEY `idx_device_status` (`device_status`),
  KEY `idx_laboratory` (`laboratory_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备/资产表';

-- -----------------------------------------------------------
-- 4. 国标→业务分类映射表（自动分类规则）
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `category_mapping` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `gb_category_name` varchar(100) NOT NULL COMMENT '国标分类名',
  `keyword` varchar(100) NOT NULL COMMENT '匹配关键词（用于自动分类的包含匹配）',
  `category_id` bigint NOT NULL COMMENT '映射到的业务分类ID',
  `priority` int DEFAULT '100' COMMENT '优先级（越小越优先，用于冲突消解）',
  `is_active` tinyint DEFAULT '1' COMMENT '1启用 0禁用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_gb_name` (`gb_category_name`),
  KEY `idx_category` (`category_id`),
  KEY `idx_keyword` (`keyword`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='国标→业务分类映射表';

-- -----------------------------------------------------------
-- 5. 设备图片表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `device_image` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `device_id` bigint NOT NULL COMMENT '关联设备',
  `image_url` varchar(500) NOT NULL COMMENT '图片URL',
  `sort` int DEFAULT '0' COMMENT '排序',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_device` (`device_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备图片表';

-- -----------------------------------------------------------
-- 6. 借用单表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `borrow_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '借用人',
  `device_id` bigint NOT NULL COMMENT '设备ID',
  `start_time` datetime NOT NULL COMMENT '借用开始时间',
  `end_time` datetime NOT NULL COMMENT '计划归还时间',
  `status` varchar(20) NOT NULL DEFAULT 'PENDING_APPROVAL' COMMENT '状态: PENDING_APPROVAL/APPROVED/REJECTED/BORROWING/RETURNED/OVERDUE/CANCELLED',
  `reason` text COMMENT '借用事由',
  `purpose` varchar(500) DEFAULT NULL COMMENT '借用目的（V4：申请时必填）',
  `purpose_category` varchar(50) DEFAULT NULL COMMENT '目的大类',
  `purpose_subcategory` varchar(50) DEFAULT NULL COMMENT '目的子分类',
  `approve_flow_def` text COMMENT '审批流定义JSON',
  `current_step` int DEFAULT '0' COMMENT '当前审批步骤',
  `real_return_time` datetime DEFAULT NULL COMMENT '实际归还时间',
  `overdue_days` int DEFAULT '0' COMMENT '逾期天数',
  `damage_report` text COMMENT '损坏报告',
  `outcome` text DEFAULT NULL COMMENT '借用成果（V4：归还时可选/管理员后可补录）',
  `outcome_recorded_by` bigint DEFAULT NULL COMMENT '成果录入人ID',
  `outcome_recorded_time` datetime DEFAULT NULL COMMENT '成果录入时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_device` (`device_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='借用单表';

-- -----------------------------------------------------------
-- 6.5 借用成果表（V4.1新增）
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `borrow_outcome` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `borrow_id` bigint DEFAULT NULL COMMENT '关联借用单ID',
  `device_id` bigint NOT NULL COMMENT '关联设备ID',
  `outcome_type` varchar(50) NOT NULL COMMENT '成果类型',
  `title` varchar(500) DEFAULT NULL COMMENT '成果标题',
  `detail` text COMMENT '成果详情（JSON）',
  `file_urls` text COMMENT '附件URL列表',
  `recorded_by` bigint DEFAULT NULL COMMENT '录入人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_borrow` (`borrow_id`),
  KEY `idx_device` (`device_id`),
  KEY `idx_type` (`outcome_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='借用成果记录表';

-- -----------------------------------------------------------
-- 7. 审批记录表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `approval_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `borrow_id` bigint NOT NULL COMMENT '关联借用单',
  `step` int NOT NULL COMMENT '第几级审批',
  `approver_id` bigint DEFAULT NULL COMMENT '审批人ID（可为空，表示待管理员手动分配）',
  `result` varchar(20) DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED',
  `comment` text COMMENT '审批意见',
  `operate_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_borrow` (`borrow_id`),
  KEY `idx_approver` (`approver_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批记录表';

-- -----------------------------------------------------------
-- 8. 附件表（借用/归还照片）
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `attachment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `biz_type` varchar(30) NOT NULL COMMENT '业务类型: BORROW_IMG/RETURN_IMG/DEVICE_IMG',
  `biz_id` bigint NOT NULL COMMENT '关联业务ID',
  `file_url` varchar(500) NOT NULL COMMENT '文件URL',
  `file_size` bigint DEFAULT NULL COMMENT '文件大小(字节)',
  `upload_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `expire_time` datetime DEFAULT NULL COMMENT '过期时间（用于清理）',
  PRIMARY KEY (`id`),
  KEY `idx_biz` (`biz_type`, `biz_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='附件表';

-- -----------------------------------------------------------
-- 9. 消息通知表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `notification` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '接收人',
  `title` varchar(200) DEFAULT NULL COMMENT '标题',
  `content` text COMMENT '内容',
  `type` varchar(20) DEFAULT 'SYSTEM' COMMENT '通知类型: SYSTEM/APPROVAL/REMIND',
  `is_read` tinyint DEFAULT '0' COMMENT '0未读 1已读',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_read` (`is_read`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息通知表';

-- -----------------------------------------------------------
-- 10. 操作日志表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint DEFAULT NULL COMMENT '操作用户',
  `username` varchar(50) DEFAULT NULL COMMENT '用户名',
  `operation` varchar(100) DEFAULT NULL COMMENT '操作描述',
  `method` varchar(200) DEFAULT NULL COMMENT '请求方法',
  `params` text COMMENT '请求参数',
  `ip` varchar(50) DEFAULT NULL COMMENT '请求IP',
  `duration` bigint DEFAULT NULL COMMENT '执行时长(ms)',
  `status` tinyint DEFAULT '1' COMMENT '1成功 0失败',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_create` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志表';

-- -----------------------------------------------------------
-- 11. 系统配置表（运行时 Admin 可修改）
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `system_config` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `config_key` varchar(100) NOT NULL COMMENT '配置键',
  `config_value` varchar(500) DEFAULT NULL COMMENT '配置值',
  `description` varchar(200) DEFAULT NULL COMMENT '配置说明',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置表';

-- -----------------------------------------------------------
-- 12. 实验室表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `laboratory` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL COMMENT '实验室名称',
  `code` varchar(50) DEFAULT NULL COMMENT '实验室编码',
  `description` text COMMENT '实验室描述',
  `status` tinyint DEFAULT '1' COMMENT '1启用 0禁用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='实验室表';

-- -----------------------------------------------------------
-- 13. 实验室地点映射表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `laboratory_room` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `laboratory_id` bigint DEFAULT NULL COMMENT '关联实验室ID（可为NULL表示未分配）',
  `room_name` varchar(100) NOT NULL COMMENT '房间/地点地址（如工程南501）',
  `full_location` varchar(500) DEFAULT NULL COMMENT '完整存放地名称',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_laboratory` (`laboratory_id`),
  KEY `idx_room_name` (`room_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='实验室地点映射表';

-- 预置默认配置
INSERT INTO `system_config` (`config_key`, `config_value`, `description`) VALUES
('borrow.max_days', '7', '借用最大天数（超出拒绝），Admin可运行时修改'),
('borrow.default_approval_steps', '2', '默认审批级数（1/2/3，Admin可运行时修改）'),
('cleanup.small_record_days', '15', '小记录保留天数（通知/日志/审批记录）'),
('cleanup.large_file_days', '30', '大型临时文件保留天数（附件/借用归还图片）')
ON DUPLICATE KEY UPDATE `config_value` = VALUES(`config_value`);

-- -----------------------------------------------------------
-- 12. 维修记录表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `repair_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `device_id` bigint NOT NULL COMMENT '关联设备',
  `borrow_id` bigint DEFAULT NULL COMMENT '来源借用单ID（损坏报修时关联）',
  `fault_description` text COMMENT '故障描述',
  `status` varchar(20) DEFAULT 'PENDING' COMMENT 'PENDING=待维修 / REPAIRING=维修中 / FIXED=已修复',
  `repair_by` bigint DEFAULT NULL COMMENT '维修人ID',
  `repair_comment` text COMMENT '维修备注/方案',
  `fixed_time` datetime DEFAULT NULL COMMENT '修复时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_device` (`device_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='维修记录表';

-- -----------------------------------------------------------
-- 14. 分类描述表（目的分类 & 成果类型的说明元数据）
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `category_description` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `category_type` varchar(20) NOT NULL COMMENT '分类类型: PURPOSE=目的分类 / OUTCOME=成果类型',
  `category_name` varchar(100) NOT NULL COMMENT '分类名称（如"教学与培养""学术论文"等）',
  `description` text COMMENT '分类说明描述',
  `sort` int DEFAULT '0' COMMENT '排序',
  `status` tinyint DEFAULT '1' COMMENT '1启用 0禁用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_type` (`category_type`),
  KEY `idx_name` (`category_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分类描述表';

-- -----------------------------------------------------------
-- 15. 报废规则表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `scrap_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `gb_keyword` varchar(200) NOT NULL COMMENT '国标分类名/关键词（包含匹配）',
  `min_years` int NOT NULL DEFAULT '6' COMMENT '最低使用年限',
  `priority` int DEFAULT '100' COMMENT '优先级（越小越优先）',
  `remark` varchar(500) DEFAULT NULL COMMENT '说明',
  `status` tinyint DEFAULT '1' COMMENT '1启用 0禁用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_keyword` (`gb_keyword`(100))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报废规则表';
