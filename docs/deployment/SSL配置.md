# SSL 证书配置指南

## 为什么需要 HTTPS

- 后端 API 使用 JWT token 认证，HTTPS 防止 token 在传输中被截获
- WebSocket 连接（通知推送）在 HTTPS 下使用 WSS 协议
- CAS 登录回调要求回调地址为 HTTPS（部分浏览器策略）

## 方案一：mkcert（推荐）

mkcert 是一个简单的工具，用于创建本地信任的开发证书。

### 1. 安装 mkcert

**Windows**（在 Git Bash / PowerShell 中执行）：
```bash
# 使用 chocolatey
choco install mkcert

# 或手动下载
# 从 https://github.com/FiloSottile/mkcert/releases 下载 mkcert-v1.4.4-windows-amd64.exe
# 重命名为 mkcert.exe 并放入 PATH
```

**WSL2 Ubuntu**（在 WSL2 中执行）：
```bash
sudo apt install libnss3-tools
curl -JLO "https://dl.filippo.io/mkcert/latest?for=linux/amd64"
chmod +x mkcert-v*-linux-amd64
sudo mv mkcert-v*-linux-amd64 /usr/local/bin/mkcert
```

### 2. 生成证书

```bash
# 安装本地 CA（首次运行）
mkcert -install

# 生成 localhost 证书
mkcert -key-file=localhost-key.pem -cert-file=localhost.pem localhost 127.0.0.1 ::1
```

### 3. Nginx 配置 HTTPS

将 `frontend/nginx-ssl.conf` 或修改 `frontend/nginx.conf`：

```nginx
server {
    listen 443 ssl;
    server_name localhost;

    ssl_certificate     /etc/nginx/certs/localhost.pem;
    ssl_certificate_key /etc/nginx/certs/localhost-key.pem;

    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers HIGH:!aNULL:!MD5;

    root /usr/share/nginx/html;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://backend:8080/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location /ws/ {
        proxy_pass http://backend:8080/ws/;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }

    location /api/v1/files/ {
        proxy_pass http://backend:8080/api/v1/files/;
        proxy_cache_valid 200 1d;
    }
}

server {
    listen 80;
    server_name localhost;
    return 301 https://$host$request_uri;
}
```

### 4. Docker Compose 挂载证书

修改 `docker-compose.yml` 中 frontend 服务的 volumes：

```yaml
frontend:
  build: ./frontend
  volumes:
    - ./certs/localhost.pem:/etc/nginx/certs/localhost.pem
    - ./certs/localhost-key.pem:/etc/nginx/certs/localhost-key.pem
  ports:
    - "80:80"
    - "443:443"
```

## 方案二：Chrome/Edge 忽略 localhost 证书警告（临时调试）

在浏览器地址栏输入：
```
chrome://flags/#allow-insecure-localhost
```

将 **Allow invalid certificates for resources loaded from localhost** 设为 **Enabled**，然后重启浏览器。

⚠️ 仅适临时调试，某些 Chrome 版本已移除该标志。

## 方案三：Firefox 添加例外

访问 `https://localhost` → 点击「高级」→「接受风险并继续」。浏览器会记住该例外。

## 为什么 http://localhost 本身不需要证书

HTTP 协议（`http://`）是明文传输，不涉及 SSL/TLS 证书。访问 `http://localhost` 时浏览器不会提示证书问题。若遇到证书警告，说明：

1. 地址栏实际是 `https://localhost`
2. 页面内加载了 HTTPS 资源（混合内容警告）

## 证书文件存放位置

```
EquipmentBrrowingSystem/
├── certs/                    # 证书目录（已 .gitignore）
│   ├── localhost.pem         # 本地开发证书
│   └── localhost-key.pem     # 证书密钥
├── frontend/
│   └── nginx.conf            # HTTP 配置（生产默认）
└── docs/deployment/
    └── SSL配置.md             # 本文件
```

## 验证证书是否生效

```bash
# 确认证书文件存在
ls -la certs/

# 重新构建前端以加载证书
docker compose up -d --build frontend

# 验证 HTTPS 访问
curl -k https://localhost/api/v1/auth/health
```
