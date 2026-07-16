package com.gzhu.equipment.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gzhu.equipment.common.R;
import com.gzhu.equipment.entity.Laboratory;
import com.gzhu.equipment.entity.LaboratoryRoom;
import com.gzhu.equipment.mapper.LaboratoryRoomMapper;
import com.gzhu.equipment.service.LaboratoryService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 实验室管理控制器
 */
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
    @ApiOperation("分页查询实验室列表（支持排序）")
    @PreAuthorize("hasAuthority('laboratory:view')")
    public R<IPage<Laboratory>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false, defaultValue = "asc") String order) {
        return R.ok(laboratoryService.pageQuery(page, size, keyword, sortBy, order));
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
    @ApiOperation("删除实验室（同时删除关联的地点映射）")
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
    @ApiOperation("分页查询地点映射（支持排序）")
    @PreAuthorize("hasAuthority('laboratory:view')")
    public R<IPage<LaboratoryRoom>> listRooms(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long laboratoryId,
            @RequestParam(required = false) String roomName,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false, defaultValue = "asc") String order) {
        return R.ok(laboratoryService.pageRooms(page, size, laboratoryId, roomName, sortBy, order));
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
