---
name: deploy-remote
description: 将 Docker 镜像构建后传输到远程 SSH 服务器并一键部署
---

# deploy-remote — 远程 SSH 服务器一键部署

将本地构建的 Docker 镜像传输到远程 SSH 服务器并启动。

## 触发词

- "部署到服务器"
- "远程部署"
- "部署到公网"

## 执行步骤

调用 `scripts/deploy-remote.sh`：

```bash
# 默认部署到 hp506@gzhu-server.ydns.eu
bash scripts/deploy-remote.sh

# 指定其他服务器
bash scripts/deploy-remote.sh user@host
```

## 流程

1. **本地构建** `docker compose build backend frontend`
2. **保存镜像** `docker save` → tar 文件
3. **安装远程 Docker** 若远程未安装则自动安装
4. **传输镜像** SSH pipe 传输 tar（不落盘）
5. **传输配置** `docker-compose.yml` + `nginx.conf` + `sql/init/`
6. **远程加载并启动** `docker load` + `docker compose up -d`

## 已知问题

| 问题 | 处理 |
|------|------|
| sudo 需密码 | 确保远程用户有 sudo 权限且 NOPASSWD 配置 |
| 端口占用 | 确保 80/8080 端口未占用 |
| MySQL 数据卷 | 首次启动自动建表，已有数据不重复执行 `sql/init/` |
