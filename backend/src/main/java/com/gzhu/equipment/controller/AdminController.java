package com.gzhu.equipment.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gzhu.equipment.common.R;
import com.gzhu.equipment.entity.SysUser;
import com.gzhu.equipment.mapper.SysUserMapper;
import com.gzhu.equipment.service.SysUserService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 系统管理控制器
 *
 * GET    /admin/users          → 用户列表
 * POST   /admin/users          → 创建本地用户
 * PUT    /admin/users/{id}     → 编辑用户
 * PUT    /admin/users/{id}/role → 修改角色
 */
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Api(tags = "系统管理")
public class AdminController {

    private final SysUserMapper userMapper;
    private final SysUserService userService;

    @GetMapping("/users")
    @ApiOperation("用户列表")
    @PreAuthorize("hasAuthority('admin:user')")
    public R<IPage<SysUser>> listUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<SysUser> w = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            w.and(wp -> wp.like(SysUser::getUsername, keyword)
                    .or().like(SysUser::getRealName, keyword));
        }
        w.orderByAsc(SysUser::getUserType).orderByDesc(SysUser::getCreateTime);
        return R.ok(userMapper.selectPage(new Page<>(page, size), w));
    }

    @PostMapping("/users")
    @ApiOperation("创建本地账户")
    @PreAuthorize("hasAuthority('admin:user')")
    public R<SysUser> createUser(
            @RequestParam String username,
            @RequestParam String realName,
            @RequestParam Integer userType,
            @RequestParam String password,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String phone) {
        try {
            SysUser user = userService.createLocalUser(username, realName, userType, department, email, phone, password);
            return R.ok(user);
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        }
    }

    @PutMapping("/users/{id}/role")
    @ApiOperation("修改用户角色")
    @PreAuthorize("hasAuthority('admin:user')")
    public R<Void> updateRole(@PathVariable Long id, @RequestParam Integer userType) {
        SysUser user = userMapper.selectById(id);
        if (user == null) return R.fail(404, "用户不存在");
        user.setUserType(userType);
        userMapper.updateById(user);
        return R.ok();
    }

    @DeleteMapping("/users/{id}")
    @ApiOperation("删除用户")
    @PreAuthorize("hasAuthority('admin:user')")
    public R<String> deleteUser(@PathVariable Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) return R.fail(404, "用户不存在");
        if (user.getUsername().equals("admin")) return R.fail(400, "不能删除admin账户");
        userMapper.deleteById(id);
        return R.ok("已删除");
    }

    @PutMapping("/users/{id}/status")
    @ApiOperation("启用/禁用用户")
    @PreAuthorize("hasAuthority('admin:user')")
    public R<Void> toggleStatus(@PathVariable Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) return R.fail(404, "用户不存在");
        user.setStatus(user.getStatus() == 1 ? 0 : 1);
        userMapper.updateById(user);
        return R.ok();
    }
}
