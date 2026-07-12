package com.gzhu.equipment.controller;

import com.gzhu.equipment.common.R;
import com.gzhu.equipment.dto.CasLoginRequest;
import com.gzhu.equipment.dto.LocalLoginRequest;
import com.gzhu.equipment.security.JwtUserPrincipal;
import com.gzhu.equipment.security.LoginRateLimiter;
import com.gzhu.equipment.security.TokenBlacklist;
import com.gzhu.equipment.service.AuthService;
import com.gzhu.equipment.vo.LoginVO;
import com.gzhu.equipment.vo.UserInfoVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
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
    private final LoginRateLimiter rateLimiter;
    private final TokenBlacklist tokenBlacklist;
    private final com.gzhu.equipment.mapper.SysUserMapper sysUserMapper;

    @org.springframework.beans.factory.annotation.Value("${cas.dev-mode:false}")
    private boolean casDevMode;

    @org.springframework.beans.factory.annotation.Value("${cas.userinfo-url:https://libbooking.gzhu.edu.cn/ic-web/auth/userInfo}")
    private String casUserInfoUrl;

    @javax.annotation.PostConstruct
    void init() {
        log.info("CAS配置: devMode={} userinfoUrl={}", casDevMode, casUserInfoUrl);
    }

    /**
     * CAS 统一认证登录
     *
     * 前端完成CAS跳转登录后，获取token，调用此接口完成系统认证。
     * 后端用token调CAS userInfo API验证，创建或更新用户，签发JWT。
     *
     * POST /api/v1/auth/cas/login
     */
    @PostMapping("/cas/credential-login")
    @ApiOperation("CAS服务端无感登录（用户名+密码，无需跳转）")
    public R<LoginVO> casCredentialLogin(@RequestBody java.util.Map<String,String> body) {
        String username = body.get("username");
        String password = body.get("password");
        if (username == null || password == null) return R.fail(400, "用户名和密码不能为空");
        try {
            LoginVO vo = authService.casCredentialLogin(username, password);
            return R.ok("CAS登录成功", vo);
        } catch (Exception e) {
            log.warn("CAS凭证登录失败: {}", e.getMessage());
            return R.fail(401, "CAS登录失败: " + e.getMessage());
        }
    }

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
    public R<LoginVO> localLogin(@Valid @RequestBody LocalLoginRequest request,
                                  HttpServletRequest req) {
        String ip = req.getRemoteAddr();
        if (!rateLimiter.allowAttempt(ip)) {
            return R.fail(429, "登录尝试过于频繁，请5分钟后再试");
        }
        try {
            LoginVO loginVO = authService.localLogin(request);
            rateLimiter.clearAttempt(ip);
            log.info("本地登录成功: username={}", loginVO.getUserInfo().getUsername());
            return R.ok("登录成功", loginVO);
        } catch (BadCredentialsException e) {
            log.warn("本地登录失败: {}", e.getMessage());
            return R.fail(401, e.getMessage());
        }
    }

    /**
     * 登出 — 将当前Token加入黑名单
     */
    @PostMapping("/logout")
    @ApiOperation("登出")
    public R<String> logout(HttpServletRequest req) {
        String bearer = req.getHeader("Authorization");
        if (bearer != null && bearer.startsWith("Bearer ")) {
            String token = bearer.substring(7);
            // Token黑名单有效期设为剩余有效时间（秒），保守设为4h
            tokenBlacklist.add(token, 14400);
        }
        SecurityContextHolder.clearContext();
        return R.ok("已登出");
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
        Object principal = authentication.getPrincipal();
        if (!(principal instanceof JwtUserPrincipal)) {
            return R.fail(401, "认证信息异常");
        }
        JwtUserPrincipal jwtPrincipal = (JwtUserPrincipal) principal;
        UserInfoVO userInfo = authService.getCurrentUserInfo(jwtPrincipal.getUserId());
        return R.ok(userInfo);
    }

    /**
     * 可选审批人列表（教师+管理员，所有登录用户可用）
     */
    @GetMapping("/approvers")
    @ApiOperation("可选审批人列表（教师+管理员，所有登录用户可用）")
    public R<java.util.List<com.gzhu.equipment.entity.SysUser>> listApprovers() {
        var users = sysUserMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.gzhu.equipment.entity.SysUser>()
                        .in(com.gzhu.equipment.entity.SysUser::getUserType, 1, 2, 3)
                        .eq(com.gzhu.equipment.entity.SysUser::getStatus, 1)
                        .orderByAsc(com.gzhu.equipment.entity.SysUser::getUserType)
        );
        return R.ok(users);
    }

    @PutMapping("/change-password")
    @ApiOperation("当前登录用户修改密码")
    public R<String> changePassword(@RequestBody java.util.Map<String,String> body) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || !(authentication.getPrincipal() instanceof JwtUserPrincipal)) {
            return R.fail(401, "未登录");
        }
        JwtUserPrincipal principal = (JwtUserPrincipal) authentication.getPrincipal();
        String oldPassword = body.get("oldPassword");
        String newPassword = body.get("newPassword");
        if (oldPassword == null || newPassword == null) return R.fail(400, "旧密码和新密码不能为空");
        if (newPassword.length() < 8) return R.fail(400, "新密码至少8位");

        com.gzhu.equipment.entity.SysUser user = sysUserMapper.selectById(principal.getUserId());
        if (user == null) return R.fail(404, "用户不存在");
        if (!"L".equals(user.getAuthSource())) return R.fail(400, "CAS用户请使用CAS登录，系统自动同步密码");

        // 验证旧密码
        try {
            if (!authService.verifyPassword(oldPassword, user.getPassword())) {
                return R.fail(400, "旧密码不正确");
            }
        } catch (Exception e) {
            return R.fail(400, "旧密码不正确");
        }

        user.setPassword(authService.encodePassword(newPassword));
        sysUserMapper.updateById(user);
        log.info("用户修改密码: userId={} username={}", user.getId(), user.getUsername());
        return R.ok("密码修改成功，请用新密码重新登录");
    }

    @GetMapping("/health")
    @ApiOperation("认证服务健康检查（含CAS模式信息）")
    public R<java.util.Map<String,Object>> health() {
        return R.ok(java.util.Map.of(
            "status", "UP",
            "casMode", java.util.Map.of(
                "devMode", casDevMode,
                "userinfoUrl", casUserInfoUrl
            )
        ));
    }
}
