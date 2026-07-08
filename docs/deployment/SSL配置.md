# SSL 证书配置指南

## 为什么需要 HTTPS

- 后端 API 使用 JWT token 认证，HTTPS 防止 token 在传输中被截获
- WebSocket 连接（通知推送）在 HTTPS 下使用 WSS 协议
- CAS 登录回调要求回调地址为 HTTPS（部分浏览器策略）

## 方案一：mkcert（推荐，已配置）

mkcert 是一个简单的工具，用于创建本地信任的开发证书。本项目已使用 mkcert 生成证书并配置 HTTPS。

### 1. 证书文件

```
certs/
├── localhost.pem          # SSL 证书（mkcert 生成）
├── localhost-key.pem      # 证书私钥
└── mkcert-rootCA.pem      # mkcert 根 CA（安装到 Windows 后可消除浏览器警告）
```

### 2. 在 Windows 上信任 mkcert 根 CA

双击 `certs/mkcert-rootCA.pem` → **安装证书** → **本地计算机** → **将所有证书放入下列存储** → **受信任的根证书颁发机构**。

或用命令行（管理员 PowerShell）：
```powershell
certutil -addstore -f Root certs/mkcert-rootCA.pem
```

安装后浏览器打开 `https://localhost` 将不再显示证书警告。

### 3. 重新生成证书（证书过期时）

```bash
# WSL2 Ubuntu 中执行
wsl -u root -d Ubuntu bash -c '
mkcert -install
mkcert -key-file=/app/certs/localhost-key.pem \
       -cert-file=/app/certs/localhost.pem \
       localhost 127.0.0.1 ::1
docker compose build frontend
docker compose up -d frontend
'
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
