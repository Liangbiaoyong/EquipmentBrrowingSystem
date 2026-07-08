---
name: deploy-wsl2
description: 一键将项目搬运到 WSL2 并用 Docker Compose 构建部署
---

# deploy-wsl2 — WSL2 Docker 一键部署

将当前项目从 Windows 文件系统搬运到 WSL2 Ubuntu，安装 Docker 引擎并启动所有服务。

## 触发词

- "部署到 WSL2"
- "一键部署"
- "搬运到 WSL2"
- "docker 部署"

## 执行步骤

调用 `TEMP/deploy-wsl2.sh` 脚本，传入项目根目录：

```bash
# 从项目根目录执行
bash TEMP/deploy-wsl2.sh .
```

脚本会自动完成以下步骤：

1. **检查 WSL2** — 确认 Ubuntu 发行版存在并启动
2. **安装 Docker** — 若 Docker 引擎未运行则自动安装
3. **搬运项目** — 用 `tar` 复制到 WSL2 `/app`（排除 .git/AgentTools）
4. **创建 .env** — 写入数据库密码、JWT 密钥等配置
5. **构建镜像** — `docker compose up -d --build`
6. **初始化 MinIO** — 创建 device-borrow bucket
7. **初始化密码** — 设置 admin / admin123

## 前置条件

- Windows 已安装 **WSL2 + Ubuntu 发行版**
- 项目存在于 Windows 文件系统上（任意位置）

## 输出

| 服务 | 地址 |
|------|------|
| 前端 | http://localhost |
| 后端 API | http://localhost:8080/api/v1 |
| API 文档 | http://localhost:8080/api/v1/doc.html |
| MinIO | http://localhost:9001 |
| 管理员 | admin / admin123 |
