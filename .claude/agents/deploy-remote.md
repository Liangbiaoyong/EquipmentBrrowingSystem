---
name: deploy-remote
description: 远程SSH服务器一键部署 — Docker镜像构建+传输+启动，含完整故障排除
---

# deploy-remote — 远程 SSH 服务器一键部署

## 触发词

- "部署到远程服务器"
- "部署到公网"
- "远程部署"
- "SSH 部署"

## 流程

### 常规部署

```bash
# 构建 Docker 镜像
docker compose build backend frontend

# 传输镜像到远程
docker save app-backend:latest | ssh user@host 'docker load'
docker save app-frontend:latest | ssh user@host 'docker load'

# 同步配置文件
scp docker-compose.yml user@host:~/project/
scp frontend/nginx.conf user@host:~/project/frontend/
scp -r sql/init/ user@host:~/project/sql/

# 远程修改 compose 使用预构建镜像（远程无源码）
ssh user@host "sed -i 's|build: ./backend|image: app-backend:latest|' docker-compose.yml"
ssh user@host "sed -i 's|build: ./frontend|image: app-frontend:latest|' docker-compose.yml"

# 远程启动
ssh user@host "cd ~/project && docker compose up -d"
```

## 已知故障与排除

### 1. 端口 80 被占用

**现象**：`docker compose up -d frontend` 报 `bind: address already in use`

**原因**：宿主机可能运行了 nginx（`systemctl status nginx`）监听 80/8080 端口。

**处理**：
```bash
ssh root@host 'systemctl stop nginx && systemctl disable nginx'
# 如果仍有残留进程
fuser -k 80/tcp
docker compose up -d frontend
```

### 2. Docker Hub 拉取失败（教育网 IPv6 问题）

**现象**：`docker pull mysql:8.0` 报 `connection reset by peer`，IPv6 地址超时

**原因**：教育网环境（广州大学）IPv6 到 Docker Hub 不稳定

**处理**：从本地 pipe 传输所有镜像到远程
```bash
# 先确认本地有哪些镜像
docker images

# 逐一传输
docker save mysql:8.0 | ssh root@host 'docker load'
docker save redis:7-alpine | ssh root@host 'docker load'
docker save minio/minio:latest | ssh root@host 'docker load'
docker save app-backend:latest | ssh root@host 'docker load'
docker save app-frontend:latest | ssh root@host 'docker load'
```

### 3. SSL 证书不可用

**问题**：
- Let's Encrypt 无法验证（教育网 80 端口对 CA 服务器不可达）
- 443 端口无法绑定或防火墙拦截

**处理**：
1. 检查端口可达性：`curl -s http://外网IP:80/.well-known/acme-challenge/test`
2. 如果公网端口不可达 → 切换到纯 HTTP 模式
3. 修改 `frontend/nginx.conf` 为纯 HTTP，去掉 SSL 配置和 `443:443` 端口映射
4. 重建前端镜像并传输

```nginx
server {
    listen 80;
    server_name localhost;
    # ... 标准 HTTP 配置，不含 SSL
}
```

### 4. 浏览器缓存 HTTPS 重定向

**现象**：切换到 HTTP 后浏览器仍然跳转 HTTPS

**原因**：`301 Moved Permanently` 会被浏览器永久缓存

**处理**：清除浏览器缓存或用无痕模式测试

### 5. 后端 JWT 密钥太短

**现象**：`jwt.secret 长度不足: 当前 X 字节，HMAC-SHA512 要求至少 64 字节`

**处理**：在远程服务器的 `.env` 文件中设置足够长的密钥（>64 字符）

### 6. docker-compose.yml 远程无 build 上下文

**现象**：`unable to prepare context: path "./backend" not found`

**处理**：远程必须用 `image:` 而非 `build:`：
```bash
sed -i 's|build: ./backend|image: app-backend:latest|' docker-compose.yml
sed -i 's|build: ./frontend|image: app-frontend:latest|' docker-compose.yml
```

### 7. .env 变更后 docker compose restart 不生效

**现象**：修改 `.env` 后重启容器，环境变量未更新

**原因**：`docker compose restart` 不重新加载 `.env`

**处理**：必须 `docker compose up -d` 重新创建容器（或 `docker compose rm -fs` 后 `up -d`）

### 8. 容器端口映射异常

**现象**：`docker port dev-frontend 80` 显示 `{invalid IP 80}`

**处理**：`docker compose down frontend && docker compose up -d frontend` 重建

## 最佳实践

| 原则 | 说明 |
|:----|------|
| **增量传输** | 每次只传输修改过的镜像（前端改动只传前端镜像），避免 1.2GB 重复传输 |
| **先停 host nginx** | 部署前检查 `ss -tlnp | grep :80`，确保端口可用 |
| **预构建镜像** | 本地构建好再传输，远程不需有源码和 Maven/npm |
| **同步修改** | 远程修改后（如 `docker-compose.yml`）要 scp 回本地提交 |
| **curl 验证** | 每次部署后用 `curl -sI http://域名/` 确认 HTTP 200 |
