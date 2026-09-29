-- ============================================================
-- V16: 补建缺失的 overdue_record 表（一次性修复）
-- ============================================================
-- 背景
--   `overdue_record` 由 sql/init/08-update-v6-overdue.sql 创建。但 sql/init/ 目录
--   只在 **数据库首次初始化**（数据目录为空）时被 MySQL 入口脚本执行一次；
--   该文件是在卷已存在之后才加入仓库的，因此**从未执行过**，线上库缺这张表。
--
--   后果：BorrowController#overdueStats 会查这张表（催还中 / 已强制归还两个计数），
--   于是抛 SQLSyntaxErrorException → 接口 500 → 前端逾期管理页四张统计卡片
--   全部停在默认值 0，并弹出「服务器内部错误」。
--
-- 幂等：CREATE TABLE IF NOT EXISTS，重复执行无副作用。
--
-- 执行方式（本文件不在 sql/init/ 内，不会被初始化流程自动执行）
--   docker exec -i dev-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" \
--     --default-character-set=utf8mb4 device_borrow < sql/migrations/18-create-overdue-record.sql
--
-- 注：全新环境无需执行——初始化流程按文件名顺序执行，08 号脚本会建好这张表。
-- ============================================================

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
