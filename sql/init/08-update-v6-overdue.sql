-- ============================================================
-- V6: 逾期管理增强 — 逾期记录表 + 催还跟踪
-- ============================================================
USE `device_borrow`;

-- 1. 逾期记录表（每次逾期检测/催还/强制归还都记录）
CREATE TABLE IF NOT EXISTS `overdue_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `borrow_id` bigint NOT NULL COMMENT '关联借用单ID',
  `device_id` bigint NOT NULL COMMENT '设备ID',
  `user_id` bigint NOT NULL COMMENT '借用人ID',
  `overdue_days` int DEFAULT '0' COMMENT '本次逾期天数',
  `fine_amount` decimal(10,2) DEFAULT '0.00' COMMENT '罚款金额',
  `fine_status` varchar(20) DEFAULT 'UNPAID' COMMENT '罚款状态: UNPAID未缴/PAID已缴/WAIVED免除',
  `collection_status` varchar(20) DEFAULT 'PENDING' COMMENT '催缴状态: PENDING待催还/NOTIFIED已通知/COLLECTED已强制归还',
  `notify_count` int DEFAULT '0' COMMENT '催还通知总次数',
  `last_notify_time` datetime DEFAULT NULL COMMENT '最近一次催还时间',
  `admin_collect_time` datetime DEFAULT NULL COMMENT '强制归还时间（管理员操作）',
  `collect_admin_id` bigint DEFAULT NULL COMMENT '强制归还操作人ID',
  `collect_remark` text COMMENT '强制归还备注',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_borrow` (`borrow_id`),
  KEY `idx_device` (`device_id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_fine_status` (`fine_status`),
  KEY `idx_collection_status` (`collection_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='逾期记录表';
