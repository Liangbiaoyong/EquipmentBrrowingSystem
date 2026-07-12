package com.gzhu.equipment.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gzhu.equipment.common.R;
import com.gzhu.equipment.entity.Device;
import com.gzhu.equipment.entity.RepairRecord;
import com.gzhu.equipment.security.JwtUserPrincipal;
import com.gzhu.equipment.service.RepairService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/** V3: 维修管理 — 完整生命周期+新设备状态 */
@RestController @RequestMapping("/repairs") @RequiredArgsConstructor @Api(tags = "维修管理")
public class RepairController {
    private final RepairService repairService;

    @GetMapping @ApiOperation("维修记录列表") @PreAuthorize("hasAuthority('repair:manage')")
    public R<IPage<RepairRecord>> list(@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int size,@RequestParam(required=false)String status){
        return R.ok(repairService.list(page,size,status));
    }

    @GetMapping("/devices") @ApiOperation("维修相关设备列表(按设备状态)") @PreAuthorize("hasAuthority('repair:manage')")
    public R<IPage<Device>> listDevices(@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int size,@RequestParam(required=false)Integer deviceStatus){
        return R.ok(repairService.listRepairDevices(page,size,deviceStatus));
    }

    @PostMapping @ApiOperation("创建维修记录(设备→待维修)") @PreAuthorize("hasAuthority('repair:manage')")
    public R<RepairRecord> create(@RequestParam Long deviceId,@RequestParam(required=false) Long borrowId,@RequestParam String faultDescription){
        return R.ok(repairService.createFromDamage(deviceId,borrowId,faultDescription));
    }

    @PutMapping("/{id}/start") @ApiOperation("开始维修(设备→维修中)") @PreAuthorize("hasAuthority('repair:manage')")
    public R<Void> startRepair(@PathVariable Long id){
        repairService.startRepair(id, getUserId()); return R.ok();
    }

    @PutMapping("/{id}/fix") @ApiOperation("修复完成(设备→正常)") @PreAuthorize("hasAuthority('repair:manage')")
    public R<String> markFixed(@PathVariable Long id,@RequestParam(required=false,defaultValue="")String comment){
        repairService.markFixed(id,comment); return R.ok("设备已恢复正常");
    }

    @PutMapping("/{id}/unrepairable") @ApiOperation("无法修复(设备→无法维修)") @PreAuthorize("hasAuthority('repair:manage')")
    public R<String> markUnrepairable(@PathVariable Long id,@RequestParam(required=false,defaultValue="")String comment){
        repairService.markUnrepairable(id,comment); return R.ok("已标记无法维修");
    }

    @PutMapping("/{id}/scrap") @ApiOperation("标记待报废(设备→待报废)") @PreAuthorize("hasAuthority('repair:manage')")
    public R<String> markScrap(@PathVariable Long id,@RequestParam(required=false,defaultValue="")String comment){
        repairService.markScrap(id,comment); return R.ok("已标记待报废");
    }

    @PostMapping("/confirm-scrap") @ApiOperation("确认报废(设备→已报废)") @PreAuthorize("hasAuthority('repair:manage')")
    public R<String> confirmScrap(@RequestParam Long deviceId,@RequestParam(required=false,defaultValue="")String comment){
        repairService.confirmScrap(deviceId,comment); return R.ok("已报废");
    }

    private Long getUserId(){
        Authentication auth=SecurityContextHolder.getContext().getAuthentication();
        if(auth!=null&&auth.getPrincipal() instanceof JwtUserPrincipal p) return p.getUserId();
        return 1L;
    }
}
