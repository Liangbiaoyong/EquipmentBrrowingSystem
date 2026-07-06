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
-- 2. 设备分类表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `device_category` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(50) NOT NULL COMMENT '分类名称',
  `parent_id` bigint DEFAULT '0' COMMENT '父分类ID',
  `sort` int DEFAULT '0' COMMENT '排序',
  `status` tinyint DEFAULT '1' COMMENT '1启用 0禁用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备分类表';

-- -----------------------------------------------------------
-- 3. 设备表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `device` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL COMMENT '设备名称',
  `model` varchar(100) DEFAULT NULL COMMENT '型号',
  `category_id` bigint DEFAULT NULL COMMENT '分类ID',
  `location` varchar(200) DEFAULT NULL COMMENT '存放位置',
  `total_qty` int NOT NULL DEFAULT '1' COMMENT '总数量',
  `available_qty` int NOT NULL DEFAULT '1' COMMENT '当前可借数量',
  `status` tinyint DEFAULT '1' COMMENT '1正常 2维修中 3报废',
  `description` text COMMENT '设备描述/配件说明',
  `cover_image` varchar(500) DEFAULT NULL COMMENT '封面图URL',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_category` (`category_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备表';

-- -----------------------------------------------------------
-- 4. 设备图片表
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
-- 5. 借用单表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `borrow_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '借用人',
  `device_id` bigint NOT NULL COMMENT '设备ID',
  `start_time` datetime NOT NULL COMMENT '借用开始时间',
  `end_time` datetime NOT NULL COMMENT '计划归还时间',
  `status` varchar(20) NOT NULL DEFAULT 'PENDING_APPROVAL' COMMENT '状态: PENDING_APPROVAL/APPROVED/REJECTED/BORROWING/RETURNED/OVERDUE/CANCELLED',
  `reason` text COMMENT '借用事由',
  `approve_flow_def` text COMMENT '审批流定义JSON',
  `current_step` int DEFAULT '0' COMMENT '当前审批步骤',
  `real_return_time` datetime DEFAULT NULL COMMENT '实际归还时间',
  `overdue_days` int DEFAULT '0' COMMENT '逾期天数',
  `damage_report` text COMMENT '损坏报告',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_device` (`device_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='借用单表';

-- -----------------------------------------------------------
-- 6. 审批记录表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `approval_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `borrow_id` bigint NOT NULL COMMENT '关联借用单',
  `step` int NOT NULL COMMENT '第几级审批',
  `approver_id` bigint NOT NULL COMMENT '审批人ID',
  `result` varchar(20) DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED',
  `comment` text COMMENT '审批意见',
  `operate_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_borrow` (`borrow_id`),
  KEY `idx_approver` (`approver_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批记录表';

-- -----------------------------------------------------------
-- 7. 附件表（借用/归还照片）
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
-- 8. 消息通知表
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
-- 9. 操作日志表
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
