#!/bin/bash
# ============================================================
# 远程部署脚本 — 将 Docker 镜像部署到 SSH 服务器
#
# 用法:
#   方式1 - 在当前项目目录执行:
#     bash scripts/deploy-remote.sh
#
#   方式2 - 指定服务器:
#     bash scripts/deploy-remote.sh hp506@gzhu-server.ydns.eu
#
# 前置条件:
#   - 远程服务器已安装 Docker
#   - 本机可 SSH 到远程服务器（已配置公钥）
#   - 本机 Docker 已运行
# ============================================================
set -euo pipefail

SERVER="${1:-hp506@gzhu-server.ydns.eu}"
REMOTE_DIR="~/server/EquipmentBrrowingSystem"

echo "============================================"
echo "  远程部署到 $SERVER"
echo "============================================"

# ─── Step 1: 构建 Docker 镜像 ───
echo "[1/6] 构建 Docker 镜像..."
cd "$(dirname "$0")/.."
docker compose build backend frontend 2>&1 | tail -3
echo "  ✅ 镜像构建完成"

# ─── Step 2: 保存镜像为 tar ───
echo "[2/6] 保存镜像..."
docker save app-backend:latest -o /tmp/app-backend.tar
docker save app-frontend:latest -o /tmp/app-frontend.tar
echo "  ✅ 镜像已保存 (backend: $(du -h /tmp/app-backend.tar | cut -f1), frontend: $(du -h /tmp/app-frontend.tar | cut -f1))"

# ─── Step 3: 安装远程服务器 Docker ───
echo "[3/6] 检查/安装远程 Docker..."
ssh "$SERVER" 'which docker' 2>/dev/null && echo "  ✅ Docker 已安装" || {
  echo "  ⚠️  安装 Docker 中..."
  ssh -tt "$SERVER" 'sudo apt-get update -qq && sudo apt-get install -y -qq docker.io docker-compose-v2 && sudo usermod -aG docker $USER && newgrp docker' 2>&1 | tail -5
  echo "  ✅ Docker 已安装"
}

# ─── Step 4: 传输镜像到远程 ───
echo "[4/6] 传输镜像到远程服务器..."
REMOTE_TMP="/tmp/equipment-deploy"
ssh "$SERVER" "mkdir -p $REMOTE_TMP $REMOTE_DIR"

echo "  传输后端镜像 (146MB)..."
docker save app-backend:latest | ssh "$SERVER" "cat > $REMOTE_TMP/app-backend.tar"
echo "  传输前端镜像 (26MB)..."
docker save app-frontend:latest | ssh "$SERVER" "cat > $REMOTE_TMP/app-frontend.tar"
echo "  ✅ 镜像传输完成"

# ─── Step 5: 传输配置文件 ───
echo "[5/6] 传输配置文件..."
tar czf /tmp/deploy-configs.tar.gz \
  docker-compose.yml \
  .env.example \
  frontend/nginx.conf \
  sql/init/ \
  scripts/ \
  2>/dev/null
ssh "$SERVER" "tar xzf - -C $REMOTE_DIR" < /tmp/deploy-configs.tar.gz
echo "  ✅ 配置文件已传输"

# ─── Step 6: 远程加载并启动 ───
echo "[6/6] 远程加载镜像并启动..."
ssh "$SERVER" "
  cd $REMOTE_DIR
  echo '  加载后端镜像...'
  docker load -i $REMOTE_TMP/app-backend.tar
  echo '  加载前端镜像...'
  docker load -i $REMOTE_TMP/app-frontend.tar
  echo '  创建 .env...'
  [ -f .env ] || cp .env.example .env
  echo '  启动服务...'
  docker compose up -d 2>&1 | tail -5
  echo ''
  echo '  ====================='
  echo '  容器状态:'
  docker compose ps 2>&1
  echo '  ====================='
  echo '  健康检查:'
  sleep 5
  curl -s http://localhost:8080/api/v1/auth/health 2>&1
" 2>&1

echo ""
echo "============================================"
echo "  ✅ 部署完成!"
echo "  访问地址: http://gzhu-server.ydns.eu"
echo "  管理员: admin / admin123"
echo "============================================"
