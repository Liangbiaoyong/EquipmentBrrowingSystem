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
     * CAS统一认证登录
     *
     * 流程：
     * 1. 使用frontend传来的token调用CAS userInfo API验证
     * 2. 解析CAS返回的用户信息
     * 3. 创建或更新本地用户记录
     * 4. 签发JWT并返回
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
}
