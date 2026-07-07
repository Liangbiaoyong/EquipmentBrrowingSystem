package com.gzhu.equipment.controller;

import com.gzhu.equipment.common.R;
import com.gzhu.equipment.service.SystemConfigService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 系统配置管理 — Admin 运行时修改系统参数
 *
 * GET    /admin/config          → 所有配置
 * GET    /admin/config/{key}    → 单个配置值
 * PUT    /admin/config/{key}    → 修改配置
 * DELETE /admin/config/{key}    → 删除配置（恢复默认值）
 */
@RestController
@RequestMapping("/admin/config")
@RequiredArgsConstructor
@Api(tags = "系统配置管理")
public class SystemConfigController {

    private final SystemConfigService configService;

    @GetMapping
    @ApiOperation("获取所有系统配置")
    @PreAuthorize("hasAuthority('admin:config')")
    public R<java.util.List<com.gzhu.equipment.entity.SystemConfig>> listAll() {
        return R.ok(configService.listAll());
    }

    @GetMapping("/{key}")
    @ApiOperation("获取单个配置值")
    @PreAuthorize("hasAuthority('admin:config')")
    public R<Map<String, String>> getValue(@PathVariable String key) {
        String value = configService.getValue(key, null);
        if (value == null) return R.fail(404, "配置项不存在: " + key);
        return R.ok(Map.of("key", key, "value", value));
    }

    @PutMapping("/{key}")
    @ApiOperation("修改/新增系统配置")
    @PreAuthorize("hasAuthority('admin:config')")
    public R<String> setValue(@PathVariable String key,
                               @RequestParam String value,
                               @RequestParam(required = false) String description) {
        configService.setValue(key, value, description);
        return R.ok("配置已更新");
    }

    @DeleteMapping("/{key}")
    @ApiOperation("删除配置（恢复 application.yml 默认值）")
    @PreAuthorize("hasAuthority('admin:config')")
    public R<String> deleteValue(@PathVariable String key) {
        configService.deleteByKey(key);
        return R.ok("配置已删除，将使用默认值");
    }
}
