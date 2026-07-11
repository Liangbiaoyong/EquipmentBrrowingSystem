-- ============================================================
-- V3 升级脚本：设备状态拆分（借还状态 + 设备物理状态）
-- 旧: status (1可借用/2借用中/3维修中/4待报废)
-- 新: borrow_status(1可借用/2借用中/3不可借/4逾期) + device_status(1正常/2待维修/3维修中/4待报废/5已报废)
-- ============================================================

USE `device_borrow`;

-- 1. 新增 borrow_status 和 device_status 列
ALTER TABLE `device`
  ADD COLUMN `borrow_status` tinyint DEFAULT 1 COMMENT '借还状态: 1可借用 2借用中 3不可借 4逾期' AFTER `borrow_type`,
  ADD COLUMN `device_status` tinyint DEFAULT 1 COMMENT '设备物理状态: 1正常 2待维修 3维修中 4待报废 5已报废' AFTER `borrow_status`,
  ADD INDEX `idx_borrow_status` (`borrow_status`),
  ADD INDEX `idx_device_status` (`device_status`);

-- 2. 迁移旧数据：status → borrow_status + device_status
--    status=1(可借用) → borrow_status=1(可借用), device_status=1(正常)
--    status=2(借用中) → borrow_status=2(借用中), device_status=1(正常)
--    status=3(维修中) → borrow_status=3(不可借), device_status=3(维修中)
--    status=4(待报废) → borrow_status=3(不可借), device_status=4(待报废)
UPDATE `device` SET `borrow_status` = 1, `device_status` = 1 WHERE `status` = 1;
UPDATE `device` SET `borrow_status` = 2, `device_status` = 1 WHERE `status` = 2;
UPDATE `device` SET `borrow_status` = 3, `device_status` = 3 WHERE `status` = 3;
UPDATE `device` SET `borrow_status` = 3, `device_status` = 4 WHERE `status` = 4;

-- 3. 保留旧 status 列（兼容过渡期，后续版本删除）
-- ALTER TABLE `device` DROP COLUMN `status`;  -- 待确认所有代码都已迁移后再执行
