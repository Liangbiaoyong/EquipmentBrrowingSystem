package com.gzhu.equipment.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gzhu.equipment.dto.CasLoginRequest;
import com.gzhu.equipment.dto.LocalLoginRequest;
import com.gzhu.equipment.entity.SysUser;
import com.gzhu.equipment.security.JwtTokenProvider;
import com.gzhu.equipment.security.PermissionConstants;
import com.gzhu.equipment.security.UserDetailsServiceImpl;
import com.gzhu.equipment.service.AuthService;
import com.gzhu.equipment.service.SysUserService;
import com.gzhu.equipment.vo.LoginVO;
import com.gzhu.equipment.vo.UserInfoVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * 认证服务实现 — CAS统一认证 + 本地登录
 *
 * CAS认证流程（参考 TEMP/cas_login_json_doc.md）：
 * 1. 前端完成CAS跳转登录，获取token
 * 2. 前端将token传给后端POST /auth/cas/login
 * 3. 后端用token调用 https://libbooking.gzhu.edu.cn/ic-web/auth/userInfo
 * 4. 从用户信息API返回的JSON中提取用户属性
 * 5. 创建或更新本地sys_user记录
 * 6. 签发JWT返回
 *
 * 学生/教师判别（参考 TEMP/学生教师账户判别方法.txt）：
 * - ident字段: 257=学生, 259=教师
 * - 辅助判断: 教师 deptName==className, 学生 deptName!=className
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final SysUserService sysUserService;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    @Value("${cas.userinfo-url:https://libbooking.gzhu.edu.cn/ic-web/auth/userInfo}")
    private String casUserInfoUrl;

    // ==================== CAS 登录 ====================

    @Override
    public LoginVO casLogin(CasLoginRequest request) {
        // Step 1: 调用CAS用户信息API验证token
        JsonNode userData = fetchCasUserInfo(request.getToken(), request.getCookies());
        if (userData == null) {
            throw new BadCredentialsException("CAS token无效或已过期");
        }

        // Step 2: 解析CAS返回的用户信息
        SysUser casUser = parseCasUserInfo(userData);
        if (casUser.getUsername() == null || casUser.getUsername().isEmpty()) {
            throw new BadCredentialsException("CAS返回的用户信息不完整，缺少用户名");
        }

        // Step 3: 创建或更新本地用户
        SysUser savedUser = sysUserService.createOrUpdateCasUser(casUser);

        // Step 4: 签发JWT
        return buildLoginVO(savedUser);
    }

    /**
     * 调用CAS用户信息API —— GET https://libbooking.gzhu.edu.cn/ic-web/auth/userInfo
     *
     * 请求头：token, lan, accept, cookie
     * 响应格式：{"code":"0", "message":"success", "data":{...}}
     */
    private JsonNode fetchCasUserInfo(String token, String cookies) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("accept", "application/json, text/plain, */*");
            headers.set("lan", "1");
            headers.set("token", token);
            headers.set("user-agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36");

            if (StringUtils.hasText(cookies)) {
                headers.set("cookie", cookies);
            }

            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    casUserInfoUrl, HttpMethod.GET, entity, String.class);

            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                log.warn("CAS userInfo API返回非200: status={}", response.getStatusCodeValue());
                return null;
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            String code = root.path("code").asText();

            // code不为"0"即为失败（message字段仅供参考，不作为成功判定依据）
            if (!"0".equals(code)) {
                log.warn("CAS userInfo API返回错误: code={}, message={}",
                        code, root.path("message").asText(""));
                return null;
            }

            return root.path("data");

        } catch (Exception e) {
            log.error("调用CAS userInfo API失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 解析CAS用户信息JSON为SysUser实体
     *
     * CAS API返回data字段包含：
     * uuid, accNo, logonName, trueName, deptName, className,
     * classId, deptId, ident, cardNo, sex, email, handPhone,
     * status, expiredDate, ...
     */
    private SysUser parseCasUserInfo(JsonNode data) {
        SysUser user = new SysUser();

        // 基本信息
        user.setCasUuid(textOrNull(data, "uuid"));
        user.setUsername(textOrNull(data, "logonName"));  // 学工号
        user.setRealName(textOrNull(data, "trueName"));
        user.setAccNo(intOrNull(data, "accNo"));

        // 部门/班级
        user.setDepartment(textOrNull(data, "deptName"));
        user.setClassName(textOrNull(data, "className"));
        user.setClassId(longOrNull(data, "classId"));
        user.setDeptId(longOrNull(data, "deptId"));

        // 身份
        Integer ident = intOrNull(data, "ident");
        user.setIdent(ident);

        // 根据ident判断用户类型
        if (ident != null) {
            if (ident == 257) {
                user.setUserType(0);  // 学生
            } else if (ident == 259) {
                user.setUserType(1);  // 教师
            } else {
                user.setUserType(0);  // 默认学生
            }
        }

        // 卡片
        user.setCardNo(textOrNull(data, "cardNo"));

        // 性别
        Integer sex = intOrNull(data, "sex");
        user.setSex(sex != null ? sex : 0);

        // 联系方式
        String email = textOrNull(data, "email");
        if (email != null && !email.isEmpty()) {
            user.setEmail(email);
        }
        String phone = textOrNull(data, "handPhone");
        if (phone != null && !phone.isEmpty()) {
            user.setPhone(phone);
        }

        // 账号状态
        Integer status = intOrNull(data, "status");
        user.setStatus(status != null && status == 1 ? 1 : 0);

        // 过期日期
        user.setExpiredDate(intOrNull(data, "expiredDate"));

        return user;
    }

    // ==================== 本地登录 ====================

    @Override
    public LoginVO localLogin(LocalLoginRequest request) {
        SysUser user = sysUserService.getByUsername(request.getUsername());
        if (user == null) {
            throw new BadCredentialsException("用户名或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() == 0) {
            throw new BadCredentialsException("用户已被禁用");
        }
        if (!"L".equals(user.getAuthSource())) {
            throw new BadCredentialsException("CAS用户请使用统一认证登录");
        }
        if (!StringUtils.hasText(user.getPassword())) {
            throw new BadCredentialsException("账户未设置密码");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BadCredentialsException("用户名或密码错误");
        }

        return buildLoginVO(user);
    }

    // ==================== 用户信息 ====================

    @Override
    public UserInfoVO getCurrentUserInfo(Long userId) {
        SysUser user = sysUserService.getById(userId);
        if (user == null || user.getStatus() == 0) {
            throw new BadCredentialsException("用户不存在或已禁用");
        }
        return buildUserInfoVO(user);
    }

    // ==================== 构建返回VO ====================

    private LoginVO buildLoginVO(SysUser user) {
        List<String> roles = new ArrayList<>();
        String role = UserDetailsServiceImpl.getUserRole(user.getUserType());
        if (role != null) {
            roles.add("ROLE_" + role);
        }

        String token = jwtTokenProvider.generateToken(
                user.getId(), user.getUsername(), user.getUserType(), roles);

        return LoginVO.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getExpirationMs())
                .userInfo(buildUserInfoVO(user))
                .build();
    }

    private UserInfoVO buildUserInfoVO(SysUser user) {
        List<String> roles = new ArrayList<>();
        String role = UserDetailsServiceImpl.getUserRole(user.getUserType());
        if (role != null) {
            roles.add(role);
        }
        List<String> permissions = PermissionConstants.getPermissionsByUserType(user.getUserType());

        return UserInfoVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .realName(user.getRealName())
                .userType(user.getUserType())
                .userTypeName(UserDetailsServiceImpl.getUserTypeName(user.getUserType()))
                .department(user.getDepartment())
                .className(user.getClassName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .authSource(user.getAuthSource())
                .roles(roles)
                .permissions(permissions)
                .build();
    }

    // ==================== JSON解析辅助方法 ====================

    private String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.asText().isEmpty()) {
            return null;
        }
        return value.asText();
    }

    private Integer intOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isInt()) {
            return value.asInt();
        }
        // 可能是字符串形式的数字
        try {
            return Integer.parseInt(value.asText());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long longOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isLong() || value.isInt()) {
            return value.asLong();
        }
        try {
            return Long.parseLong(value.asText());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
