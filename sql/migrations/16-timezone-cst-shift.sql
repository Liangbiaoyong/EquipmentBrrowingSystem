-- ============================================================
-- V14: 历史时间数据 UTC → UTC+8 平移（一次性数据迁移）
-- ============================================================
-- 背景
--   本系统此前全链路运行在 UTC：宿主机、容器、JVM 均未设置时区，
--   而 MySQL 的 @@global.time_zone 为 SYSTEM。因此凡由系统生成的时间
--   （MySQL NOW()/CURRENT_TIMESTAMP、Java 侧 LocalDateTime.now()）
--   写入的都是 UTC 墙钟，界面与日志比北京时间慢 8 小时。
--   自 V14 起容器与 JVM 统一为 Asia/Shanghai，新写入的数据即为北京时间。
--
-- ⚠️ 只有「系统生成」的时间列需要平移
--   03-test-data.sql 里有一部分时间列是**脚本写死的字面量**，本来就是本地时间：
--     start_time            ← 第 65 行 CONCAT(@day,' ',@start_hour,':00:00')
--     end_time              ← 第 66 行 @start_time + N 天
--     real_return_time      ← 第 117/118 行 @end_time + N 天
--     outcome_recorded_time ← 第 131 行 @end_time + N 天
--     create_time           ← 第 132 行 @start_time - N 天
--     approval_log.operate_time ← 第 147/152 行 @start_time - 1 天
--   前两列（start_time/end_time）无论种子还是应用写入都源自用户选择或字面量，
--   一律**不平移**；其余四列仅对「应用写入」的行平移。
--
-- ✅ 判别种子行
--   种子行的 create_time 由整点字面量推导，秒恒为 0，而 update_time 是灌数据时刻的 NOW()。
--   故 `SECOND(create_time) = 0 AND create_time <> update_time` 可稳定识别种子行。
--
-- ⚠️ 一次性脚本
--   依赖 system_config 的 db.timezone_shifted 标记做幂等保护，重复执行不会二次平移。
--   但请勿在「全新初始化、数据本来就是北京时间」的库上执行，那会把正确数据推快 8 小时。
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
--
-- 历史版本提醒
--   2026-09-29 之前版本的 16 号脚本按「全库都是 UTC」一刀切，把上列字面量列也平移了。
--   若某个库执行的是那一版，需再执行 17-timezone-fix-authored-columns.sql 回退。
-- ============================================================

SET @shifted := (SELECT COUNT(*) FROM `system_config` WHERE `config_key` = 'db.timezone_shifted');

-- 说明：DATE_ADD(NULL, ...) 结果为 NULL，未赋值的时间列保持 NULL，无需额外判空。
-- 说明：create_time/update_time 带 ON UPDATE CURRENT_TIMESTAMP，凡是有 update_time
--       的表都在同一条 UPDATE 中显式赋值，避免它被自动刷成 NOW()。
--
-- 以下各表的 create_time/update_time 均来自列默认值 CURRENT_TIMESTAMP 或应用写入，
-- 即系统生成，整体平移。

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

-- 6. borrow_record
--    start_time / end_time 不平移：由用户选择（日期选择器）或种子字面量给出，本就是本地时间。
--    pickup_time / return_request_time 只有应用会写（种子不写这两列），平移。
--    update_time 一律平移（种子写 NOW()，应用走自动填充）。
UPDATE `borrow_record`
   SET `pickup_time`          = DATE_ADD(`pickup_time`,          INTERVAL 8 HOUR),
       `return_request_time`  = DATE_ADD(`return_request_time`,  INTERVAL 8 HOUR),
       `update_time`          = DATE_ADD(`update_time`,          INTERVAL 8 HOUR)
 WHERE @shifted = 0;

--    create_time / real_return_time / outcome_recorded_time：
--    应用写入的为系统生成，需平移；种子行由字面量推导，不平移。
UPDATE `borrow_record`
   SET `create_time`           = DATE_ADD(`create_time`,           INTERVAL 8 HOUR),
       `real_return_time`      = DATE_ADD(`real_return_time`,      INTERVAL 8 HOUR),
       `outcome_recorded_time` = DATE_ADD(`outcome_recorded_time`, INTERVAL 8 HOUR),
       `update_time`           = `update_time`
 WHERE @shifted = 0
   AND NOT (SECOND(`create_time`) = 0 AND `create_time` <> `update_time`);

-- 7. borrow_outcome
UPDATE `borrow_outcome`
   SET `create_time` = DATE_ADD(`create_time`, INTERVAL 8 HOUR)
 WHERE @shifted = 0;

-- 8. approval_log
--    种子行由 start_time 推导（第 147/152 行），不平移；应用写入的为系统生成，平移。
UPDATE `approval_log` a
  JOIN `borrow_record` b ON a.`borrow_id` = b.`id`
   SET a.`operate_time` = DATE_ADD(a.`operate_time`, INTERVAL 8 HOUR)
 WHERE @shifted = 0
   AND NOT (SECOND(b.`create_time`) = 0 AND b.`create_time` <> b.`update_time`);

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
