package com.gzhu.equipment.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录成功返回 — JWT token + 用户摘要
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginVO {

    /** JWT访问令牌 */
    private String accessToken;

    /** Token类型（固定 Bearer） */
    private String tokenType;

    /** Token有效期（毫秒） */
    private Long expiresIn;

    /** 用户基本信息 */
    private UserInfoVO userInfo;
}
