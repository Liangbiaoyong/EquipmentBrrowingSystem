# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

广州大学建筑学院设备借用系统 — 集设备管理、在线预约、多级审批、归还核验、数据统计于一体的 Web 系统。

- **后端**: Spring Boot 2.7.x + MyBatis-Plus + Spring Security + JWT + CAS Client
- **前端**: Vue 3 + Vite + Pinia + Element Plus
- **数据库**: MySQL 8.0 + Redis 7
- **文件存储**: MinIO (自建 S3 兼容对象存储)
- **部署**: Docker + Docker Compose + Nginx

## 目录结构

```
EquipmentBrrowingSystem/
├── backend/               # Spring Boot 后端
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/gzhu/equipment/
│   │   │   │   ├── controller/     # REST 接口层
│   │   │   │   ├── service/        # 业务逻辑层
│   │   │   │   ├── mapper/         # MyBatis-Plus 数据访问
│   │   │   │   ├── entity/         # 数据库实体
│   │   │   │   ├── dto/            # 数据传输对象
│   │   │   │   ├── config/         # 安全/跨域/Swagger 配置
│   │   │   │   └── util/           # 工具类（图片压缩、日期）
│   │   │   └── resources/
│   │   └── test/                   # 单元测试 (JUnit5 + Mockito)
│   ├── Dockerfile
│   └── pom.xml
├── frontend/              # Vue 3 前端
│   ├── src/
│   │   ├── api/           # Axios 接口封装
│   │   ├── components/    # 公共组件
│   │   ├── views/         # 页面
│   │   ├── router/        # 路由守卫
│   │   ├── store/         # Pinia 状态管理
│   │   └── utils/         # 工具函数
│   ├── Dockerfile
│   └── package.json
├── docs/                  # 项目文档
│   ├── requirements/      # 需求文档
│   ├── design/            # 设计文档（含数据库、API、架构图）
│   ├── development/       # 开发文档
│   ├── testing/           # 测试文档
│   └── user/              # 用户手册
├── tests/                 # 集成/端到端/E2E 测试
├── sql/                   # SQL 初始化脚本
│   └── init/
├── docker-compose.yml     # 完整编排文件
├── .env.example           # 环境变量模板
└── CLAUDE.md
```

## 常用命令

### 后端

```bash
# 构建（跳过测试）
cd backend && mvn clean package -DskipTests

# 运行测试
cd backend && mvn test

# 运行单个测试类
cd backend && mvn test -Dtest=UserServiceTest

# 运行单个测试方法
cd backend && mvn test -Dtest=UserServiceTest#testLogin

# 本地启动开发服务器（默认 8080）
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 代码生成器（MyBatis-Plus Generator）
cd backend && mvn mybatis-plus:generate
```

### 前端

```bash
# 安装依赖
cd frontend && npm install

# 开发服务器（默认 5173，代理到后端 8080）
cd frontend && npm run dev

# 构建
cd frontend && npm run build

# 代码检查
cd frontend && npm run lint

# 类型检查
cd frontend && npm run type-check
```

### Docker

```bash
# 完整部署（构建并启动所有服务）
docker compose up -d --build

# 仅启动基础设施（MySQL + Redis + MinIO），用于本地开发
docker compose up -d mysql redis minio

# 查看日志
docker compose logs -f backend

# 停止
docker compose down

# 停止并删除数据卷
docker compose down -v
```

### 本地开发工作流（热更新）

```bash
# 终端1：启动基础设施（只需一次）
docker compose up -d mysql redis minio

# 终端2：启动后端（热更新）
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 终端3：启动前端（热更新，代理到后端 8080）
cd frontend && npm run dev
```

> 本地开发时只容器化基础设施，后端和前端以开发进程运行获得热更新。
> 打包部署时执行 `docker compose up -d --build` 构建所有镜像。

### Git（每完成阶段性成果后自动推送）

```bash
git add -A && git commit -m "<type>: <description>" && git push
```

提交类型前缀：`feat` / `fix` / `docs` / `refactor` / `test` / `chore`

## 开发约定

### 后端
- **API 前缀**: 所有接口以 `/api/v1` 开头
- **认证**: 请求头携带 `Authorization: Bearer <JWT>`，无状态认证
- **鉴权**: 方法级 `@PreAuthorize` 注解，角色分 `STUDENT` / `TEACHER` / `LAB_ADMIN` / `SYSTEM_ADMIN`
- **响应格式**: 统一使用 `R<T>` 泛型封装（`{code, msg, data}`）
- **图片处理**: 上传使用 Thumbnailator 压缩至 1920px 以内、5MB 以下
- **分页**: 使用 MyBatis-Plus `Page` 对象，前端传 `page` / `size`
- **设备状态**: 可用(1) / 已借出(2) / 维修中(3) / 报废(4)
- **借用状态**: PENDING_APPROVAL / APPROVED / REJECTED / BORROWING / RETURNED / OVERDUE / CANCELLED

### 前端
- **组件库**: Element Plus，使用 `<el-xxx>` 标签
- **状态管理**: Pinia，按模块拆分 store
- **HTTP 请求**: 统一使用 `src/api/` 下的封装，自动处理 JWT 注入和 401 跳转
- **路由守卫**: `src/router/` 中处理未认证跳转和角色权限

### 数据库
- **引擎**: InnoDB，字符集 `utf8mb4`
- **命名**: 表名 `snake_case`，字段 `snake_case`，主键 `id`，时间字段 `xxx_time`
- **核心表**: `sys_user` / `device` / `device_image` / `borrow_record` / `approval_log` / `attachment` / `notification`
- **软删除**: 不使用逻辑删除，关键记录永久保留

### 审批流
- 默认两级：审批人（申请人指定教师）→ 审核员（实验室管理员）
- 可配置三级：开启后增加「最终确认」节点
- 审批流定义以 JSON 快照存入 `borrow_record.approve_flow_def`

### 图片存储策略
- MinIO 路径: 设备图片 `device-images/`（永久），借用归还图片 `borrow-images/{yyyy-MM}/`（半年）
- 清理任务: `@Scheduled(cron = "0 0 3 * * ?")` 每天凌晨 3 点执行

### 部署
- 完整部署方式: `docker compose up -d --build`
- 生产环境配置文件: `application-prod.yml`，通过环境变量注入
- `.env` 文件存储敏感信息（数据库密码、JWT密钥等），不提交到仓库

## 测试策略

- **单元测试**: 后端 Service 层使用 JUnit5 + Mockito，目标覆盖率 > 70%
- **集成测试**: `tests/` 目录存放端到端或 E2E 测试
- **性能测试**: JMeter 脚本模拟 30 并发用户
- **安全测试**: 测试越权访问、XSS、文件上传漏洞
- **提交前检查**: `mvn test` 通过 + `npm run lint` 无报错

## 远程仓库

项目使用 Git 进行版本管理。每完成一个重要功能开发或重大问题修复后，自动推送到远程仓库。
