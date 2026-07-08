-- ============================================================
-- V2.1 上线问题修复迁移脚本
-- 执行: mysql -u devuser -p device_borrow < this_file.sql
-- ============================================================

USE `device_borrow`;

-- 1. 修复数据库字符集（解决中文乱码）
-- ⚠️ 重要：如果数据在latin1连接下导入，数据已永久损坏，必须TRUNCATE后重新导入
-- 下面的CONVERT只修改表默认字符集，不能修复已损坏的数据
ALTER DATABASE device_borrow CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE sys_user CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE device CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE device_category CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE category_mapping CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 清空并重建数据（如果乱码问题持续，取消下面注释来清空后重新导入）
-- TRUNCATE TABLE device;
-- TRUNCATE TABLE device_category;
-- TRUNCATE TABLE category_mapping;
-- 然后重新执行 02-data.sql，并重新导入CSV

-- 2. 确保 repair_record 表存在（如之前未创建）
CREATE TABLE IF NOT EXISTS `repair_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `device_id` bigint NOT NULL,
  `borrow_id` bigint DEFAULT NULL,
  `fault_description` text,
  `status` varchar(20) DEFAULT 'PENDING',
  `repair_by` bigint DEFAULT NULL,
  `repair_comment` text,
  `fixed_time` datetime DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_device` (`device_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3. 确保 device.default_approver_id 字段存在
ALTER TABLE device ADD COLUMN IF NOT EXISTS `default_approver_id` bigint DEFAULT NULL AFTER `custodian`;

-- 4. 确保 device.available_qty 字段存在
ALTER TABLE device ADD COLUMN IF NOT EXISTS `available_qty` int NOT NULL DEFAULT '1' AFTER `total_qty`;

-- 5. 确保所有核心表存在（防止生产环境遗漏导致页面卡死）
CREATE TABLE IF NOT EXISTS `system_config` (
  `id` bigint NOT NULL AUTO_INCREMENT, `config_key` varchar(100) NOT NULL, `config_value` varchar(500) DEFAULT NULL,
  `description` varchar(200) DEFAULT NULL, `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `notification` (
  `id` bigint NOT NULL AUTO_INCREMENT, `user_id` bigint NOT NULL, `title` varchar(200) DEFAULT NULL,
  `content` text, `type` varchar(20) DEFAULT 'SYSTEM', `is_read` tinyint DEFAULT '0',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY (`id`), KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `attachment` (
  `id` bigint NOT NULL AUTO_INCREMENT, `biz_type` varchar(30) NOT NULL, `biz_id` bigint NOT NULL,
  `file_url` varchar(500) NOT NULL, `file_size` bigint DEFAULT NULL, `upload_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `expire_time` datetime DEFAULT NULL, PRIMARY KEY (`id`), KEY `idx_biz` (`biz_type`,`biz_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 6. 创建实验室管理员（密码: lab123456）
INSERT INTO `sys_user` (`username`, `real_name`, `user_type`, `department`, `password`, `auth_source`, `status`)
VALUES ('labadmin', '实验室管理员', 2, '建筑学院',
        '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH',
        'L', 1)
ON DUPLICATE KEY UPDATE `real_name` = '实验室管理员';
