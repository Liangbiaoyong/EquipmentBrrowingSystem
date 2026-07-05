-- ============================================================
-- 初始数据
-- ============================================================

USE `device_borrow`;

-- 插入系统管理员（密码: admin123，使用BCrypt加密）
INSERT INTO `sys_user` (`username`, `real_name`, `user_type`, `department`, `password`, `auth_source`, `status`)
VALUES ('admin', '系统管理员', 3, '信息中心',
        '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', -- 实际需用BCrypt生成
        'L', 1)
ON DUPLICATE KEY UPDATE `real_name` = '系统管理员';

-- 插入默认设备分类
INSERT INTO `device_category` (`id`, `name`, `parent_id`, `sort`, `status`) VALUES
(1, '测量仪器', 0, 1, 1),
(2, '摄影器材', 0, 2, 1),
(3, '模型工具', 0, 3, 1),
(4, '计算机设备', 0, 4, 1),
(5, '实验设备', 0, 5, 1)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);
