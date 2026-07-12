@echo off
REM ============================================================
REM 一键部署脚本 (Windows)
REM 用法: 双击运行 或在终端执行 run-deploy.bat
REM ============================================================

echo ============================================================
echo Equipment Borrowing System - 远程部署
echo ============================================================

set REMOTE_USER=hp506
set REMOTE_HOST=gzhu-server.ydns.eu
set REMOTE_DIR=/home/hp506/server
set REPO_URL=https://ghproxy.net/https://github.com/Liangbiaoyong/EquipmentBrrowingSystem.git

echo [1/6] 备份旧项目...
ssh %REMOTE_USER%@%REMOTE_HOST% "mv %REMOTE_DIR%/EquipmentBrrowingSystem %REMOTE_DIR%/EquipmentBrrowingSystem.bak.$(date +%%s) 2>/dev/null; echo 备份完成"

echo [2/6] 克隆最新代码...
ssh %REMOTE_USER%@%REMOTE_HOST% "cd %REMOTE_DIR% && git clone %REPO_URL% EquipmentBrrowingSystem && echo 克隆完成"

echo [3/6] 恢复配置...
ssh %REMOTE_USER%@%REMOTE_HOST% "BACKUP=$(ls -d %REMOTE_DIR%/EquipmentBrrowingSystem.bak.* 2>/dev/null | sort | tail -1); if [ -n "$BACKUP" ]; then cp $BACKUP/.env %REMOTE_DIR%/EquipmentBrrowingSystem/.env 2>/dev/null; cp -r $BACKUP/certs %REMOTE_DIR%/EquipmentBrrowingSystem/ 2>/dev/null; fi; echo 配置恢复完成"

echo [4/6] Docker Compose 构建并启动...
ssh %REMOTE_USER%@%REMOTE_HOST% "cd %REMOTE_DIR%/EquipmentBrrowingSystem && docker compose up -d --build 2>&1"

echo [5/6] 等待服务就绪...
timeout /t 20 /nobreak >nul
ssh %REMOTE_USER%@%REMOTE_HOST% "docker ps --format 'table {{.Names}}\t{{.Status}}'"

echo.
echo [6/6] 是否清除数据库并重新导入?
set /p CONFIRM="输入 y 确认: "
if /i "%CONFIRM%"=="y" (
    echo 正在清除旧数据并重新导入...
    for /f %%i in ('ssh %REMOTE_USER%@%REMOTE_HOST% "grep MYSQL_ROOT_PASSWORD %REMOTE_DIR%/EquipmentBrrowingSystem/.env | cut -d= -f2"') do set MYSQL_ROOT_PASSWORD=%%i
    ssh %REMOTE_USER%@%REMOTE_HOST% "source %REMOTE_DIR%/EquipmentBrrowingSystem/.env && docker exec dev-mysql mysql -uroot -p$MYSQL_ROOT_PASSWORD --default-character-set=utf8mb4 device_borrow -e 'TRUNCATE approval_log; TRUNCATE borrow_outcome; TRUNCATE borrow_record; DELETE FROM device; DELETE FROM device_image; DELETE FROM category_mapping;' && echo 清除完成"
    ssh %REMOTE_USER%@%REMOTE_HOST% "cd %REMOTE_DIR%/EquipmentBrrowingSystem && docker exec -i dev-mysql mysql -uroot -p$(grep MYSQL_ROOT_PASSWORD .env | cut -d= -f2) --default-character-set=utf8mb4 device_borrow < sql/init/02-data.sql && echo 种子数据导入完成"

    set /p GEN_TEST="生成一个月测试数据? (y/N): "
    if /i "!GEN_TEST!"=="y" (
        ssh %REMOTE_USER%@%REMOTE_HOST% "cd %REMOTE_DIR%/EquipmentBrrowingSystem && docker exec -i dev-mysql mysql -uroot -p$(grep MYSQL_ROOT_PASSWORD .env | cut -d= -f2) --default-character-set=utf8mb4 device_borrow < sql/init/03-test-data.sql && echo 测试数据生成完成"
    )
)

echo.
echo ============================================================
echo 部署完成!
echo 后端 API: http://%REMOTE_HOST%:8080
echo 前端页面: http://%REMOTE_HOST%
echo 测试账号: admin/admin123
echo ============================================================
pause
