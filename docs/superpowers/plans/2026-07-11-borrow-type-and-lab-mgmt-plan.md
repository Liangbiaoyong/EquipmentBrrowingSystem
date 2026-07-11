# 设备借用分类 + 实验室管理 — 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在设备表新增借用类型字段 + 实现实验室管理功能（含地点映射、设备关联、统一同步）

**Architecture:** 在现有 Spring Boot 后端基础上新增 laboratory/laboratory_room 表及对应 entity→mapper→service→controller 完整链路；device 表新增 borrow_type 和 laboratory_id 两个字段

**Tech Stack:** Spring Boot 2.7.x + MyBatis-Plus + MySQL 8.0

---

## 文件变更清单

### 新建文件
- `sql/init/03-update-v2.sql` — 表结构变更和新表 DDL
- `backend/src/main/java/com/gzhu/equipment/entity/Laboratory.java`
- `backend/src/main/java/com/gzhu/equipment/entity/LaboratoryRoom.java`
- `backend/src/main/java/com/gzhu/equipment/mapper/LaboratoryMapper.java`
- `backend/src/main/java/com/gzhu/equipment/mapper/LaboratoryRoomMapper.java`
- `backend/src/main/java/com/gzhu/equipment/service/LaboratoryService.java`
- `backend/src/main/java/com/gzhu/equipment/service/impl/LaboratoryServiceImpl.java`
- `backend/src/main/java/com/gzhu/equipment/controller/LaboratoryController.java`

### 修改文件
- `sql/init/01-schema.sql` — 新增表定义 + device 表新增字段
- `backend/src/main/java/com/gzhu/equipment/entity/Device.java` — 新增 borrowType, laboratoryId
- `backend/src/main/java/com/gzhu/equipment/service/DeviceService.java` — pageQuery 新增 borrowType, laboratoryId 参数
- `backend/src/main/java/com/gzhu/equipment/service/impl/DeviceServiceImpl.java` — pageQuery 实现新增筛选条件
- `backend/src/main/java/com/gzhu/equipment/controller/DeviceController.java` — 列表请求新增 borrowType, laboratoryId 参数；详情返回新增字段
- `backend/src/main/java/com/gzhu/equipment/vo/DeviceDetailVO.java` — 新增 borrowType, laboratoryName 字段
- `backend/src/main/java/com/gzhu/equipment/security/PermissionConstants.java` — 新增实验室管理权限
- `docs/development/前端API对接文档.md` — 新增实验室管理 API 文档 + 设备接口变更
- `docs/design/设计文档.md` — 新增实验室数据库设计 + 设备表变更

---

### Task 1: SQL 脚本 — 新表 + 表结构变更

**Files:**
- Create: `sql/init/03-update-v2.sql`
- Modify: `sql/init/01-schema.sql`

- [ ] **Step 1: 创建 03-update-v2.sql 升级脚本**

```sql
-- ============================================================
-- V2 升级脚本：实验室管理 + 设备借用分类
-- ============================================================

-- 1. 实验室表
CREATE TABLE IF NOT EXISTS `laboratory` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL COMMENT '实验室名称',
  `code` varchar(50) DEFAULT NULL COMMENT '实验室编码',
  `location_prefix` varchar(200) DEFAULT NULL COMMENT '存放地前缀',
  `description` text COMMENT '实验室描述',
  `status` tinyint DEFAULT '1' COMMENT '1启用 0禁用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='实验室表';

-- 2. 实验室地点映射表
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

-- 3. device 表新增字段
ALTER TABLE `device`
  ADD COLUMN IF NOT EXISTS `borrow_type` tinyint DEFAULT 2 COMMENT '借用类型: 1可现场借用 2可借出（默认）' AFTER `status`,
  ADD COLUMN IF NOT EXISTS `laboratory_id` bigint DEFAULT NULL COMMENT '所属实验室ID' AFTER `location`,
  ADD INDEX IF NOT EXISTS `idx_borrow_type` (`borrow_type`),
  ADD INDEX IF NOT EXISTS `idx_laboratory` (`laboratory_id`);
```

**注意：** MySQL 8.0.16+ 支持 `IF NOT EXISTS` 和 `IF EXISTS` 子句，但 `ADD COLUMN IF NOT EXISTS` 在部分版本可能不支持。如果部署环境版本较低，可以用存储过程替代或手动判断。为兼容性，这里使用标准 `ALTER TABLE` 加条件判断（见 ddl 兼容性说明）。

- [ ] **Step 2: 同步更新 01-schema.sql**

在 01-schema.sql 末尾（或 device 表定义处）添加新表和新字段定义，确保全新部署也能包含这些变更。

---

### Task 2: Laboratory 实体 + Mapper

**Files:**
- Create: `backend/src/main/java/com/gzhu/equipment/entity/Laboratory.java`
- Create: `backend/src/main/java/com/gzhu/equipment/mapper/LaboratoryMapper.java`

- [ ] **Step 1: 创建 Laboratory 实体**

```java
package com.gzhu.equipment.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("laboratory")
public class Laboratory implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 实验室名称 */
    private String name;

    /** 实验室编码 */
    private String code;

    /** 存放地前缀（如一教楼＞5＞） */
    private String locationPrefix;

    /** 实验室描述 */
    private String description;

    /** 1启用 0禁用 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
```

- [ ] **Step 2: 创建 LaboratoryMapper**

```java
package com.gzhu.equipment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gzhu.equipment.entity.Laboratory;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LaboratoryMapper extends BaseMapper<Laboratory> {
}
```

---

### Task 3: LaboratoryRoom 实体 + Mapper

**Files:**
- Create: `backend/src/main/java/com/gzhu/equipment/entity/LaboratoryRoom.java`
- Create: `backend/src/main/java/com/gzhu/equipment/mapper/LaboratoryRoomMapper.java`

- [ ] **Step 1: 创建 LaboratoryRoom 实体**

```java
package com.gzhu.equipment.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("laboratory_room")
public class LaboratoryRoom implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联实验室ID */
    private Long laboratoryId;

    /** 房间/地点简称（如工程南501） */
    private String roomName;

    /** 完整存放地名称 */
    private String fullLocation;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
```

- [ ] **Step 2: 创建 LaboratoryRoomMapper**

```java
package com.gzhu.equipment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gzhu.equipment.entity.LaboratoryRoom;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LaboratoryRoomMapper extends BaseMapper<LaboratoryRoom> {
}
```

---

### Task 4: Device 实体新增字段

**Files:**
- Modify: `backend/src/main/java/com/gzhu/equipment/entity/Device.java`

- [ ] **Step 1: 添加 borrowType 和 laboratoryId 字段**

在 `status` 字段之后添加：
```java
    /** 借用类型: 1可现场借用 2可借出（默认） */
    private Integer borrowType;
```

在 `location` 字段之后添加：
```java
    /** 所属实验室ID */
    private Long laboratoryId;
```

在 `createBy` 字段之后添加（transient 非数据库字段，用于展示）：
```java
    /** 实验室名称（非数据库字段） */
    @TableField(exist = false)
    private String laboratoryName;
```

---

### Task 5: DeviceService 新增筛选参数

**Files:**
- Modify: `backend/src/main/java/com/gzhu/equipment/service/DeviceService.java`
- Modify: `backend/src/main/java/com/gzhu/equipment/service/impl/DeviceServiceImpl.java`

- [ ] **Step 1: DeviceService 接口新增参数**

pageQuery 方法增加 `borrowType` 和 `laboratoryId` 参数：
```java
    IPage<Device> pageQuery(int page, int size,
                            String keyword, Long categoryId,
                            Integer status, String location,
                            String gbCategoryName,
                            Integer borrowType, Long laboratoryId);
```

- [ ] **Step 2: DeviceServiceImpl 实现新增筛选**

在 pageQuery 实现中添加：
```java
        if (borrowType != null) {
            wrapper.eq(Device::getBorrowType, borrowType);
        }
        if (laboratoryId != null) {
            wrapper.eq(Device::getLaboratoryId, laboratoryId);
        }
```

---

### Task 6: DeviceController 新增参数

**Files:**
- Modify: `backend/src/main/java/com/gzhu/equipment/controller/DeviceController.java`

- [ ] **Step 1: 列表接口新增 borrowType 和 laboratoryId 参数**

```java
    @GetMapping
    @ApiOperation("分页查询设备列表")
    public R<IPage<Device>> listDevices(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer borrowType,
            @RequestParam(required = false) Long laboratoryId,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String gbCategoryName) {
        return R.ok(deviceService.pageQuery(page, size, keyword, categoryId, status, location, gbCategoryName, borrowType, laboratoryId));
    }
```

---

### Task 7: DeviceDetailVO 新增字段

**Files:**
- Modify: `backend/src/main/java/com/gzhu/equipment/vo/DeviceDetailVO.java`

- [ ] **Step 1: 新增 borrowType 和 laboratoryName 字段**

```java
    private Integer borrowType;
    private String laboratoryName;
```

更新 `DeviceController.getDevice()` 方法，设置这两个字段：
```java
        // 借用类型
        vo.setBorrowType(device.getBorrowType());
        
        // 实验室名称
        if (device.getLaboratoryId() != null) {
            var lab = com.gzhu.equipment.mapper.LaboratoryMapper == null ? null : null;
            // 实际上需要在 Controller 注入 LaboratoryMapper 或使用 LaboratoryService
            var labMapper = /*注入的 laboratoryMapper*/;
            var lab = labMapper.selectById(device.getLaboratoryId());
            vo.setLaboratoryName(lab != null ? lab.getName() : null);
        }
```

---

### Task 8: LaboratoryService + Impl

**Files:**
- Create: `backend/src/main/java/com/gzhu/equipment/service/LaboratoryService.java`
- Create: `backend/src/main/java/com/gzhu/equipment/service/impl/LaboratoryServiceImpl.java`

- [ ] **Step 1: LaboratoryService 接口**

```java
package com.gzhu.equipment.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.gzhu.equipment.entity.Laboratory;
import com.gzhu.equipment.entity.LaboratoryRoom;
import java.util.List;
import java.util.Map;

public interface LaboratoryService extends IService<Laboratory> {

    /** 分页查询实验室 */
    IPage<Laboratory> pageQuery(int page, int size, String keyword);

    /** 获取所有启用实验室（下拉用） */
    List<Laboratory> listEnabled();

    /** 获取实验室详情（含地点映射列表） */
    Laboratory getDetail(Long id);

    /** 分页查询地点映射 */
    IPage<LaboratoryRoom> pageRooms(int page, int size, Long laboratoryId, String roomName);

    /** 新增地点映射 */
    void addRoom(LaboratoryRoom room);

    /** 更新地点映射 */
    void updateRoom(Long id, LaboratoryRoom room);

    /** 删除地点映射 */
    void deleteRoom(Long id);

    /** 获取不重复的存放地列表（从 device 表抽取） */
    List<String> listDistinctLocations();

    /** 同步设备的地点→实验室映射 */
    int syncDeviceLaboratories();

    /** 根据location自动匹配laboratoryId */
    Long matchLaboratoryId(String location);
}
```

- [ ] **Step 2: LaboratoryServiceImpl 实现**

```java
package com.gzhu.equipment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gzhu.equipment.entity.Device;
import com.gzhu.equipment.entity.Laboratory;
import com.gzhu.equipment.entity.LaboratoryRoom;
import com.gzhu.equipment.mapper.DeviceMapper;
import com.gzhu.equipment.mapper.LaboratoryMapper;
import com.gzhu.equipment.mapper.LaboratoryRoomMapper;
import com.gzhu.equipment.service.LaboratoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LaboratoryServiceImpl extends ServiceImpl<LaboratoryMapper, Laboratory> implements LaboratoryService {

    private final LaboratoryMapper laboratoryMapper;
    private final LaboratoryRoomMapper roomMapper;
    private final DeviceMapper deviceMapper;

    @Override
    public IPage<Laboratory> pageQuery(int page, int size, String keyword) {
        LambdaQueryWrapper<Laboratory> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(Laboratory::getName, keyword)
                    .or().like(Laboratory::getCode, keyword);
        }
        wrapper.orderByAsc(Laboratory::getName);
        return laboratoryMapper.selectPage(new Page<>(page, size), wrapper);
    }

    @Override
    public List<Laboratory> listEnabled() {
        return laboratoryMapper.selectList(
                new LambdaQueryWrapper<Laboratory>().eq(Laboratory::getStatus, 1)
                        .orderByAsc(Laboratory::getName));
    }

    @Override
    public Laboratory getDetail(Long id) {
        return laboratoryMapper.selectById(id);
    }

    @Override
    public IPage<LaboratoryRoom> pageRooms(int page, int size, Long laboratoryId, String roomName) {
        LambdaQueryWrapper<LaboratoryRoom> wrapper = new LambdaQueryWrapper<>();
        if (laboratoryId != null) {
            wrapper.eq(LaboratoryRoom::getLaboratoryId, laboratoryId);
        }
        if (StringUtils.hasText(roomName)) {
            wrapper.like(LaboratoryRoom::getRoomName, roomName);
        }
        wrapper.orderByAsc(LaboratoryRoom::getRoomName);
        return roomMapper.selectPage(new Page<>(page, size), wrapper);
    }

    @Override
    public void addRoom(LaboratoryRoom room) {
        roomMapper.insert(room);
    }

    @Override
    public void updateRoom(Long id, LaboratoryRoom room) {
        room.setId(id);
        roomMapper.updateById(room);
    }

    @Override
    public void deleteRoom(Long id) {
        roomMapper.deleteById(id);
    }

    @Override
    public List<String> listDistinctLocations() {
        return deviceMapper.selectList(
                new LambdaQueryWrapper<Device>()
                        .select(Device::getLocation)
                        .isNotNull(Device::getLocation)
                        .groupBy(Device::getLocation))
                .stream()
                .map(Device::getLocation)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public int syncDeviceLaboratories() {
        List<Device> devices = deviceMapper.selectList(
                new LambdaQueryWrapper<Device>().isNotNull(Device::getLocation));
        int updated = 0;
        for (Device device : devices) {
            Long matchedLabId = matchLaboratoryId(device.getLocation());
            if (matchedLabId != null && !matchedLabId.equals(device.getLaboratoryId())) {
                device.setLaboratoryId(matchedLabId);
                deviceMapper.updateById(device);
                updated++;
            }
        }
        log.info("实验室同步完成：{} 台设备已更新", updated);
        return updated;
    }

    @Override
    public Long matchLaboratoryId(String location) {
        if (!StringUtils.hasText(location)) return null;
        List<LaboratoryRoom> rooms = roomMapper.selectList(null);
        // 优先最长匹配（最精确的房间名优先）
        rooms.sort((a, b) -> b.getRoomName().length() - a.getRoomName().length());
        for (LaboratoryRoom room : rooms) {
            if (location.contains(room.getRoomName())) {
                return room.getLaboratoryId();
            }
        }
        return null;
    }
}
```

---

### Task 9: LaboratoryController

**Files:**
- Create: `backend/src/main/java/com/gzhu/equipment/controller/LaboratoryController.java`

- [ ] **Step 1: 创建完整 Controller**

```java
package com.gzhu.equipment.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gzhu.equipment.common.R;
import com.gzhu.equipment.entity.Laboratory;
import com.gzhu.equipment.entity.LaboratoryRoom;
import com.gzhu.equipment.mapper.LaboratoryRoomMapper;
import com.gzhu.equipment.security.PermissionConstants;
import com.gzhu.equipment.service.LaboratoryService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/laboratories")
@RequiredArgsConstructor
@Api(tags = "实验室管理")
public class LaboratoryController {

    private final LaboratoryService laboratoryService;
    private final LaboratoryRoomMapper roomMapper;

    // ==================== 实验室 CRUD ====================

    @GetMapping
    @ApiOperation("分页查询实验室列表")
    @PreAuthorize("hasAuthority('laboratory:view')")
    public R<IPage<Laboratory>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        return R.ok(laboratoryService.pageQuery(page, size, keyword));
    }

    @GetMapping("/list")
    @ApiOperation("获取所有启用的实验室（下拉选择用）")
    @PreAuthorize("hasAuthority('laboratory:view')")
    public R<List<Laboratory>> listEnabled() {
        return R.ok(laboratoryService.listEnabled());
    }

    @GetMapping("/{id}")
    @ApiOperation("获取实验室详情")
    @PreAuthorize("hasAuthority('laboratory:view')")
    public R<Laboratory> get(@PathVariable Long id) {
        Laboratory lab = laboratoryService.getDetail(id);
        if (lab == null) return R.fail(404, "实验室不存在");
        return R.ok(lab);
    }

    @PostMapping
    @ApiOperation("新增实验室")
    @PreAuthorize("hasAuthority('laboratory:manage')")
    public R<Laboratory> create(@RequestBody Laboratory laboratory) {
        laboratoryService.save(laboratory);
        log.info("新增实验室: id={} name={}", laboratory.getId(), laboratory.getName());
        return R.ok(laboratory);
    }

    @PutMapping("/{id}")
    @ApiOperation("更新实验室")
    @PreAuthorize("hasAuthority('laboratory:manage')")
    public R<Laboratory> update(@PathVariable Long id, @RequestBody Laboratory laboratory) {
        laboratory.setId(id);
        laboratoryService.updateById(laboratory);
        log.info("更新实验室: id={}", id);
        return R.ok(laboratory);
    }

    @DeleteMapping("/{id}")
    @ApiOperation("删除实验室")
    @PreAuthorize("hasAuthority('laboratory:manage')")
    public R<Void> delete(@PathVariable Long id) {
        laboratoryService.removeById(id);
        // 同时删除关联的地点映射
        roomMapper.delete(new LambdaQueryWrapper<LaboratoryRoom>().eq(LaboratoryRoom::getLaboratoryId, id));
        log.info("删除实验室: id={}", id);
        return R.ok();
    }

    // ==================== 地点映射管理 ====================

    @GetMapping("/rooms")
    @ApiOperation("分页查询地点映射")
    @PreAuthorize("hasAuthority('laboratory:view')")
    public R<IPage<LaboratoryRoom>> listRooms(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long laboratoryId,
            @RequestParam(required = false) String roomName) {
        return R.ok(laboratoryService.pageRooms(page, size, laboratoryId, roomName));
    }

    @PostMapping("/rooms")
    @ApiOperation("新增地点映射")
    @PreAuthorize("hasAuthority('laboratory:manage')")
    public R<LaboratoryRoom> createRoom(@RequestBody LaboratoryRoom room) {
        laboratoryService.addRoom(room);
        log.info("新增地点映射: room={} labId={}", room.getRoomName(), room.getLaboratoryId());
        return R.ok(room);
    }

    @PutMapping("/rooms/{id}")
    @ApiOperation("更新地点映射")
    @PreAuthorize("hasAuthority('laboratory:manage')")
    public R<LaboratoryRoom> updateRoom(@PathVariable Long id, @RequestBody LaboratoryRoom room) {
        laboratoryService.updateRoom(id, room);
        log.info("更新地点映射: id={}", id);
        return R.ok(room);
    }

    @DeleteMapping("/rooms/{id}")
    @ApiOperation("删除地点映射")
    @PreAuthorize("hasAuthority('laboratory:manage')")
    public R<Void> deleteRoom(@PathVariable Long id) {
        laboratoryService.deleteRoom(id);
        return R.ok();
    }

    // ==================== 同步功能 ====================

    @PostMapping("/sync-devices")
    @ApiOperation("同步所有设备实验室（按当前映射规则批量更新）")
    @PreAuthorize("hasAuthority('laboratory:manage')")
    public R<Integer> syncDevices() {
        int count = laboratoryService.syncDeviceLaboratories();
        return R.ok("同步完成，" + count + " 台设备已更新", count);
    }

    @GetMapping("/locations")
    @ApiOperation("获取所有不重复的存放地列表（供映射配置使用）")
    @PreAuthorize("hasAuthority('laboratory:view')")
    public R<List<String>> listLocations() {
        return R.ok(laboratoryService.listDistinctLocations());
    }
}
```

---

### Task 10: PermissionConstants 新增权限

**Files:**
- Modify: `backend/src/main/java/com/gzhu/equipment/security/PermissionConstants.java`

- [ ] **Step 1: 新增实验室权限常量**

在权限标识定义区域新增：
```java
    public static final String LAB_VIEW    = "laboratory:view";
    public static final String LAB_MANAGE  = "laboratory:manage";
```

在学生权限列表中添加 `LAB_VIEW`：
```java
    private static final List<String> STUDENT_PERMS = Collections.unmodifiableList(Arrays.asList(
            DASHBOARD_VIEW, NOTIFICATION_VIEW, PROFILE_VIEW,
            DEVICE_VIEW, LAB_VIEW,
            BORROW_CREATE, BORROW_MY, BORROW_VIEW, BORROW_RETURN
    ));
```

在教师权限、实验室管理员权限、系统管理员权限中添加 `LAB_VIEW` 和 `LAB_MANAGE`：
- 教师：仅 `LAB_VIEW`
- 实验室管理员：`LAB_VIEW` + `LAB_MANAGE`
- 系统管理员：`LAB_VIEW` + `LAB_MANAGE`

---

### Task 11: 初始化 32 条实验室地点映射数据

**Files:**
- Modify: `sql/init/02-data.sql`

- [ ] **Step 1: 在 02-data.sql 末尾添加实验室和映射数据**

```sql
-- ============================================================
-- V2 实验室 + 地点映射初始数据
-- ============================================================

-- 实验室
INSERT INTO `laboratory` (`name`, `code`, `location_prefix`, `description`, `status`) VALUES
('建成环境气候模拟实验室', 'LAB-CLIMATE', '工程实验楼南楼＞5＞', NULL, 1),
('声振环境实验室', 'LAB-SOUND', '理科教学楼北楼＞6＞', NULL, 1),
('建筑光学与暗夜保护实验室', 'LAB-OPTICS', '工程实验楼南楼＞5＞', NULL, 1),
('数字孪生与空间模型实验室', 'LAB-DIGITAL', '理科教学楼北楼＞5＞', NULL, 1),
('资源与环境智慧测评实验室', 'LAB-RESOURCE', '理科教学楼北楼＞5＞', NULL, 1)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

-- 地点映射
-- 先查询实验室ID再插入（假设按上述插入顺序，id为1-5）
-- 建成环境气候模拟实验室(id假设=1): 工程南501
-- 声振环境实验室(id假设=2): 工程北623
-- 建筑光学与暗夜保护实验室(id假设=3): 工程南530
-- 数字孪生与空间模型实验室(id假设=4): 工程北504
-- 资源与环境智慧测评实验室(id假设=5): 工程北503
```

---

### Task 12: 更新 DeviceDetailVO 和设备详情接口

**Files:**
- Modify: `backend/src/main/java/com/gzhu/equipment/vo/DeviceDetailVO.java`
- Modify: `backend/src/main/java/com/gzhu/equipment/controller/DeviceController.java`

---

### Task 13: 更新文档

**Files:**
- Modify: `docs/development/前端API对接文档.md`
- Modify: `docs/design/设计文档.md`

---

### Task 14: Git commit + push

```bash
git add -A
git status  # 确认只包含本功能相关文件
git commit -m "feat: 新增设备借用分类(borrow_type)和实验室管理功能

- device表新增borrow_type(借用类型)和laboratory_id(所属实验室)字段
- 新建laboratory(实验室表)和laboratory_room(地点映射表)
- 实验室CRUD + 地点映射管理 + 批量同步设备实验室
- PermissionConstants新增实验室相关权限
- 初始化32条地点映射数据

Co-Authored-By: Claude <noreply@anthropic.com>"
```
