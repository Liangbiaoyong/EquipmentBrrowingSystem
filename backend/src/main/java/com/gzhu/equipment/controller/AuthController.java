package com.gzhu.equipment.controller;

import com.gzhu.equipment.common.R;
import com.gzhu.equipment.dto.CasLoginRequest;
import com.gzhu.equipment.dto.LocalLoginRequest;
import com.gzhu.equipment.service.AuthService;
import com.gzhu.equipment.vo.LoginVO;
import com.gzhu.equipment.vo.UserInfoVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * 认证控制器 — CAS统一认证、本地登录、用户信息
 *
 * 基准路径: /api/v1/auth
 */
@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Api(tags = "用户认证")
public class AuthController {

    private final AuthService authService;

    /**
     * CAS 统一认证登录
     *
     * 前端完成CAS跳转登录后，获取token，调用此接口完成系统认证。
     * 后端用token调CAS userInfo API验证，创建或更新用户，签发JWT。
     *
     * POST /api/v1/auth/cas/login
     */
    @PostMapping("/cas/login")
    @ApiOperation("CAS统一认证登录")
    public R<LoginVO> casLogin(@Valid @RequestBody CasLoginRequest request) {
        try {
            LoginVO loginVO = authService.casLogin(request);
            log.info("CAS登录成功: username={}", loginVO.getUserInfo().getUsername());
            return R.ok("CAS登录成功", loginVO);
        } catch (BadCredentialsException e) {
            log.warn("CAS登录失败: {}", e.getMessage());
            return R.fail(401, e.getMessage());
        }
    }

    /**
     * 本地账户登录
     *
     * 仅 auth_source=L 的本地账户（如系统管理员、实验室管理员）可使用。
     *
     * POST /api/v1/auth/local/login
     */
    @PostMapping("/local/login")
    @ApiOperation("本地账户登录")
    public R<LoginVO> localLogin(@Valid @RequestBody LocalLoginRequest request) {
        try {
            LoginVO loginVO = authService.localLogin(request);
            log.info("本地登录成功: username={}", loginVO.getUserInfo().getUsername());
            return R.ok("登录成功", loginVO);
        } catch (BadCredentialsException e) {
            log.warn("本地登录失败: {}", e.getMessage());
            return R.fail(401, e.getMessage());
        }
    }

    /**
     * 获取当前登录用户信息及权限
     *
     * 用于前端路由守卫获取用户角色权限，以及刷新页面时恢复用户状态。
     *
     * GET /api/v1/auth/info
     */
    @GetMapping("/info")
    @ApiOperation("获取当前用户信息及权限")
    public R<UserInfoVO> getCurrentUserInfo() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return R.fail(401, "未登录");
        }
        Long userId = (Long) authentication.getPrincipal();
        UserInfoVO userInfo = authService.getCurrentUserInfo(userId);
        return R.ok(userInfo);
    }

    /**
     * 健康检查（无需登录）
     */
    @GetMapping("/health")
    @ApiOperation("认证服务健康检查")
    public R<String> health() {
        return R.ok("认证服务正常");
    }
}
