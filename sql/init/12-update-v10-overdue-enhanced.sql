-- V10: 逾期管理增强
USE `device_borrow`;

-- overdue_record 表增加字段（如不存在则添加）
ALTER TABLE `overdue_record` ADD COLUMN IF NOT EXISTS `fine_amount` decimal(10,2) DEFAULT '0.00' COMMENT '罚款金额';
ALTER TABLE `overdue_record` ADD COLUMN IF NOT EXISTS `fine_paid` tinyint DEFAULT '0' COMMENT '是否已缴罚款 0未缴 1已缴';
ALTER TABLE `overdue_record` ADD COLUMN IF NOT EXISTS `notify_count` int DEFAULT '0' COMMENT '催还通知次数';
ALTER TABLE `overdue_record` ADD COLUMN IF NOT EXISTS `last_notify_time` datetime DEFAULT NULL COMMENT '最近催还时间';
ALTER TABLE `overdue_record` ADD COLUMN IF NOT EXISTS `admin_collect_time` datetime DEFAULT NULL COMMENT '管理员强制归还时间';
ALTER TABLE `overdue_record` ADD COLUMN IF NOT EXISTS `collect_admin_id` bigint DEFAULT NULL COMMENT '强制归还管理员ID';
ALTER TABLE `overdue_record` ADD COLUMN IF NOT EXISTS `collect_remark` text COMMENT '强制归还备注';
