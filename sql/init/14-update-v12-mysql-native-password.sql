-- ============================================================
-- V12: MySQL root用户改为mysql_native_password
-- 原因: mariadb-dump(MariaDB客户端)不支持caching_sha2_password插件
-- 影响: 仅改变认证插件，密码不变，对内网安全无影响
-- ============================================================
USE `device_borrow`;

ALTER USER 'root'@'%' IDENTIFIED WITH mysql_native_password BY '${MYSQL_ROOT_PASSWORD}';
ALTER USER 'root'@'localhost' IDENTIFIED WITH mysql_native_password BY '${MYSQL_ROOT_PASSWORD}';
FLUSH PRIVILEGES;

SELECT 'AUTH_MIGRATED' AS result;
