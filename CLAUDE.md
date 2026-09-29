# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

广州大学仪器共享平台 — 集设备管理、在线预约、多级审批、归还核验、数据统计于一体的 Web 系统。

- **后端**: Spring Boot 2.7.x + MyBatis-Plus + Spring Security + JWT + CAS Client
- **前端**: Vue 3 + Vite + Pinia + Element Plus
- **数据库**: MySQL 8.0 + Redis 7
- **文件存储**: MinIO (自建 S3 兼容对象存储)
- **部署**: Docker + Docker Compose + Nginx

## 目录结构

```
EquipmentBrrowingSystem/
├── backend/                       # Spring Boot 后端
│   ├── src/main/java/com/gzhu/equipment/
│   │   ├── DeviceBorrowApplication.java   # 启动类（@EnableScheduling）
│   │   ├── common/                        # R统一返回、PageParam、GlobalExceptionHandler
│   │   ├── config/                        # Security / MinIO / Knife4j / CORS 配置
│   │   ├── controller/                    # REST 接口层（待实现）
│   │   ├── service/                       # 业务逻辑层（待实现）
│   │   ├── mapper/                        # MyBatis-Plus Mapper（待实现）
│   │   └── entity/                        # 数据库实体（待实现）
│   ├── src/main/resources/
│   │   ├── application.yml                # 主配置（激活 dev profile）
│   │   ├── application-dev.yml            # 本地开发配置（直连 localhost）
│   │   └── application-prod.yml           # 生产配置（环境变量注入）
│   ├── src/test/java/                     # 单元测试目录
│   ├── Dockerfile                         # 多阶段构建（Maven → JRE）
│   └── pom.xml
├── frontend/                      # Vue 3 前端
│   ├── src/
│   │   ├── api/                   # request.js（Axios封装） + auth/device/borrow
│   │   ├── router/index.js        # 路由定义 + 导航守卫
│   │   ├── store/                 # Pinia（index + user 模块）
│   │   ├── views/                 # Login / Layout / Dashboard + 占位页
│   │   └── App.vue / main.js
│   ├── Dockerfile + nginx.conf    # 多阶段构建（Node → Nginx）
│   ├── vite.config.js             # 开发代理到 8080
│   └── package.json
├── sql/init/
│   ├── 01-schema.sql              # 17 张核心表 DDL（含 V4/V5）
│   ├── 02-data.sql                # 初始管理员 + 4用户 + 10分类 + 实验室
│   ├── 03-test-data.sql           # 测试数据生成（每天5~50条，一个月）
│   ├── 03-update-v2.sql           # V2迁移
│   ├── 04-update-v3-status.sql    # V3设备状态迁移
│   ├── 05-update-v4-purpose-outcome.sql     # V4借用目的+成果
│   ├── 06-update-v4-enhanced-purpose-outcome.sql  # V4增强
│   └── 07-update-v5-category-descriptions.sql     # V5分类描述
├── docs/                          # 需求/设计/开发/测试/用户文档
├── tests/                         # 集成/E2E/性能测试
├── docker-compose.yml             # MySQL + Redis + MinIO + 后端 + 前端
├── .env.example                   # 环境变量模板
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

- **包基础路径**: `com.gzhu.equipment`
- **API 前缀**: 所有接口以 `/api/v1` 开头
- **认证**: 请求头携带 `Authorization: Bearer <JWT>`，无状态认证
- **鉴权**: 方法级 `@PreAuthorize` 注解，角色分 `STUDENT` / `TEACHER` / `LAB_ADMIN` / `SYSTEM_ADMIN`
- **响应格式**: 统一使用 `R<T>` 泛型封装（`{code, msg, data}`）
- **图片处理**: 上传使用 Thumbnailator 压缩至 1920px 以内、5MB 以下
- **分页**: 使用 MyBatis-Plus `Page` 对象，前端传 `page` / `size`
- **多环境**: `application.yml` 激活 profile，`-dev` 直连 localhost，`-prod` 全部环境变量注入
- **设备状态**: 可借用(1) / 借用中(2) / 维修中(3) / 待报废(4)
- **借用状态**: PENDING_APPROVAL / APPROVED / REJECTED / BORROWING / RETURNED / OVERDUE / CANCELLED

### 前端

- **组件库**: Element Plus，使用 `<el-xxx>` 标签
- **状态管理**: Pinia，按模块拆分 store
- **HTTP 请求**: 统一使用 `src/api/` 下的封装，自动处理 JWT 注入和 401 跳转
- **路由守卫**: `src/router/` 中处理未认证跳转和角色权限

### 数据库

- **引擎**: InnoDB，字符集 `utf8mb4`
- **命名**: 表名 `snake_case`，字段 `snake_case`，主键 `id`，时间字段 `xxx_time`
- **核心表（17张）**: `sys_user` / `device_category` / `device` / `device_image` / `borrow_record` / `borrow_outcome` / `approval_log` / `attachment` / `notification` / `sys_log` / `category_mapping` / `category_description` / `laboratory` / `laboratory_room` / `repair_record` / `system_config` / `borrow_outcome`
- **软删除**: 不使用逻辑删除，关键记录永久保留
- **utf8mb4 加固**: MySQL 服务端 `MYSQL_CHARACTER_SET_SERVER=utf8mb4` + JDBC `characterEncoding=UTF-8&useUnicode=true` + CLI 导入 `--default-character-set=utf8mb4`

### 时区（全链路中国时区）

系统统一使用 `Asia/Shanghai`，四层都要一致，缺一层就会整体偏移 8 小时：

| 层 | 位置 | 说明 |
|:--|:-----|:-----|
| 容器 | `docker-compose.yml` 各服务 `TZ: Asia/Shanghai` | 决定容器内 `date`、进程默认时区 |
| JVM | `backend/Dockerfile` 的 `ENV TZ` + `-Duser.timezone` | 决定 `LocalDateTime.now()` 与**日志时间戳** |
| MySQL | mysql 服务 `--default-time-zone=+08:00` | 决定 `NOW()` / `CURRENT_TIMESTAMP` 及 datetime 列默认值 |
| 应用序列化 | `application-*.yml` 的 `spring.jackson.time-zone` | 决定 `Date`/`Instant` 的 JSON 输出 |

- **数据库里存的是「本地墙钟时间」**：`LocalDateTime` 不做时区换算，Java 写入与 MySQL 写入的口径都必须是中国时间
- **前端禁止用 `Date#toISOString()` 生成要提交给后端的时间**（它按 UTC 输出）——用 `frontend/src/utils/datetime.js` 的 `toLocalDateTime` / `todayRange` / `toLocalDate`
- 宿主机建议一并 `timedatectl set-timezone Asia/Shanghai`，保证宿主机日志与 cron 口径一致


### 审批流

- 默认两级：审批人（申请人指定教师）→ 审核员（实验室管理员）
- 可配置三级：开启后增加「最终确认」节点
- 审批流定义以 JSON 快照存入 `borrow_record.approve_flow_def`

### 图片存储策略

- MinIO 路径: 设备图片 `device-images/`（永久），借用归还图片 `borrow-images/{yyyy-MM}/`（半年）
- 清理任务: `@Scheduled(cron = "0 0 3 * * ?")` 每天凌晨 3 点执行

### 部署

- **远程服务器**: `gzhu-server.ydns.eu`（公网裸暴露），SSH `root@gzhu-server.ydns.eu`
- **项目目录**: `/home/hp506/server/EquipmentBrrowingSystem`
- **完整部署方式**: `docker compose up -d --build`
- **5 个容器**: `dev-mysql`(3306) / `dev-redis`(6379) / `dev-minio`(9000/9001) / `dev-backend`(8080) / `dev-frontend`(80)
- **访问地址**: http://gzhu-server.ydns.eu (前端) / http://gzhu-server.ydns.eu:8080 (后端API)
- **生产环境配置文件**: `application-prod.yml`，通过环境变量注入
- `.env` 文件存储敏感信息（数据库密码、JWT密钥等），不提交到仓库
- **Docker Hub 镜像加速**: 已配置 DaoCloud / 南京大学镜像（国内服务器必备）
- **测试账号**: `admin/admin123`(系统管理员) / `student01/admin123`(学生) / `teacher01/admin123`(教师) / `labadmin/admin123`(实验室管理员)
- **一键部署脚本**: `deploy-remote.sh` (Linux/Mac) / `run-deploy.bat` (Windows)
- **初始化流程**: `01-schema.sql` → `02-data.sql` → `07-update-v5-category-descriptions.sql` → `03-test-data.sql`（按文件名排序自动执行）
- **一次性数据迁移**: `sql/migrations/` 目录**不会**被初始化流程自动执行，需手工导入。目前有两支：
  - `16-timezone-cst-shift.sql` — 把 V14 之前由**系统生成**的时间（`NOW()`/`CURRENT_TIMESTAMP`/`LocalDateTime.now()`）整体 +8 小时。
    注意：`03-test-data.sql` 里 `start_time`/`end_time`/`real_return_time`/`outcome_recorded_time`/`create_time`/
    `approval_log.operate_time` 是**脚本写死的字面量，本来就是本地时间**，脚本已按种子行特征（`SECOND(create_time)=0
    AND create_time<>update_time`）将其排除，不做平移。
    仅在**存量数据**的库上执行，全新初始化的库无需执行（否则会把正确数据推快 8 小时）。
  - `17-timezone-fix-authored-columns.sql` — **仅为「2026-09-29 之前版本的 16 号脚本已执行过」的库准备的回退**。
    那一版误把上述字面量列也 +8 小时。**全新环境不要执行本脚本**（跑修正版 16 号即可）。
  - `18-create-overdue-record.sql` — 补建缺失的 `overdue_record` 表。
    该表由 `sql/init/08-update-v6-overdue.sql` 创建，但 `sql/init/` **只在数据库首次初始化时执行一次**；
    若该文件是在卷已存在之后才加入仓库，线上库就会缺表，导致 `/borrows/overdue/stats` 抛 SQL 异常返回 500
    （表现为逾期管理页统计全为 0 + 「服务器内部错误」）。全新环境无需执行本脚本。
    两支脚本都以 `system_config` 中的标记做幂等保护，重复执行不会二次平移。
    ```bash
    docker exec -i dev-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" \
      --default-character-set=utf8mb4 device_borrow < sql/migrations/16-timezone-cst-shift.sql
    ```

> **`sql/init/` 只跑一次**：目录挂载到 `/docker-entrypoint-initdb.d`，仅在数据目录为空时执行。
> 后续往该目录新增脚本**不会**对已有卷生效 —— 这类补丁必须写成 `sql/migrations/` 下的手工脚本。
> 另注意：MySQL 8 **不支持** `ALTER TABLE ... ADD COLUMN IF NOT EXISTS`（那是 MariaDB 语法），
> 迁移脚本里不要用；需要幂等就靠建表时的 `CREATE TABLE IF NOT EXISTS` 或 information_schema 判断。


### ⚠️ 部署关键经验

| 问题 | 原因 | 解决 |
|:----|:-----|:-----|
| CAS 登录 `ClassNotFoundException` | `TEMP/` 目录不在 git 中，volume 未挂载 | `scp -r ./TEMP` 到远程 + `docker-compose.yml` 加 `- ./TEMP:/app/TEMP` |
| 新增表 `Table doesn't exist` | MySQL volume 持久化后不重跑 init 脚本 | 手动 `CREATE TABLE` 或重建 volume |
| 功能未更新 | `git pull` 后构建用缓存 | 用 `--no-cache` 确保重新编译 |
| 批量导入/新增规则无效 | 前端表单无默认值，`minYears` 为空 | 设置 `ruleForm={minYears:6, priority:100}` |
| 界面/日志时间比北京慢 8 小时 | 容器与 JVM 未设时区，跑在 UTC | `docker-compose.yml` 加 `TZ`、Dockerfile 加 `ENV TZ` 与 `-Duser.timezone`、MySQL 加 `--default-time-zone=+08:00`；存量数据跑 `sql/migrations/16-timezone-cst-shift.sql` |

**部署三步检查**：
1. ✅ 代码完整 — `git pull --ff-only`，检查 commit 一致
2. ✅ 容器配置 — `docker-compose.yml` volume 覆盖所有运行时依赖（尤其是 `TEMP/`）
3. ✅ 数据库结构 — `SHOW TABLES` 对比本地 `sql/init/`，补全缺失表

## 测试策略

- **单元测试**: 后端 Service 层使用 JUnit5 + Mockito，**217 个测试**（Controller 13个文件 + Service 8个文件 + Security 3个文件）
- **测试命令**: `cd backend && mvn test`（全部通过方可提交）
- **测试数据生成**: `sql/init/03-test-data.sql` — 生成最近一个月每天5~50条随机借用记录（含审批记录+成果数据）
- **运行测试数据**: `docker exec -i dev-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" --default-character-set=utf8mb4 device_borrow < sql/init/03-test-data.sql`
- **性能测试**: JMeter 脚本模拟 30 并发用户
- **安全测试**: 测试越权访问、XSS、文件上传漏洞
- **提交前检查**: `mvn test` 通过 + `npm run lint` 无报错

## 远程仓库

项目使用 Git 进行版本管理。每完成一个重要功能开发或重大问题修复后，自动推送到远程仓库。
