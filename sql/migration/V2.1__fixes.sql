-- ============================================================
-- V2.1 上线问题修复迁移脚本
-- 执行: mysql -u devuser -p device_borrow < this_file.sql
-- ============================================================

USE `device_borrow`;

-- 1. 修复数据库字符集（解决中文乱码）
ALTER DATABASE device_borrow CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE sys_user CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE device CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE device_category CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE category_mapping CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

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

-- 5. 创建实验室管理员（密码: lab123456）
INSERT INTO `sys_user` (`username`, `real_name`, `user_type`, `department`, `password`, `auth_source`, `status`)
VALUES ('labadmin', '实验室管理员', 2, '建筑学院',
        '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH',
        'L', 1)
ON DUPLICATE KEY UPDATE `real_name` = '实验室管理员';
