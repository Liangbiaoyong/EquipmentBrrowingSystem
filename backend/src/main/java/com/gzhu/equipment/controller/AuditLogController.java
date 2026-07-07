package com.gzhu.equipment.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gzhu.equipment.common.R;
import com.gzhu.equipment.entity.SysLog;
import com.gzhu.equipment.mapper.SysLogMapper;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/logs")
@RequiredArgsConstructor
@Api(tags = "操作日志审计")
public class AuditLogController {

    private final SysLogMapper sysLogMapper;

    @GetMapping
    @ApiOperation("操作日志列表")
    @PreAuthorize("hasAuthority('admin:log')")
    public R<IPage<SysLog>> list(@RequestParam(defaultValue = "1") int page,
                                  @RequestParam(defaultValue = "20") int size,
                                  @RequestParam(required = false) String username,
                                  @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<SysLog> w = new LambdaQueryWrapper<>();
        if (username != null && !username.isEmpty()) w.like(SysLog::getUsername, username);
        if (status != null) w.eq(SysLog::getStatus, status);
        w.orderByDesc(SysLog::getCreateTime);
        return R.ok(sysLogMapper.selectPage(new Page<>(page, size), w));
    }
}
