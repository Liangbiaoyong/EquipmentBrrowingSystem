package com.gzhu.equipment.service;

import com.gzhu.equipment.dto.CasLoginRequest;
import com.gzhu.equipment.dto.LocalLoginRequest;
import com.gzhu.equipment.vo.LoginVO;
import com.gzhu.equipment.vo.UserInfoVO;

/**
 * 认证服务接口
 */
public interface AuthService {

    /**
     * CAS服务端无感登录（用户名+密码）
     * 后端完成完整CAS流程：GET登录页→JS加密→POST→跟随跳转→提取token→调用userInfo→创建/更新用户→签发JWT
     */
    LoginVO casCredentialLogin(String username, String password);

    /**
     * CAS统一认证登录（前端token回调）
     */
    LoginVO casLogin(CasLoginRequest request);

    /**
     * 本地账户登录
     *
     * 流程：
     * 1. 查用户表，仅 auth_source=L 的账户可登录
     * 2. BCrypt校验密码
     * 3. 签发JWT并返回
     */
    LoginVO localLogin(LocalLoginRequest request);

    /**
     * 获取当前登录用户信息
     */
    UserInfoVO getCurrentUserInfo(Long userId);

    /** 验证密码（BCrypt） */
    boolean verifyPassword(String rawPassword, String encodedPassword);

    /** 加密密码（BCrypt） */
    String encodePassword(String rawPassword);
}
