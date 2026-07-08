package com.gzhu.equipment.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;

/**
 * CAS 服务端无感登录 — 仿照 TEMP/inspect_userinfo_fields.py + non_webview_login.py
 *
 * 流程：
 * 1. GET CAS登录页 → 提取 lt, execution
 * 2. 加载 des.js → JS引擎执行 strEnc(用户名+密码+lt) 得到RSA加密串
 * 3. POST CAS登录 → 跟随302跳转 → 提取token + cookies
 * 4. 用token调用 userInfo API → 返回用户信息JSON
 */
public interface CasServerLoginService {

    /**
     * 完整的CAS服务端登录流程
     * @param username CAS用户名（学工号）
     * @param password CAS密码
     * @return userInfo API 返回的 data 节点（用户信息JSON）
     * @throws IOException 网络异常
     * @throws RuntimeException CAS认证失败
     */
    JsonNode login(String username, String password) throws IOException;
}
