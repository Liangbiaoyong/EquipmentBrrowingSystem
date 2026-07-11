# 设备借用分类 + 实验室管理 — 设计文档

| 版本 | 日期 | 修订内容 | 作者 |
|------|------|----------|------|
| V1.0 | 2026-07-11 | 初始版 | 开发团队 |

---

## 1. 功能概述

本次开发包含两个独立但相关的功能：

### 1.1 设备借用分类

在设备浏览时增加「借用类型」分类筛选，将设备区分为：
- **可现场借用（1）**：只能在实验室内现场使用，不可带走
- **可借出（2）**：可借出实验室使用（包含现场使用），**默认值**

影响设备列表查询、详情显示、新增/编辑表单。

### 1.2 实验室管理

建立实验室数据体系，包含：
1. **实验室定义** — 管理实验室名称、编码等信息
2. **地点映射** — 将32个房间/地点映射到对应实验室
3. **设备关联** — 每台设备可关联一个实验室（自动从地点匹配或手动指定）
4. **统一修改** — 修改地点→实验室映射后，自动同步关联设备

---

## 2. 数据库设计

### 2.1 新表：`laboratory`（实验室表）

```sql
CREATE TABLE IF NOT EXISTS `laboratory` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL COMMENT '实验室名称',
  `code` varchar(50) DEFAULT NULL COMMENT '实验室编码',
  `location_prefix` varchar(200) DEFAULT NULL COMMENT '存放地前缀（如一教楼＞5＞）',
  `description` text COMMENT '实验室描述',
  `status` tinyint DEFAULT '1' COMMENT '1启用 0禁用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='实验室表';
```

### 2.2 新表：`laboratory_room`（实验室地点映射表）

```sql
CREATE TABLE IF NOT EXISTS `laboratory_room` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `laboratory_id` bigint NOT NULL COMMENT '关联实验室ID',
  `room_name` varchar(100) NOT NULL COMMENT '房间/地点简称（如工程南501）',
  `full_location` varchar(500) DEFAULT NULL COMMENT '完整存放地名称',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_laboratory` (`laboratory_id`),
  KEY `idx_room_name` (`room_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='实验室地点映射表';
```

### 2.3 表变更：`device` 新增字段

```sql
ALTER TABLE `device`
  ADD COLUMN `borrow_type` tinyint DEFAULT 2 COMMENT '借用类型: 1可现场借用 2可借出（默认）' AFTER `status`,
  ADD COLUMN `laboratory_id` bigint DEFAULT NULL COMMENT '所属实验室ID' AFTER `location`,
  ADD KEY `idx_borrow_type` (`borrow_type`),
  ADD KEY `idx_laboratory` (`laboratory_id`);
```

### 2.4 E-R 关系更新

```
laboratory 1──N laboratory_room
laboratory 1──N device
```

---

## 3. API 设计

### 3.1 设备接口变更

| 方法 | 路径 | 变更说明 |
|------|------|----------|
| GET | /devices | 新增请求参数 `borrowType`、`laboratoryId` |
| GET | /devices/{id} | 返回新增 `borrowType`、`laboratoryName` 字段 |
| PUT | /devices/{id} | 请求体支持 `borrowType`、`laboratoryId` |

### 3.2 实验室管理接口（新增）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /laboratories | 实验室列表（分页+关键词） |
| GET | /laboratories/list | 实验室下拉列表（全部启用） |
| GET | /laboratories/{id} | 实验室详情（含关联房间列表） |
| POST | /laboratories | 新增实验室 |
| PUT | /laboratories/{id} | 编辑实验室 |
| DELETE | /laboratories/{id} | 删除实验室 |
| GET | /laboratories/rooms | 地点映射列表（分页+实验室筛选） |
| POST | /laboratories/rooms | 新增地点映射 |
| PUT | /laboratories/rooms/{id} | 编辑地点映射 |
| DELETE | /laboratories/rooms/{id} | 删除地点映射 |
| POST | /laboratories/sync-devices | 同步所有设备的地点→实验室映射（按当前映射规则批量更新） |
| GET | /laboratories/locations | 获取所有不重复的存放地列表（用于映射配置） |

---

## 4. 权限设计

新增权限标识：

| 权限 | 说明 | 分配角色 |
|------|------|----------|
| `laboratory:view` | 查看实验室信息 | 所有人（含学生） |
| `laboratory:manage` | 管理实验室（CRUD+映射） | LAB_ADMIN, SYSTEM_ADMIN |

权限常量 `PermissionConstants` 中新增：
- `public static final String LAB_VIEW = "laboratory:view";`
- `public static final String LAB_MANAGE = "laboratory:manage";`

---

## 5. 初始化数据

### 实验室映射数据（32条）

根据用户提供的房间→实验室映射关系，导入到 `laboratory_room` 表。

实验室列表：

| 实验室名称 | 关联房间 |
|------------|----------|
| 建成环境气候模拟实验室 | 工程南501 |
| 声振环境实验室 | 工程北623 |
| 建筑光学与暗夜保护实验室 | 工程南530 |
| 数字孪生与空间模型实验室 | 工程北504 |
| 资源与环境智慧测评实验室 | 工程北503 |
| （其他未命名的房间归入"待分配实验室"或null） |

---

## 6. 同步策略

**场景：** 管理员修改了地点→实验室的映射关系后，已有设备需要更新。

**方案：** 提供手动触发的 `/laboratories/sync-devices` 接口：
1. 遍历所有 `device` 记录
2. 对每条记录的 `location` 字段，在 `laboratory_room` 中查找匹配的 `room_name`
3. 若找到且记录的 `laboratory_id` 与映射不同，更新 `laboratory_id`
4. 若 `laboratory_id` 已被手动指定且与自动映射不同，**不覆盖**（保留手动设置）

**匹配规则：** 设备 `location` 包含 `laboratory_room.room_name` 即视为匹配。
