# 前端 API 对接文档

| 版本 | 日期 | 说明 |
|------|------|------|
| V3.1 | 2026-07-11 | 维修管理完善(完整生命周期+设备状态流转)+逾期管理+侧边栏重组 |
| V2.1 | 2026-07-11 | 批量导入修复：GBK编码检测 + 智能导入(主键识别+增量更新) |
| V1.0 | 2026-07-07 | 初始版本，覆盖所有后端已实现接口 |
| V2.1 | 2026-07-11 | 批量导入修复：GBK编码检测 + 智能导入(主键识别+增量更新) |

---

## 一、通用约定

### 基础路径

```
开发环境: http://localhost:8080/api/v1
```

### 认证方式

```
请求头: Authorization: Bearer <JWT>
```

JWT 有效期 4 小时，过期需重新登录。登录接口返回的 `accessToken` 存储到 localStorage。

### 统一响应格式

```json
{
  "code": 200,       // 200正常 / 401未登录 / 404不存在 / 500异常
  "msg": "操作成功",
  "data": { ... }    // 业务数据，可能为 null
}
```

前端拦截器规则：
- `code === 401` → 清除 token → 跳转登录页
- `code === 200` → 正常
- 其他 → toast 提示 `msg`

### 权限标识

登录后从 `/auth/info` 获取 `permissions` 数组，格式为 `module:action`：

```json
["dashboard:view", "device:view", "borrow:create", "borrow:my", ...]
```

路由守卫: `to.meta.permissions` 与 `userStore.permissions` 取交集，为空则跳 `/403`。

---

## 二、认证模块 `/auth`

### 1. CAS 登录（推荐 — 服务端无感登录）

前端直接提交CAS用户名+密码，后端完成CAS协议交互（bootstrap引导 → 密码RSA加密 → POST登录 → 跟随跳转提取token → 调userInfo API验证）。

```
POST /auth/cas/credential-login
Content-Type: application/json

请求:
{
  "username": "2022010101",    // 学工号
  "password": "xxxxx"          // CAS统一认证密码
}

响应:
{
  "code": 200,
  "msg": "CAS登录成功",
  "data": {
    "accessToken": "eyJhbGciOiJIUzUx...",
    "tokenType": "Bearer",
    "expiresIn": 14400000,
    "userInfo": {
      "id": 1,
      "username": "2022010101",
      "realName": "张三",
      "userType": 0,
      "userTypeName": "学生",
      "department": "建筑学院",
      "className": "建筑学191",
      "authSource": "C",
      "roles": ["STUDENT"],
      "permissions": ["dashboard:view", "device:view", "borrow:create", ...]
    }
  }
}
```

错误：`{ "code": 401, "msg": "CAS登录失败: 账号或密码错误" }`

### 2. CAS 登录（备用 — token+cookie 方式）

前端完成CAS浏览器跳转后，将回调URL中提取的token传给后端验证。

```
POST /auth/cas/login
Content-Type: application/json

请求:
{
  "token": "从CAS回调中提取的token",
  "cookies": "JSESSIONID=xxx; route=xxx"  // 可选
}

响应: 同上
```

### 3. 本地登录（管理员）

```
POST /auth/local/login
{ "username": "admin", "password": "admin123" }
```

响应格式同上。密码错误时 `code=401, msg="用户名或密码错误"`。

### 4. 获取当前用户信息

```
GET /auth/info
Authorization: Bearer <token>

响应: 同登录返回的 userInfo（permissions 用于路由守卫和菜单渲染）
```

### 5. 登出

```
POST /auth/logout
Authorization: Bearer <token>

响应: { "code": 200, "msg": "已登出" }
```

### 6. 健康检查

```
GET /auth/health  → 无需认证
```

---

## 三、设备管理 `/devices`

### 1. 分页查询设备列表

```
GET /devices?page=1&size=20&keyword=&categoryId=&status=&location=&gbCategoryName=

参数（全部可选）:
  page           默认1
  size           默认20
  keyword        搜索关键词（匹配名称/资产编号/型号）
  categoryId     业务分类ID（1=计算机 2=摄影摄像 3=音频 4=空调 5=仪器仪表
                              6=家具 7=无人机 8=软件 9=安全监控 10=其他）
  borrowStatus   借还状态: 1可借用 2借用中 3不可借 4逾期（V3替换旧status）
  deviceStatus   设备状态: 1正常 2待维修 3维修中 4待报废 5已报废（V3新增）
  borrowType     借用类型: 1可现场借用 2可借出（V2新增）
  laboratoryId   所属实验室ID（V2新增）
  location       存放地（模糊匹配）

响应:
{
  "code": 200,
  "data": {
    "records": [ Device对象数组 ],
    "total": 2469,
    "current": 1,
    "size": 20
  }
}
```

### 2. 设备详情（含图片+借用状态）

```
GET /devices/{id}

响应:
{
  "code": 200,
  "data": {
    "device": {                     // 完整的Device对象
      "id": 1,
      "assetNo": "2026002840",
      "name": "移动工作站",
      "model": "联想:ThinkPad X1",
      "specs": "Ultra7-255H 32GB...",
      "categoryId": 1,
      "location": "工程实验楼南楼520",
      "department": "建筑与城市规划学院",
      "totalQty": 1,
      "availableQty": 1,
      "unitPrice": 17500.00,
      "status": 1,
      "gbCategoryName": "其他计算机",
      ...
    },
    "images": [                     // 设备图片列表
      { "id": 1, "imageUrl": "device-images/xxx.jpg", "sort": 0 }
    ],
    "categoryName": "计算机及外设",
    "isBorrowing": false,           // 当前是否被借出
    "currentBorrower": null,        // 当前借用人
    "expectedReturnTime": null,     // 预计归还时间
    "borrowCount": 12,              // 历史借用次数
    "borrowType": 2,                // 借用类型: 1可现场借用 2可借出
    "laboratoryName": "建成环境气候模拟实验室"  // 所属实验室名称
  }
}
```

### 3. 按资产编号查询

```
GET /devices/by-asset-no/{assetNo}
```

### 4. 更新设备（V2支持borrowType和laboratoryId）

```
PUT /devices/{id}
Content-Type: application/json
权限: device:manage

请求体: Device对象（JSON），新增字段:
  borrowType    借用类型: 1可现场借用 2可借出
  laboratoryId  所属实验室ID
```

### 5. 删除设备

```
DELETE /devices/{id}
权限: admin:user (SYSTEM_ADMIN)
```

### 6. 批量导入（CSV/XLSX/XLS）【V2更新：智能导入】

```
POST /devices/import
Content-Type: multipart/form-data
权限: device:manage

参数: file (文件，支持 .csv / .xlsx / .xls)

编码说明: CSV自动检测编码（UTF-8/GBK），解决中文Windows导出乱码问题
格式兼容: 同时支持 .xlsx (XSSF) 和 .xls (HSSF) 格式

智能导入逻辑（V2新增）：
1. 以资产编号(asset_no)为主键识别数据
2. 导入时对比新旧数据：
   - 删除：旧数据中在新数据里不存在的设备记录
   - 更新：旧数据中存在的设备，按新数据更新业务字段，但保留旧记录的：
     * borrowType（借用类型）
     * laboratoryId（所属实验室）
     * coverImage（封面图）
     * description（备注/描述，如新数据有则覆盖）
     * defaultApproverId（默认审批人）
     * 关联的图片列表（device_image）
   - 新增：旧数据中没有的新设备，缺失值设置为默认值或空

响应:
{
  "code": 200,
  "data": {
    "totalRows": 2469,          // 新文件中的数据行数
    "successCount": 500,        // 新增的记录数
    "updateCount": 1969,        // 更新的记录数
    "deleteCount": 150,         // 删除的旧记录数（V2新增）
    "failCount": 0,             // 失败的记录数
    "autoCategoryCount": 2300,  // 自动分类命中数
    "uncategorizedCount": 169,  // 未分类数
    "batchId": "a1b2c3d4",
    "errors": []
  }
}
```

### 7. 导入预览（Dry-Run）【V2更新：前端预览前20条+统计信息】

```
POST /devices/import/dry-run
Content-Type: multipart/form-data
权限: device:manage

参数: file (文件)
返回前20条解析结果+分类统计，不写入数据库，不执行删除操作

编码兼容: 同样支持UTF-8/GBK自动检测 + XLSX/XLS格式
```

### 8. CSV 导出

```
GET /devices/export/csv?categoryId=1&batchId=xxx
权限: device:manage
返回: CSV文件下载（Content-Disposition: attachment）
```

### 9. 批次管理

```
GET    /devices/batches              → 所有导入批次列表
GET    /devices/batches/{batchId}    → 按批次查询设备
DELETE /devices/batches/{batchId}    → 按批次清除（admin:user）
```

### 10. 设备快速选择器（V3新增，用于借用申请页）

```
GET /devices/picker?page=1&size=50&keyword=ThinkPad&categoryId=1&borrowType=2

参数:
  page         默认1
  size         默认50
  keyword      搜索关键词（匹配名称/资产编号/型号）
  categoryId   业务分类筛选
  borrowType   借用类型筛选

说明: 仅返回borrowStatus=1(可借用)的设备，精简字段（不含规格/国标/供货商等大字段）
用途: 借用申请页面的设备搜索下拉框
```

---

## 四、设备图片 `/devices`

### 1. 图片列表

```
GET /devices/{deviceId}/images

响应: [ { "id": 1, "deviceId": 1, "imageUrl": "device-images/xxx.jpg", "sort": 0 }, ... ]
```

### 2. 上传图片（MultipartFile，自动压缩）

```
POST /devices/{deviceId}/images/upload
Content-Type: multipart/form-data
权限: device:manage

参数:
  file  图片文件（png/jpg/jpeg）
  sort  排序(可选, 默认0)

流程: 上传 → 压缩(1920px/0.8质量/≤5MB) → MinIO → 写DB → 无封面自动设封面
```

### 3. 添加图片（URL方式，兼容旧接口）

```
POST /devices/{deviceId}/images
Content-Type: application/x-www-form-urlencoded
权限: device:manage

参数: imageUrl, sort(可选)
```

### 4. 删除图片

```
DELETE /devices/images/{id}
权限: device:manage
删除DB记录+MinIO文件。若删除的是封面图则自动用下一张替代。
```

### 5. 缺少图片的设备

```
GET /devices/missing-images?page=1&size=50
权限: device:manage
返回无图片的设备列表（用于管理页面整改）
```

---

## 五、借用审批 `/borrows`

### 1. 提交借用申请

```
POST /borrows
Content-Type: application/json
权限: borrow:create

请求:
{
  "deviceId": 1,
  "startTime": "2026-07-10T09:00:00",
  "endTime": "2026-07-14T17:00:00",    // 最长7天（可配置）
  "reason": "课程设计需要",
  "approverId": 5                         // 指定审批教师ID
}

错误响应:
- "借用时长不能超过 X 天，当前 Y 天"
- "所选时段设备已被占用，请重新选择时间"
- "设备库存不足"
```

### 2. 我的借用列表

```
GET /borrows/my?page=1&size=20&status=
权限: borrow:my

status可选: PENDING_APPROVAL / APPROVED / BORROWING / RETURNED / REJECTED
不传则全部
```

### 3. 借用详情

```
GET /borrows/{id}
权限: borrow:view

响应: BorrowRecord对象 (包含 approveFlowDef 审批流JSON快照)
```

### 4. 取消借用

```
POST /borrows/{id}/cancel
权限: borrow:create
仅PENDING_APPROVAL状态可取消
```

### 5. 一级待审批（教师/Admin）

```
GET /borrows/pending/first?page=1&size=20
权限: approval:first
```

### 6. 二级待审批（实验室管理员/Admin）

```
GET /borrows/pending/second?page=1&size=20
权限: approval:second
```

### 7. 审批操作

```
POST /borrows/approve
Content-Type: application/json
权限: approval:first 或 approval:second

请求:
{
  "borrowId": 1,
  "approved": true,          // true=通过 false=驳回
  "comment": "同意借用"       // 通过可选，驳回必填
}

响应: 更新后的BorrowRecord (status=APPROVED/REJECTED/流转到下一级)
```

### 8. 归还登记

```
POST /borrows/{id}/return
Content-Type: multipart/form-data
权限: borrow:return

参数:
  damageReport  损坏描述（可选）
  file          归还照片（可选，MultipartFile）

归还后: 库存+1 / 有damageReport→设备标记维修中 / 逾期自动计算天数
```

### 9. 逾期列表

```
GET /borrows/overdue?page=1&size=20
权限: return:manage
```

---

## 六、分类管理 `/categories`

### 1. 分类列表

```
GET /categories              → 全部启用分类
GET /categories/top-level    → 仅一级分类
```

### 2. 自动分类测试

```
GET /categories/classify?gbName=其他计算机
响应: "匹配成功: 计算机及外设"  或  "未匹配，将归入「其他设备」"
```

### 3. 映射规则管理

```
GET    /categories/mappings?categoryId=1   → 按分类查规则
POST   /categories/mappings                → 新增规则 { gbCategoryName, keyword, categoryId, priority }
PUT    /categories/mappings/{id}           → 更新规则
DELETE /categories/mappings/{id}           → 删除规则
PUT    /categories/mappings/{id}/toggle    → 启用/禁用
权限: LAB_ADMIN+
```

---

## 七、数据统计 `/statistics`

### 1. 仪表盘概览

```
GET /statistics/overview
权限: dashboard:view

响应:
{
  "deviceStats": { "total": 2469, "normal": 2400, "repair": 50, "scrap": 19 },
  "borrowStats": { "borrowing": 5, "overdue": 2, "pendingApproval": 3, "total": 356 }
}
```

### 2. 本月借用趋势

```
GET /statistics/trend
权限: statistics:view

响应: [ { "date": "2026-07-01", "count": 12 }, { "date": "2026-07-02", "count": 8 }, ... ]
```

### 3. 热门设备 TOP10

```
GET /statistics/top-devices
权限: statistics:view

响应: [ { "deviceName": "移动工作站", "borrowCount": 45 }, ... ]
```

### 4. 高频用户 TOP10

```
GET /statistics/top-users
权限: statistics:view

响应: [ { "userName": "张三", "borrowCount": 23 }, ... ]
```

### 5. 分类利用率

```
GET /statistics/utilization
权限: statistics:view

响应: [ { "categoryName": "计算机及外设", "borrowCount": 120 }, ... ]
```

### 6. 导出报表

```
GET /statistics/export
权限: statistics:view
返回: CSV文件下载
```

---

## 八、通知消息 `/notifications`

### 1. 我的通知

```
GET /notifications?page=1&size=20
权限: notification:view

响应: [ { "id":1, "title":"借用申请已通过", "content":"...", "type":"APPROVAL", "isRead":0 }, ... ]
```

### 2. 未读数量

```
GET /notifications/unread-count
响应: { "unreadCount": 3 }
```

### 3. 标记已读

```
PUT /notifications/{id}/read    → 单条已读
PUT /notifications/read-all     → 全部已读
```

### 4. WebSocket 实时推送

```
连接: ws://localhost:8080/api/v1/ws/notification/{userId}

推送JSON:
{ "type": "APPROVAL", "title": "借用申请已通过", "content": "设备「xxx」..." }

type取值: APPROVAL / REMIND / SYSTEM
```

---

## 九、数据表管理 `/admin/data-tables`（V3 新增）

> **权限分级**：系统管理员可访问所有表（含sys_user/system_config为只读）；实验室管理员仅可访问设备相关表（白名单：device/device_image/borrow_record等14张表）。

### 1. 获取表列表

```
GET /admin/data-tables/tables
权限: admin:user 或 laboratory:manage

响应: [{ "TABLE_NAME":"device", "TABLE_ROWS":2469, "TABLE_COMMENT":"设备/资产表", "SIZE_MB":2.5 }, ...]
```

### 2. 查询表数据（分页+排序+关键词搜索）

```
GET /admin/data-tables/{tableName}?page=1&size=50&sort=id&order=desc&keyword=xxx

参数:
  page     默认1
  size     默认50
  sort     排序列名（必须是表中存在的列）
  order    asc/desc，默认asc
  keyword  关键词（模糊搜索所有字符串列）

响应:
{
  "columns": [{ "COLUMN_NAME":"id","DATA_TYPE":"bigint","COLUMN_COMMENT":"主键",... }],
  "rows": [{ "id":1,"name":"设备A",... }],
  "total": 2469,
  "page": 1,
  "size": 50,
  "readOnly": false
}
```

### 3. 更新单行数据

```
PUT /admin/data-tables/{tableName}/{id}
Content-Type: application/json
权限: admin:user 或 laboratory:manage（readOnly表禁止编辑）

请求体: { "字段名": "新值", ... }
```

### 4. 批量更新

```
PUT /admin/data-tables/{tableName}/batch
Content-Type: application/json
权限: admin:user（仅系统管理员）

请求体: { "ids": [1,2,3], "updates": { "字段名": "新值" } }
```

### 5. 删除单行

```
DELETE /admin/data-tables/{tableName}/{id}
权限: admin:user（仅系统管理员，readOnly表禁止删除）
```

---

## 十、实验室管理 `/laboratories`（V2 新增）

### 1. 实验室列表（分页）

```
GET /laboratories?page=1&size=20&keyword=
权限: laboratory:view

参数:
  page      默认1
  size      默认20
  keyword   搜索关键词（匹配名称/编码）

响应:
{
  "code": 200,
  "data": {
    "records": [
      {
        "id": 1,
        "name": "建成环境气候模拟实验室",
        "code": "LAB-CLIMATE",
        "locationPrefix": "工程实验楼南楼＞5＞",
        "description": null,
        "status": 1,
        "createTime": "2026-07-11T00:00:00",
        "updateTime": "2026-07-11T00:00:00"
      }
    ],
    "total": 5,
    "current": 1,
    "size": 20
  }
}
```

### 2. 实验室下拉列表（所有启用）

```
GET /laboratories/list
权限: laboratory:view

响应: Laboratory数组（仅status=1的实验室）
```

### 3. 实验室详情

```
GET /laboratories/{id}
权限: laboratory:view
```

### 4. 新增实验室

```
POST /laboratories
Content-Type: application/json
权限: laboratory:manage

请求体:
{
  "name": "新实验室名称",
  "code": "LAB-CODE",
  "locationPrefix": "存放地前缀",
  "description": "描述",
  "status": 1
}
```

### 5. 更新实验室

```
PUT /laboratories/{id}
Content-Type: application/json
权限: laboratory:manage

请求体: 同上（部分字段更新）
```

### 6. 删除实验室（同时删除关联地点映射）

```
DELETE /laboratories/{id}
权限: laboratory:manage
```

### 7. 地点映射列表（分页）

```
GET /laboratories/rooms?page=1&size=20&laboratoryId=&roomName=
权限: laboratory:view

参数:
  laboratoryId   按实验室筛选（可选）
  roomName       按房间名搜索（可选）
```

### 8. 新增地点映射

```
POST /laboratories/rooms
Content-Type: application/json
权限: laboratory:manage

请求体:
{
  "laboratoryId": 1,
  "roomName": "工程南501",
  "fullLocation": "工程实验楼南楼＞5＞建成环境气候模拟实验室-501"
}
```

### 9. 更新地点映射

```
PUT /laboratories/rooms/{id}
Content-Type: application/json
权限: laboratory:manage
请求体: 同上
```

### 10. 删除地点映射

```
DELETE /laboratories/rooms/{id}
权限: laboratory:manage
```

### 11. 同步设备实验室（按当前映射规则批量更新）

```
POST /laboratories/sync-devices
权限: laboratory:manage

响应:
{
  "code": 200,
  "data": 5,       // 更新了多少台设备的laboratory_id
  "msg": "同步完成，5 台设备已更新"
}
```

### 12. 获取所有不重复存放地（供映射配置）

```
GET /laboratories/locations
权限: laboratory:view

响应: [ "工程实验楼南楼＞5＞绿色智慧建筑实验室1室-520", "工程南522", ... ]
```

---

## 十、系统管理 `/admin`（原章节九）

### 1. 用户管理

```
GET  /admin/users?page=1&size=20&keyword=    → 用户列表
POST /admin/users?username=&realName=&userType=&password=... → 创建本地账户
PUT  /admin/users/{id}/role?userType=2       → 变更角色
PUT  /admin/users/{id}/status                → 启用/禁用切换
权限: admin:user
```

### 2. 系统配置

```
GET    /admin/config                → 所有配置项
GET    /admin/config/{key}          → 单个配置值
PUT    /admin/config/{key}?value=14&description=  → 修改配置（运行时生效）
DELETE /admin/config/{key}          → 删除配置（恢复yml默认值）
权限: admin:config

预置配置项:
  borrow.max_days             默认7    借用最大天数
  borrow.default_approval_steps 默认2  审批级数
  cleanup.small_record_days   默认15   小记录保留天数
  cleanup.large_file_days     默认30   大文件保留天数
```

### 3. 操作日志

```
GET /admin/logs?page=1&size=20&username=&status=
权限: admin:log

响应: SysLog对象列表 (userId, username, operation, method, ip, duration, status, createTime)
```

---

## 十二、前端路由权限对照表

| 路由 | 所需权限 | 组件 |
|------|----------|------|
| `/login` | 无 | Login |
| `/dashboard` | `dashboard:view` | Dashboard |
| `/notifications` | `notification:view` | Notifications |
| `/profile` | `profile:view` | Profile |
| `/devices` | `device:view` | DeviceList |
| `/devices/:id` | `device:view` | DeviceDetail |
| `/devices/manage` | `device:manage` | DeviceManage |
| `/devices/create` | `device:manage` | DeviceCreate |
| `/devices/:id/edit` | `device:manage` | DeviceEdit |
| `/borrows/create` | `borrow:create` | BorrowCreate |
| `/borrows/my` | `borrow:my` | MyBorrows |
| `/borrows/:id` | `borrow:view` | BorrowDetail |
| `/borrows/:id/return` | `borrow:return` | BorrowReturn |
| `/borrows/pending/first` | `approval:first` | PendingFirst |
| `/borrows/pending/second` | `approval:second` | PendingSecond |
| `/returns` | `return:manage` | ReturnManage |
| `/repairs` | `repair:manage` | RepairManage |
| `/statistics` | `statistics:view` | Statistics |
| `/admin/users` | `admin:user` | AdminUsers |
| `/admin/settings` | `admin:config` | AdminSettings |
| `/admin/logs` | `admin:log` | AdminLogs |
| `/laboratories` | `laboratory:view` | LaboratoryList |
| `/laboratories/:id` | `laboratory:view` | LaboratoryDetail |
| `/laboratories/manage` | `laboratory:manage` | LaboratoryManage |
| `/laboratories/rooms` | `laboratory:manage` | LaboratoryRooms |
| `/403` | 无 | Forbidden |

---

## 十三、DTO/VO 类型定义（TypeScript 参考）

```typescript
// 登录
interface LoginVO {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  userInfo: UserInfoVO;
}

interface UserInfoVO {
  id: number;
  username: string;
  realName: string;
  userType: number;        // 0学生 1教师 2实验室管理员 3系统管理员
  userTypeName: string;
  department: string;
  className: string;
  authSource: string;      // C:CAS L:本地
  roles: string[];
  permissions: string[];
}

// 设备
interface Device {
  id: number;
  assetNo: string;
  name: string;
  model: string;
  specs: string;
  categoryId: number;
  location: string;
  department: string;
  totalQty: number;
  availableQty: number;
  unitPrice: number;
  totalAmount: number;
  status: number;          // 【废弃】旧状态，V3起使用 borrowStatus + deviceStatus
  borrowStatus: number;    // 借还状态: 1可借用 2借用中 3不可借 4逾期
  deviceStatus: number;    // 设备状态: 1正常 2待维修 3维修中 4待报废 5已报废
  borrowStatusName: string; // 借还状态中文名（非DB字段，后端填充）
  deviceStatusName: string; // 设备状态中文名（非DB字段，后端填充）
  borrowType: number;      // 1可现场借用 2可借出（默认）
  laboratoryId: number | null;
  gbCategoryName: string;
  gbCategoryCode: string;
  eduCategoryName: string;
  purchaseDate: string;    // yyyy-MM-dd
  manufacturer: string;
  supplier: string;
  coverImage: string;
}

interface DeviceDetailVO {
  device: Device;
  images: DeviceImage[];
  categoryName: string;
  isBorrowing: boolean;
  currentBorrower: string | null;
  expectedReturnTime: string | null;
  borrowType: number;          // 1可现场借用 2可借出
  laboratoryName: string | null;
  borrowCount: number;
}

interface DeviceImage {
  id: number;
  deviceId: number;
  imageUrl: string;
  sort: number;
}

// 借用
interface BorrowRecord {
  id: number;
  userId: number;
  deviceId: number;
  startTime: string;
  endTime: string;
  status: string;          // PENDING_APPROVAL/APPROVED/REJECTED/BORROWING/RETURNED/OVERDUE/CANCELLED
  reason: string;
  approveFlowDef: string;  // JSON快照
  currentStep: number;
  realReturnTime: string;
  overdueDays: number;
  damageReport: string;
}

// 导入结果
interface ImportResultDTO {
  totalRows: number;
  successCount: number;
  updateCount: number;
  deleteCount: number;       // V2新增: 删除的旧记录数
  failCount: number;
  autoCategoryCount: number;
  uncategorizedCount: number;
  batchId: string;
  errors: ImportError[];
}

// 通知
interface Notification {
  id: number;
  title: string;
  content: string;
  type: string;            // SYSTEM/APPROVAL/REMIND
  isRead: number;           // 0未读 1已读
  createTime: string;
}

// 实验室（V2新增）
interface Laboratory {
  id: number;
  name: string;
  code: string;
  locationPrefix: string;
  description: string;
  status: number;           // 1启用 0禁用
  createTime: string;
  updateTime: string;
}

interface LaboratoryRoom {
  id: number;
  laboratoryId: number;
  roomName: string;
  fullLocation: string;
  createTime: string;
}

// 系统配置
interface SystemConfig {
  id: number;
  configKey: string;
  configValue: string;
  description: string;
}
```
