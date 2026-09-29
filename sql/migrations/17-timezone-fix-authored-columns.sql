-- ============================================================
-- V15: 修正 V14 对「字面量时间列」的过度平移（一次性数据修复）
-- ============================================================
-- ⚠️ 只有在「2026-09-29 之前版本的 16-timezone-cst-shift.sql」已经执行过的库上才需要运行本脚本。
--    那一版按「全库都是 UTC 墙钟」一刀切，把以下几列也 +8 小时，而它们本来就是正确的本地时间：
--
--      来源 sql/init/03-test-data.sql：
--        start_time            ← 脚本第 65 行字面量 CONCAT(@day,' ',@start_hour,':00:00')
--        end_time              ← 第 66 行 @start_time + N 天
--        real_return_time      ← 第 117/118 行 @end_time + N 天
--        outcome_recorded_time ← 第 131 行 @end_time + N 天
--        create_time           ← 第 132 行 @start_time - N 天
--        approval_log.operate_time ← 第 147/152 行 @start_time - 1 天
--
--    真正需要 +8 小时的只有「系统生成」的时间：NOW()/CURRENT_TIMESTAMP 与
--    Java 侧 LocalDateTime.now() 写入的列（update_time、pickup_time、
--    return_request_time、各表默认值填充的 create_time 等）——这些 V14 已处理正确，本脚本不动。
--
-- 判别种子行
--   种子行的 create_time 由整点字面量推导，秒恒为 0；而 update_time 是灌数据时刻的 NOW()。
--   故 `SECOND(create_time)=0 AND create_time<>update_time` 可稳定识别种子行
--   （实测 1786/1840 行，其余 54 行为应用创建，其 create_time 属系统生成，不予回退）。
--
-- 幂等保护
--   system_config.db.timezone_shifted 必须为 1（即 V14 已执行），
--   且 db.authored_times_reverted 未设置。两个条件同时满足才执行。
--
-- 执行方式
--   docker exec -i dev-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" \
--     --default-character-set=utf8mb4 device_borrow < sql/migrations/17-timezone-fix-authored-columns.sql
-- ============================================================

SET @applied := (SELECT COUNT(*) FROM `system_config` WHERE `config_key` = 'db.timezone_shifted');
SET @fixed   := (SELECT COUNT(*) FROM `system_config` WHERE `config_key` = 'db.authored_times_reverted');

-- 1) start_time / end_time：无论种子还是应用写入，取值都源自用户选择或字面量，统一回退
--    注意：borrow_record.update_time 带 ON UPDATE CURRENT_TIMESTAMP，须显式赋值以免被刷成 NOW()
UPDATE `borrow_record`
   SET `start_time`  = DATE_SUB(`start_time`, INTERVAL 8 HOUR),
       `end_time`    = DATE_SUB(`end_time`,   INTERVAL 8 HOUR),
       `update_time` = `update_time`
 WHERE @applied = 1 AND @fixed = 0;

-- 2) 种子行由字面量推导的三列回退；应用创建的行保持 V14 修正后的值
UPDATE `borrow_record`
   SET `create_time`           = DATE_SUB(`create_time`,           INTERVAL 8 HOUR),
       `real_return_time`      = DATE_SUB(`real_return_time`,      INTERVAL 8 HOUR),
       `outcome_recorded_time` = DATE_SUB(`outcome_recorded_time`, INTERVAL 8 HOUR),
       `update_time`           = `update_time`
 WHERE @applied = 1 AND @fixed = 0
   AND SECOND(`create_time`) = 0 AND `create_time` <> `update_time`;

-- 3) 种子借用单对应的审批日志（operate_time 由 start_time 推导）
UPDATE `approval_log` a
  JOIN `borrow_record` b ON a.`borrow_id` = b.`id`
   SET a.`operate_time` = DATE_SUB(a.`operate_time`, INTERVAL 8 HOUR)
 WHERE @applied = 1 AND @fixed = 0
   AND SECOND(b.`create_time`) = 0 AND b.`create_time` <> b.`update_time`;

-- 落标记
INSERT IGNORE INTO `system_config` (`config_key`, `config_value`, `description`)
VALUES ('db.authored_times_reverted', '1', '已回退 V14 对字面量时间列（start_time/end_time 等）的过度平移（V15）');
