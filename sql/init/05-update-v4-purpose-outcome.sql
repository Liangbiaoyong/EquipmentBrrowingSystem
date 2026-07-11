-- ============================================================
-- V4 升级脚本：借用目的与成果记录
-- ============================================================

USE `device_borrow`;

-- borrow_record 新增字段
ALTER TABLE `borrow_record`
  ADD COLUMN `purpose` varchar(500) DEFAULT NULL COMMENT '借用目的（V4新增，申请时必填）' AFTER `reason`,
  ADD COLUMN `purpose_category` varchar(50) DEFAULT NULL COMMENT '目的分类: 教学/科研/行政办公/竞赛活动/其他' AFTER `purpose`,
  ADD COLUMN `outcome` text DEFAULT NULL COMMENT '借用成果（归还时可选填写/管理员后续补充）' AFTER `damage_report`,
  ADD COLUMN `outcome_recorded_by` bigint DEFAULT NULL COMMENT '成果录入人ID' AFTER `outcome`,
  ADD COLUMN `outcome_recorded_time` datetime DEFAULT NULL COMMENT '成果录入时间' AFTER `outcome_recorded_by`;

-- 将旧 reason 数据迁移到 purpose（如 reason 有值）
UPDATE `borrow_record` SET `purpose` = `reason` WHERE `reason` IS NOT NULL AND `purpose` IS NULL;
