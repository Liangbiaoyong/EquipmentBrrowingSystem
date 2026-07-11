# 前端 API 对接文档

| 版本 | 日期 | 说明 |
|------|------|------|
| V1.0 | 2026-07-07 | 初始版本，覆盖所有后端已实现接口 |

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
  status         1可借用 2借用中 3维修中 4待报废
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
    "borrowCount": 12               // 历史借用次数
  }
}
```

### 3. 按资产编号查询

```
GET /devices/by-asset-no/{assetNo}
```

### 4. 更新设备

```
PUT /devices/{id}
Content-Type: application/json
权限: device:manage

请求体: Device对象（JSON）
```

### 5. 删除设备

```
DELETE /devices/{id}
权限: admin:user (SYSTEM_ADMIN)
```

### 6. 批量导入（CSV/XLSX）

```
POST /devices/import
Content-Type: multipart/form-data
权限: device:manage

参数: file (文件)

响应:
{
  "code": 200,
  "data": {
    "totalRows": 2469,
    "successCount": 2100,
    "updateCount": 369,
    "failCount": 0,
    "autoCategoryCount": 2300,
    "uncategorizedCount": 169,
    "batchId": "a1b2c3d4",
    "errors": []
  }
}
```

### 7. 导入预览（Dry-Run）

```
POST /devices/import/dry-run
Content-Type: multipart/form-data
权限: device:manage

参数: file (文件)
返回前20条解析结果+分类统计，不写入数据库
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

## 九、系统管理 `/admin`

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

## 十、前端路由权限对照表

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
| `/403` | 无 | Forbidden |

---

## 十一、DTO/VO 类型定义（TypeScript 参考）

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
  status: number;          // 1可借用 2借用中 3维修中 4待报废
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

// 系统配置
interface SystemConfig {
  id: number;
  configKey: string;
  configValue: string;
  description: string;
}
```
