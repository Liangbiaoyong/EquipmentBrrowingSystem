-- ============================================================
-- V6 借用取走/图片字段迁移
-- 新增 pickup_time（取走时间）、pickup_image（取走照片）
-- ============================================================

-- 使用存储过程避免重复添加报错
DROP PROCEDURE IF EXISTS add_pickup_columns;

DELIMITER //
CREATE PROCEDURE add_pickup_columns()
BEGIN
  IF NOT EXISTS (SELECT * FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'device_borrow' AND TABLE_NAME = 'borrow_record' AND COLUMN_NAME = 'pickup_time') THEN
    ALTER TABLE `borrow_record` ADD COLUMN `pickup_time` datetime DEFAULT NULL COMMENT '取走时间（实际借出开始）' AFTER `end_time`;
  END IF;

  IF NOT EXISTS (SELECT * FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'device_borrow' AND TABLE_NAME = 'borrow_record' AND COLUMN_NAME = 'pickup_image') THEN
    ALTER TABLE `borrow_record` ADD COLUMN `pickup_image` varchar(500) DEFAULT NULL COMMENT '取走/借用照片URL' AFTER `pickup_time`;
  END IF;
END //
DELIMITER ;

CALL add_pickup_columns();
DROP PROCEDURE IF EXISTS add_pickup_columns;
