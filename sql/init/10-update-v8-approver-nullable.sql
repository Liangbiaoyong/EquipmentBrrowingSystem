-- ============================================================
-- V8: 修复 approval_log.approver_id 允许 NULL
-- ============================================================
USE `device_borrow`;

-- approver_id 允许为空（审批人未分配时可为NULL）
ALTER TABLE `approval_log` MODIFY `approver_id` bigint DEFAULT NULL COMMENT '审批人ID（可为空，待管理员手动分配）';
