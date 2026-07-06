package com.gzhu.equipment.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 本地账户登录请求
 */
@Data
public class LocalLoginRequest {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;
}
