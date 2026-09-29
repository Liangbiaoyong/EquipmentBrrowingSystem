-- ============================================================
-- V14: 历史时间数据 UTC → UTC+8 平移（一次性数据迁移）
-- ============================================================
-- 背景
--   本系统此前全链路运行在 UTC：宿主机、容器、JVM 均未设置时区，
--   而 MySQL 的 @@global.time_zone 为 SYSTEM。因此所有 datetime 列
--   写入的都是 UTC 墙钟时间，界面与日志比北京时间慢 8 小时。
--   自 V14 起容器与 JVM 统一为 Asia/Shanghai，新写入的数据即为北京时间。
--   本脚本把既有数据整体 +8 小时，使存量与新数据口径一致。
--
-- ⚠️ 一次性脚本
--   依赖 system_config 的 db.timezone_shifted 标记做幂等保护，重复执行
--   不会二次平移。但请勿在「全新初始化、数据本来就是北京时间」的库上执行，
--   那会把正确数据推快 8 小时。
--
-- 执行时机
--   时区配置（docker-compose.yml / Dockerfile）上线后执行一次。
--
-- 执行方式（本文件不在 sql/init/ 内，不会被初始化流程自动执行）
--   docker exec -i dev-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" \
--     --default-character-set=utf8mb4 device_borrow < sql/migrations/16-timezone-cst-shift.sql
--
--   注：脚本内不使用 USE 语句，库名由上面的调用参数指定，
--       避免在别的库上误执行时写到生产库。
-- ============================================================

SET @shifted := (SELECT COUNT(*) FROM `system_config` WHERE `config_key` = 'db.timezone_shifted');

-- 说明：DATE_ADD(NULL, ...) 结果为 NULL，未赋值的时间列保持 NULL，无需额外判空。
-- 说明：create_time/update_time 带 ON UPDATE CURRENT_TIMESTAMP，凡是有 update_time
--       的表都在同一条 UPDATE 中显式赋值，避免它被自动刷成 NOW()。

-- 1. sys_user
UPDATE `sys_user`
   SET `last_cas_login` = DATE_ADD(`last_cas_login`, INTERVAL 8 HOUR),
       `create_time`    = DATE_ADD(`create_time`,    INTERVAL 8 HOUR),
       `update_time`    = DATE_ADD(`update_time`,    INTERVAL 8 HOUR)
 WHERE @shifted = 0;

-- 2. device_category
UPDATE `device_category`
   SET `create_time` = DATE_ADD(`create_time`, INTERVAL 8 HOUR)
 WHERE @shifted = 0;

-- 3. device
UPDATE `device`
   SET `create_time` = DATE_ADD(`create_time`, INTERVAL 8 HOUR),
       `update_time` = DATE_ADD(`update_time`, INTERVAL 8 HOUR)
 WHERE @shifted = 0;

-- 4. category_mapping
UPDATE `category_mapping`
   SET `create_time` = DATE_ADD(`create_time`, INTERVAL 8 HOUR)
 WHERE @shifted = 0;

-- 5. device_image
UPDATE `device_image`
   SET `create_time` = DATE_ADD(`create_time`, INTERVAL 8 HOUR)
 WHERE @shifted = 0;

-- 6. borrow_record（含业务时间列：计划/实际借用归还、逾期判定依赖 end_time）
UPDATE `borrow_record`
   SET `start_time`           = DATE_ADD(`start_time`,           INTERVAL 8 HOUR),
       `end_time`             = DATE_ADD(`end_time`,             INTERVAL 8 HOUR),
       `pickup_time`          = DATE_ADD(`pickup_time`,          INTERVAL 8 HOUR),
       `real_return_time`     = DATE_ADD(`real_return_time`,     INTERVAL 8 HOUR),
       `return_request_time`  = DATE_ADD(`return_request_time`,  INTERVAL 8 HOUR),
       `outcome_recorded_time`= DATE_ADD(`outcome_recorded_time`,INTERVAL 8 HOUR),
       `create_time`          = DATE_ADD(`create_time`,          INTERVAL 8 HOUR),
       `update_time`          = DATE_ADD(`update_time`,          INTERVAL 8 HOUR)
 WHERE @shifted = 0;

-- 7. borrow_outcome
UPDATE `borrow_outcome`
   SET `create_time` = DATE_ADD(`create_time`, INTERVAL 8 HOUR)
 WHERE @shifted = 0;

-- 8. approval_log
UPDATE `approval_log`
   SET `operate_time` = DATE_ADD(`operate_time`, INTERVAL 8 HOUR)
 WHERE @shifted = 0;

-- 9. attachment
UPDATE `attachment`
   SET `upload_time` = DATE_ADD(`upload_time`, INTERVAL 8 HOUR),
       `expire_time` = DATE_ADD(`expire_time`, INTERVAL 8 HOUR)
 WHERE @shifted = 0;

-- 10. notification
UPDATE `notification`
   SET `create_time` = DATE_ADD(`create_time`, INTERVAL 8 HOUR)
 WHERE @shifted = 0;

-- 11. sys_log
UPDATE `sys_log`
   SET `create_time` = DATE_ADD(`create_time`, INTERVAL 8 HOUR)
 WHERE @shifted = 0;

-- 12. system_config
UPDATE `system_config`
   SET `create_time` = DATE_ADD(`create_time`, INTERVAL 8 HOUR),
       `update_time` = DATE_ADD(`update_time`, INTERVAL 8 HOUR)
 WHERE @shifted = 0;

-- 13. laboratory
UPDATE `laboratory`
   SET `create_time` = DATE_ADD(`create_time`, INTERVAL 8 HOUR),
       `update_time` = DATE_ADD(`update_time`, INTERVAL 8 HOUR)
 WHERE @shifted = 0;

-- 14. laboratory_room
UPDATE `laboratory_room`
   SET `create_time` = DATE_ADD(`create_time`, INTERVAL 8 HOUR)
 WHERE @shifted = 0;

-- 15. repair_record
UPDATE `repair_record`
   SET `fixed_time`  = DATE_ADD(`fixed_time`,  INTERVAL 8 HOUR),
       `create_time` = DATE_ADD(`create_time`, INTERVAL 8 HOUR),
       `update_time` = DATE_ADD(`update_time`, INTERVAL 8 HOUR)
 WHERE @shifted = 0;

-- 16. category_description
UPDATE `category_description`
   SET `create_time` = DATE_ADD(`create_time`, INTERVAL 8 HOUR),
       `update_time` = DATE_ADD(`update_time`, INTERVAL 8 HOUR)
 WHERE @shifted = 0;

-- 17. scrap_rule
UPDATE `scrap_rule`
   SET `create_time` = DATE_ADD(`create_time`, INTERVAL 8 HOUR),
       `update_time` = DATE_ADD(`update_time`, INTERVAL 8 HOUR)
 WHERE @shifted = 0;

-- 落标记：表示历史数据已平移，重复执行会被上面的 WHERE 拦住
INSERT IGNORE INTO `system_config` (`config_key`, `config_value`, `description`)
VALUES ('db.timezone_shifted', '1', '历史时间数据已从 UTC 平移为 UTC+8（V14 时区统一）');
