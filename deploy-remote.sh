#!/bin/bash
# ============================================================
# 远程一键部署脚本 — Equipment Borrowing System
# 用法: bash deploy-remote.sh
# 功能: Git clone + Docker Compose build & deploy + 数据初始化
# ============================================================
set -e

REMOTE_USER="${1:-hp506}"
REMOTE_HOST="${2:-gzhu-server.ydns.eu}"
REMOTE_DIR="/home/${REMOTE_USER}/server"
PROJECT="EquipmentBrrowingSystem"
REPO_URL="https://ghproxy.net/https://github.com/Liangbiaoyong/EquipmentBrrowingSystem.git"

echo "============================================================"
echo " Equipment Borrowing System - 远程一键部署"
echo " 目标: ${REMOTE_USER}@${REMOTE_HOST}"
echo "============================================================"

# ===== 1. 备份旧项目 =====
echo ""
echo "[1/6] 备份旧项目..."
ssh ${REMOTE_USER}@${REMOTE_HOST} "cd ${REMOTE_DIR} && \
    BACKUP_NAME=\"${PROJECT}.bak.\$(date +%Y%m%d%H%M%S)\" && \
    if [ -d \"${PROJECT}\" ]; then \
        mv \"${PROJECT}\" \"\$BACKUP_NAME\" && \
        echo \"备份完成: \$BACKUP_NAME\"; \
    else \
        echo \"无旧项目目录\"; \
    fi"

# ===== 2. 克隆最新代码 =====
echo ""
echo "[2/6] 克隆最新代码..."
ssh ${REMOTE_USER}@${REMOTE_HOST} "cd ${REMOTE_DIR} && git clone ${REPO_URL} ${PROJECT}"
echo "克隆完成"

# ===== 3. 恢复配置 =====
echo ""
echo "[3/6] 恢复配置文件..."
ssh ${REMOTE_USER}@${REMOTE_HOST} "cd ${REMOTE_DIR} && \
    BACKUP=\$(ls -d ${PROJECT}.bak.* 2>/dev/null | sort | tail -1) && \
    if [ -n \"\${BACKUP}\" ]; then \
        [ -f \"\${BACKUP}/.env\" ] && cp \${BACKUP}/.env ${PROJECT}/.env && echo \"已恢复 .env\"; \
        [ -d \"\${BACKUP}/certs\" ] && cp -r \${BACKUP}/certs ${PROJECT}/ && echo \"已恢复 certs\"; \
    else \
        echo \"无备份文件，跳过恢复\"; \
    fi"

# ===== 4. 构建并启动 =====
echo ""
echo "[4/6] Docker Compose 构建并启动..."
ssh ${REMOTE_USER}@${REMOTE_HOST} "cd ${REMOTE_DIR}/${PROJECT} && docker compose up -d --build 2>&1"
echo "构建完成"

# ===== 5. 等待就绪 =====
echo ""
echo "[5/6] 等待服务就绪..."
sleep 15
ssh ${REMOTE_USER}@${REMOTE_HOST} "docker ps --format 'table {{.Names}}\t{{.Status}}'"

# ===== 6. 清除旧数据并导入 =====
echo ""
echo "[6/6] 清除旧数据库数据并重新导入..."
read -p "是否清除数据库并重新导入初始数据? (y/N): " CONFIRM
if [ "$CONFIRM" = "y" ] || [ "$CONFIRM" = "Y" ]; then
    ssh ${REMOTE_USER}@${REMOTE_HOST} "
        # 从 .env 读取密码
        source ${REMOTE_DIR}/${PROJECT}/.env

        # 清除现有数据（保留表结构）
        docker exec dev-mysql mysql -uroot -p\${MYSQL_ROOT_PASSWORD} \
            --default-character-set=utf8mb4 device_borrow \
            -e 'TRUNCATE approval_log; TRUNCATE borrow_outcome; TRUNCATE borrow_record; TRUNCATE attachment; TRUNCATE notification; TRUNCATE sys_log; DELETE FROM device; DELETE FROM device_image; DELETE FROM category_mapping; ALTER TABLE device AUTO_INCREMENT=1;'

        # 重新导入种子数据
        docker exec -i dev-mysql mysql -uroot -p\${MYSQL_ROOT_PASSWORD} \
            --default-character-set=utf8mb4 device_borrow \
            < ${REMOTE_DIR}/${PROJECT}/sql/init/02-data.sql

        echo '初始数据重新导入完成'

        # 可选：导入测试数据（一个月随机借用记录）
        read -p '是否生成一个月测试数据? (y/N): ' GEN_TEST
        if [ \"\${GEN_TEST}\" = \"y\" ] || [ \"\${GEN_TEST}\" = \"Y\" ]; then
            docker exec -i dev-mysql mysql -uroot -p\${MYSQL_ROOT_PASSWORD} \
                --default-character-set=utf8mb4 device_borrow \
                < ${REMOTE_DIR}/${PROJECT}/sql/init/03-test-data.sql
            echo '测试数据生成完成'
        fi
    "
fi

echo ""
echo "============================================================"
echo " 部署完成!"
echo " 后端 API: http://${REMOTE_HOST}:8080"
echo " 前端页面: http://${REMOTE_HOST}"
echo " 测试账号: admin/admin123 (系统管理员)"
echo "============================================================"
