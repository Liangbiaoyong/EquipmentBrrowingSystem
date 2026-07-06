package com.gzhu.equipment.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * CAS 登录请求 — 前端获取CAS token后提交
 */
@Data
public class CasLoginRequest {

    /** CAS认证后获取的token（从CAS回调中提取） */
    @NotBlank(message = "CAS token不能为空")
    private String token;

    /**
     * CAS登录后返回的cookie信息（可选）
     * 格式: "JSESSIONID=xxxxx; route=xxxxx"
     */
    private String cookies;
}
