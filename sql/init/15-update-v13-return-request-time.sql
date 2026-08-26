-- V13: borrow_record 增加提交归还申请时间字段
ALTER TABLE `borrow_record`
  ADD COLUMN `return_request_time` datetime DEFAULT NULL COMMENT '提交归还申请时间' AFTER `real_return_time`;
