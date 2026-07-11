-- ============================================================
-- V4.1 增强：详细目的分类 + 成果记录表
-- ============================================================
USE `device_borrow`;

-- 1. borrow_record 增加目的子分类
ALTER TABLE `borrow_record`
  ADD COLUMN `purpose_subcategory` varchar(50) DEFAULT NULL COMMENT '目的子分类' AFTER `purpose_category`;

-- 2. 借用成果记录表（一个借用单可以有多条成果，一台设备可关联多个成果）
CREATE TABLE IF NOT EXISTS `borrow_outcome` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `borrow_id` bigint DEFAULT NULL COMMENT '关联借用单ID',
  `device_id` bigint NOT NULL COMMENT '关联设备ID',
  `outcome_type` varchar(50) NOT NULL COMMENT '成果类型',
  `title` varchar(500) DEFAULT NULL COMMENT '成果标题',
  `detail` text COMMENT '成果详情（JSON格式，各类型专属字段）',
  `file_urls` text COMMENT '附件URL列表（逗号分隔）',
  `recorded_by` bigint DEFAULT NULL COMMENT '录入人ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_borrow` (`borrow_id`),
  KEY `idx_device` (`device_id`),
  KEY `idx_type` (`outcome_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='借用成果记录表';

-- 3. 更新已有的 purpose_category（初始化的时候用这些分类）
-- 注意：此UPDATE仅用于数据迁移/初始化，后续由前端提交时指定
UPDATE `borrow_record` SET `purpose_category` = NULL WHERE `purpose_category` IS NULL;
