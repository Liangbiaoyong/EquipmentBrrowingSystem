package com.gzhu.equipment.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gzhu.equipment.service.CasServerLoginService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * CAS 服务端无感登录实现
 *
 * 完全复刻 TEMP/inspect_userinfo_fields.py 的登录流程：
 * GET CAS登录页 → 提取form字段 → JS引擎RSA加密 → POST登录
 * → 跟随302跳转(手动关闭自动重定向) → 从跳转链提取token
 * → 调用userInfo API验证 → 返回用户信息
 */
@Slf4j
@Service
public class CasServerLoginServiceImpl implements CasServerLoginService {

    @Value("${cas.server-url:https://newcas.gzhu.edu.cn/cas}")
    private String casServerUrl;

    @Value("${cas.userinfo-url:https://libbooking.gzhu.edu.cn/ic-web/auth/userInfo}")
    private String casUserInfoUrl;

    @Value("${cas.request-timeout:15000}")
    private int requestTimeout;

    @Value("${cas.auth-address-url:}")
    private String authAddressUrl;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private String cachedDesJs = null;

    private static final String DES_JS_URL = "https://newcas.gzhu.edu.cn/cas/comm/js/des.js";

    // CAS bootstrap: 通过 auth/address 端点发现正确的登录URL
    private static final String AUTH_ADDRESS_PATH = "/ic-web/auth/address";
    private static final String FINAL_ADDRESS = "https://libbooking.gzhu.edu.cn";
    private static final String ERR_PAGE_URL = "https://libbooking.gzhu.edu.cn/#/error";

    // 备用service（当bootstrap发现失败时使用 — 已知在CAS注册的合法service）
    private static final String FALLBACK_SERVICE_URL = "http://libbooking.gzhu.edu.cn/authcenter/doAuth/4edbd40b8d1b4ef8970355950765d41f";

    @Override
    public JsonNode login(String username, String password) throws IOException {
        log.info("CAS服务端登录开始: username={}", username);

        // Step 0: 通过bootstrap发现CAS登录URL（动态获取正确的service参数）
        String loginPageUrl = discoverLoginUrl();
        log.info("CAS: 登录URL确定为: {}", loginPageUrl);

        // Step 0.5: 预热 — 先GET userInfo API初始化session cookie（复刻Python非WebView流程）
        Map<String, String> sessionCookies = preflightSession();
        log.info("CAS: 预热后sessionCookies={}", sessionCookies.keySet());

        // Step 1: GET 登录页 → 提取 lt, execution（保持session cookie）
        HttpGetResult loginPageResult = httpGetWithCookies(loginPageUrl, null);
        String loginPageHtml = loginPageResult.body;
        sessionCookies.putAll(loginPageResult.cookies);
        log.info("CAS: 登录页HTML长度={} cookies={}", loginPageHtml.length(), sessionCookies.keySet());

        // 提取所有表单字段（与Python non_webview_login.py一致）
        Map<String, String> formFields = extractAllFormInputs(loginPageHtml);
        String lt = formFields.get("lt");
        String execution = formFields.get("execution");
        if (lt == null || execution == null) {
            log.warn("CAS登录页解析失败，HTML前500字符: {}", loginPageHtml.substring(0, Math.min(500, loginPageHtml.length())));
            throw new RuntimeException("CAS登录页解析失败，无法提取lt/execution");
        }
        log.info("CAS: lt={} execution={} formFields={}",
            lt.substring(0, Math.min(10, lt.length())),
            execution.substring(0, Math.min(10, execution.length())),
            formFields.keySet());

        // Step 2: JS引擎加密密码
        String rsa = encryptPassword(username, password, lt);
        log.info("CAS: RSA加密完成 length={} preview={}", rsa.length(), rsa.substring(0, Math.min(20, rsa.length())));

        // Step 3: POST 登录 → 跟随跳转 → 提取 token
        // 构造POST body — 与Python一致，提交登录页所有字段 + rsa/ul/pl覆盖密码
        StringBuilder sb = new StringBuilder();
        sb.append("rsa=").append(URLEncoder.encode(rsa, "UTF-8"));
        sb.append("&ul=").append(username.length());
        sb.append("&pl=").append(password.length());
        for (Map.Entry<String, String> f : formFields.entrySet()) {
            String k = f.getKey();
            if ("rsa".equals(k) || "ul".equals(k) || "pl".equals(k)) continue;
            if ("un".equals(k) || "pd".equals(k) || "username".equals(k) || "password".equals(k)) continue;
            if (f.getValue() != null && !f.getValue().isEmpty()) {
                sb.append("&").append(k).append("=").append(URLEncoder.encode(f.getValue(), "UTF-8"));
            }
        }
        String postBody = sb.toString();
        String postUrl = loginPageUrl;

        RedirectResult redirectResult = httpPostWithRedirects(postUrl, postBody, loginPageUrl, sessionCookies);
        if (redirectResult.token == null) {
            log.warn("CAS跳转链状态: hops={} cookies={} finalUrl={} finalStatus={}",
                redirectResult.hops, redirectResult.cookies.keySet(),
                redirectResult.finalUrl, redirectResult.finalStatus);
            if (redirectResult.errorBody != null && redirectResult.errorBody.contains("账号")) {
                log.warn("CAS错误诊断(检测到'账号'): {}",
                    redirectResult.errorBody.substring(0, Math.min(300, redirectResult.errorBody.length())));
            } else if (redirectResult.errorBody != null && redirectResult.errorBody.contains("验证码")) {
                log.warn("CAS错误诊断(检测到'验证码'): {}",
                    redirectResult.errorBody.substring(0, Math.min(300, redirectResult.errorBody.length())));
            } else if (redirectResult.errorBody != null) {
                log.warn("CAS最终响应片段(前200): {}",
                    redirectResult.errorBody.substring(0, Math.min(200, redirectResult.errorBody.length())));
            }

            // 构建详细错误信息
            String errMsg = "CAS登录失败：未能从跳转中提取token";
            if (redirectResult.hops == 0 && redirectResult.finalStatus == 200) {
                // POST返回200(不是302) — 最可能是账号密码错误
                if (redirectResult.errorBody != null && redirectResult.errorBody.contains("账号")) {
                    errMsg += "（账号或密码错误）";
                } else if (redirectResult.errorBody != null && redirectResult.errorBody.contains("验证码")) {
                    errMsg += "（触发验证码）";
                } else if (redirectResult.errorBody != null && redirectResult.errorBody.contains("用户")) {
                    errMsg += "（用户不存在）";
                } else {
                    errMsg += "（POST登录后CAS返回登录页，可能账号密码错误或验证码）";
                }
            } else {
                errMsg += "（跳转链" + redirectResult.hops + "次，最终状态" + redirectResult.finalStatus + "，未找到token）";
            }
            throw new RuntimeException(errMsg);
        }
        log.info("CAS: token提取成功 preview={} source={}",
            redirectResult.token.substring(0, Math.min(30, redirectResult.token.length())),
            redirectResult.tokenSource);

        // Step 4: 用token调userInfo API
        // 合并session cookies和跳转链cookies
        if (redirectResult.cookies != null) {
            sessionCookies.putAll(redirectResult.cookies);
        }
        String cookies = sessionCookies.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining("; "));

        log.info("CAS: 调userInfo API token={} cookieKeys={}",
            redirectResult.token.substring(0, Math.min(20, redirectResult.token.length())),
            sessionCookies.keySet());
        String userInfoBody = httpGet(casUserInfoUrl, Map.of("token", redirectResult.token, "cookie", cookies));
        log.info("CAS: userInfo响应(前200)={}", userInfoBody.substring(0, Math.min(200, userInfoBody.length())));
        JsonNode root = objectMapper.readTree(userInfoBody);
        String code = root.path("code").asText();
        if (!"0".equals(code)) {
            throw new RuntimeException("CAS userInfo API返回失败: code=" + code + ", body=" + userInfoBody.substring(0, Math.min(200, userInfoBody.length())));
        }
        return root.path("data");
    }

    // ==================== CAS登录URL发现 ====================

    /**
     * 会话预热 — GET userInfo API以初始化session cookie（JSESSIONID等）
     * 对应Python的 _discover_to_login_page_url() 中的 pre_userinfo 请求
     */
    private Map<String, String> preflightSession() {
        Map<String, String> cookies = new LinkedHashMap<>();
        try {
            // 使用 httpGetWithCookies 而不是 httpGet
            // 先请求 userInfo 以初始化和收集 session cookie
            URL url = new URL(casUserInfoUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(requestTimeout);
            conn.setReadTimeout(requestTimeout);
            conn.setInstanceFollowRedirects(false);
            conn.setRequestProperty("accept", "application/json, text/plain, */*");
            conn.setRequestProperty("lan", "1");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            // 读取响应体（但不使用）
            readResponse(conn);
            // 提取Set-Cookie
            cookies.putAll(extractCookies(conn));
            log.info("CAS预热: userInfo cookies={}", cookies.isEmpty() ? "(无)" : cookies.keySet().toString());

            // 再请求 auth/address 以进一步收集 cookie（对应Python流程）
            String addressUrl = buildAuthAddressUrl();
            URL addrUrl = new URL(addressUrl);
            HttpURLConnection addrConn = (HttpURLConnection) addrUrl.openConnection();
            addrConn.setRequestMethod("GET");
            addrConn.setConnectTimeout(requestTimeout);
            addrConn.setReadTimeout(requestTimeout);
            addrConn.setInstanceFollowRedirects(false);
            addrConn.setRequestProperty("accept", "application/json, text/plain, */*");
            addrConn.setRequestProperty("lan", "1");
            addrConn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            if (!cookies.isEmpty()) {
                addrConn.setRequestProperty("Cookie", cookies.entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining("; ")));
            }
            readResponse(addrConn);
            cookies.putAll(extractCookies(addrConn));
            log.info("CAS预热: auth/address cookies={}", cookies.isEmpty() ? "(无)" : cookies.keySet().toString());
        } catch (Exception e) {
            log.warn("CAS预热失败: {}", e.getMessage());
        }
        return cookies;
    }

    /**
     * 通过CAS auth/address API发现正确的登录URL（含已在CAS注册的合法service参数）
     *
     * 流程：调用 auth/address → 获取 redirect URL → 跟随302 → 得到CAS登录页URL
     * 与 TEMP/non_webview_login.py 的 _discover_to_login_page_url() 逻辑一致
     */
    private String discoverLoginUrl() throws IOException {
        // 优先尝试 bootstrap 发现
        try {
            String addressApiUrl = buildAuthAddressUrl();
            log.info("CAS引导: 调auth/address API: {}", addressApiUrl);
            String respBody = httpGet(addressApiUrl, null);
            JsonNode root = objectMapper.readTree(respBody);
            JsonNode dataNode = root.get("data");

            if (dataNode != null && dataNode.isTextual()) {
                String redirectUrl = dataNode.asText();
                if (redirectUrl.startsWith("http")) {
                    log.info("CAS引导: auth/address返回data={}", redirectUrl);

                    // 跟随302到CAS登录页
                    URL redirectFullUrl = new URL(redirectUrl);
                    HttpURLConnection conn = (HttpURLConnection) redirectFullUrl.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(requestTimeout);
                    conn.setReadTimeout(requestTimeout);
                    conn.setInstanceFollowRedirects(false);
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");

                    int status = conn.getResponseCode();
                    String location = conn.getHeaderField("Location");
                    if (status >= 300 && status < 400 && StringUtils.hasText(location)) {
                        // CAS返回的Location可能是绝对URL或相对路径
                        if (location.startsWith("http")) {
                            log.info("CAS引导: 跟随绝对跳转到 {}", location);
                            return location;
                        }
                        // 相对路径：使用Java URL解析（比字符串拼接更健壮）
                        URL resolved = new URL(redirectFullUrl, location);
                        log.info("CAS引导: 跟随相对跳转到 {}", resolved);
                        return resolved.toString();
                    }
                    // 没有302，说明 redirectUrl 本身就是CAS登录页
                    if (status == 200) {
                        log.info("CAS引导: auth/address返回的URL直接可访问");
                        return redirectUrl;
                    }
                }
            }
            log.warn("CAS引导: auth/address返回异常 dataNode={}", dataNode);
        } catch (Exception e) {
            log.warn("CAS引导失败: {}", e.getMessage());
        }

        // 备用：使用已知在CAS注册的合法service URL
        log.warn("CAS引导失败，回退到已知service URL");
        return casServerUrl + "/login?service=" + URLEncoder.encode(FALLBACK_SERVICE_URL, "UTF-8");
    }

    /**
     * 构建 auth/address API URL
     * 优先用配置 cas.auth-address-url，否则从 cas.userinfo-url 推导
     */
    private String buildAuthAddressUrl() throws UnsupportedEncodingException {
        if (StringUtils.hasText(authAddressUrl)) {
            return authAddressUrl;
        }
        // 从 userInfo URL 推导：/ic-web/auth/userInfo → /ic-web/auth/address
        String base = casUserInfoUrl.replace("/userInfo", "/address");
        return base + "?finalAddress=" + URLEncoder.encode(FINAL_ADDRESS, "UTF-8")
                + "&errPageUrl=" + URLEncoder.encode(ERR_PAGE_URL, "UTF-8")
                + "&manager=false&consoleType=16";
    }

    // ==================== 密码加密 ====================

    private String encryptPassword(String username, String password, String lt) {
        try {
            // 加载并缓存 des.js
            if (cachedDesJs == null) {
                cachedDesJs = httpGet(DES_JS_URL, null);
                log.info("CAS: des.js加载完成 length={}", cachedDesJs.length());
            }

            ScriptEngine engine = new ScriptEngineManager().getEngineByName("nashorn");
            if (engine == null) {
                engine = new ScriptEngineManager().getEngineByName("javascript");
            }
            if (engine == null) {
                throw new RuntimeException("无可用JS引擎（需要nashorn-core依赖）");
            }

            engine.eval(cachedDesJs);
            String plainText = username + password + lt;
            Object result = engine.eval("strEnc('" + escapeJs(plainText) + "', '1', '2', '3')");
            return result != null ? result.toString() : "";
        } catch (Exception e) {
            log.error("CAS密码加密失败: {}", e.getMessage(), e);
            throw new RuntimeException("CAS密码加密失败: " + e.getMessage());
        }
    }

    private String escapeJs(String s) {
        return s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\r", "\\r");
    }

    // ==================== HTTP 工具 ====================

    private String httpGet(String url, Map<String, String> headers) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(requestTimeout);
        conn.setReadTimeout(requestTimeout);
        conn.setInstanceFollowRedirects(false);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        if (headers != null) {
            headers.forEach(conn::setRequestProperty);
        }
        return readResponse(conn);
    }

    /**
     * GET请求+提取Cookies（用于登录页获取和预热）
     */
    private HttpGetResult httpGetWithCookies(String url, Map<String, String> headers) throws IOException {
        URL requestUrl = new URL(url);
        HttpURLConnection conn = (HttpURLConnection) requestUrl.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(requestTimeout);
        conn.setReadTimeout(requestTimeout);
        conn.setInstanceFollowRedirects(false);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        if (headers != null) {
            headers.forEach(conn::setRequestProperty);
        }
        String body = readResponse(conn);
        Map<String, String> cookies = extractCookies(conn);
        return new HttpGetResult(body, cookies);
    }

    private RedirectResult httpPostWithRedirects(String url, String body, String referer, Map<String, String> sessionCookies) throws IOException {
        // Step A: POST 登录（带上sessionCookies）
        URL currentUrl = new URL(url);
        HttpURLConnection conn = (HttpURLConnection) currentUrl.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(requestTimeout);
        conn.setReadTimeout(requestTimeout);
        conn.setInstanceFollowRedirects(false);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setRequestProperty("Origin", "https://newcas.gzhu.edu.cn");
        conn.setRequestProperty("Referer", referer);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        // 携带session cookies（如JSESSIONID）
        if (sessionCookies != null && !sessionCookies.isEmpty()) {
            conn.setRequestProperty("Cookie", sessionCookies.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining("; ")));
        }

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        // 诊断：检测POST响应状态码
        int postStatus = conn.getResponseCode();
        String postLocation = conn.getHeaderField("Location");
        log.info("CAS POST响应: status={} location={}", postStatus, postLocation);

        // Step B: 跟随302跳转链（最多15次），提取token
        Map<String, String> cookies = new LinkedHashMap<>();
        // 先合并session cookies作为基础（后续跳转会覆盖Set-Cookie）
        if (sessionCookies != null) cookies.putAll(sessionCookies);
        cookies.putAll(extractCookies(conn));

        String token = null;
        String tokenSource = null;
        int hops = 0;
        Set<String> visited = new HashSet<>();
        String location = postLocation;
        String errorBody = null;
        int finalStatus = postStatus;
        int firstHopStatus = postStatus;

        // 如果POST直接返回200(不是302)，读取其body尝试提取token和诊断
        if (location == null && postStatus == 200) {
            String respBody = readResponse(conn);
            token = extractTokenFromBody(respBody);
            if (token != null) {
                tokenSource = "post-body";
                log.info("CAS: 从POST响应体提取到token");
            }
            // 保存错误诊断（可能是登录页错误信息）
            if (respBody.length() < 5000) {
                errorBody = respBody;
            } else {
                errorBody = respBody.substring(0, 500);
            }
            finalStatus = postStatus;
        }

        while (location != null && hops < 15 && !visited.contains(location)) {
            hops++;
            visited.add(location);
            log.info("CAS跳转[{}]: status={} location={}", hops,
                hops == 1 ? postStatus : -1, location);

            // 从URL提取token
            token = extractTokenFromUrl(location);
            if (token != null) {
                tokenSource = "url-hop" + hops;
                log.info("CAS: 从URL提取到token (hop={})", hops);
                break;
            }

            // 将相对URL解析为绝对URL（CAS可能返回相对路径的Location）
            URL nextUrl = new URL(currentUrl, location);
            currentUrl = nextUrl;

            HttpURLConnection next = (HttpURLConnection) nextUrl.openConnection();
            next.setRequestMethod("GET");
            next.setConnectTimeout(requestTimeout);
            next.setReadTimeout(requestTimeout);
            next.setInstanceFollowRedirects(false);
            next.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            if (!cookies.isEmpty()) {
                next.setRequestProperty("Cookie", cookies.entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining("; ")));
            }

            int respStatus = next.getResponseCode();
            finalStatus = respStatus;
            location = next.getHeaderField("Location");
            log.info("CAS跳转[{}]响应: status={} nextLocation={}", hops, respStatus, location);

            // 从响应体JSON提取token
            if (location == null) {
                String respBody = readResponse(next);
                token = extractTokenFromBody(respBody);
                if (token != null) {
                    tokenSource = "body-hop" + hops;
                    log.info("CAS: 从响应体JSON提取到token (hop={})", hops);
                    break;
                }

                // ★ 从响应头提取token（Python有这项，Java遗漏了）
                token = extractTokenFromHeaders(next);
                if (token != null) {
                    tokenSource = "header-hop" + hops;
                    log.info("CAS: 从响应头提取到token (hop={})", hops);
                    break;
                }

                // 诊断：保存最终响应片段
                if (respBody.length() < 5000) {
                    errorBody = respBody;
                } else {
                    errorBody = respBody.substring(0, 500);
                }
            }

            // 从cookie中提取token（兜底）
            Map<String, String> hopCookies = extractCookies(next);
            if (token == null) {
                for (Map.Entry<String, String> c : hopCookies.entrySet()) {
                    String ck = c.getKey().toLowerCase();
                    if (ck.contains("token") || ck.contains("auth")) {
                        token = c.getValue();
                        tokenSource = "cookie-" + c.getKey();
                        log.info("CAS: 从cookie提取到token key={}", c.getKey());
                        break;
                    }
                }
            }
            cookies.putAll(hopCookies);
        }

        RedirectResult result = new RedirectResult();
        result.token = token;
        result.tokenSource = tokenSource;
        result.cookies = cookies;
        result.hops = hops;
        result.finalUrl = currentUrl.toString();
        result.finalStatus = finalStatus;
        result.errorBody = errorBody;
        return result;
    }

    // ==================== HTML/JSON/Header 解析 ====================

    private String extractPattern(String html, String regex) {
        Matcher m = Pattern.compile(regex).matcher(html);
        return m.find() ? m.group(1) : null;
    }

    /**
     * 从CAS登录页HTML中提取所有表单input字段
     * 对应 Python non_webview_login.py 的 _extract_form_inputs()
     */
    private Map<String, String> extractAllFormInputs(String html) {
        Map<String, String> fields = new LinkedHashMap<>();
        // 匹配 &lt;input name="xxx" ... value="yyy" /&gt;
        Pattern p = Pattern.compile("<input[^>]*name=\"([^\"]+)\"[^>]*>", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(html);
        while (m.find()) {
            String inputTag = m.group(0);
            String name = m.group(1);
            // 提取value属性
            String value = "";
            Matcher vm = Pattern.compile("value=\"([^\"]*)\"").matcher(inputTag);
            if (vm.find()) {
                value = vm.group(1);
            }
            fields.put(name, value);
        }
        log.info("CAS: 登录页提取到 {} 个表单字段: {}", fields.size(), fields.keySet());
        return fields;
    }

    /**
     * 从URL query参数提取token（覆盖所有已知key，包括CAS标准ticket参数）
     * 对应Python _collect_token_candidates() 步骤1+2
     */
    private String extractTokenFromUrl(String url) {
        for (String key : Arrays.asList("token", "uniToken", "access_token", "accessToken", "ticket")) {
            String val = extractUrlParam(url, key);
            if (val != null && !val.isEmpty()) return val;
        }
        return null;
    }

    /**
     * 从JSON响应体提取token（递归查找 data 子对象）
     * 对应Python _collect_token_candidates() 中 final_body_json 的处理
     */
    private String extractTokenFromBody(String body) {
        if (body == null || body.isEmpty()) return null;
        try {
            JsonNode node = objectMapper.readTree(body);
            // 1. 顶层字符串字段
            for (String key : Arrays.asList("token", "accessToken", "uniToken", "access_token")) {
                JsonNode val = node.get(key);
                if (val != null && val.isTextual() && !val.asText().isEmpty()) return val.asText();
            }
            // 2. data 子对象中的 token 字段
            JsonNode data = node.get("data");
            if (data != null && data.isObject()) {
                for (String key : Arrays.asList("token", "accessToken", "uniToken", "access_token")) {
                    JsonNode val = data.get(key);
                    if (val != null && val.isTextual() && !val.asText().isEmpty()) return val.asText();
                }
            }
            // 3. 正则兜底：匹配 "token":"xxx" 或 "accessToken":"xxx"
            Matcher m = Pattern.compile("\"(?:token|accessToken|access_token)\"\\s*:\\s*\"([^\"]+)\"").matcher(body);
            if (m.find()) return m.group(1);
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * 从响应头提取token（Python代码有但Java遗漏的关键功能！）
     * 对应Python _collect_token_candidates() 步骤3
     */
    private String extractTokenFromHeaders(HttpURLConnection conn) {
        for (String key : Arrays.asList("token", "Token", "x-auth-token", "X-Auth-Token",
                                         "authorization", "Authorization")) {
            String value = conn.getHeaderField(key);
            if (value != null && !value.isEmpty()) {
                value = value.trim();
                if (value.regionMatches(true, 0, "Bearer ", 0, 7)) {
                    value = value.substring(7).trim();
                }
                if (!value.isEmpty()) {
                    return value;
                }
            }
        }
        return null;
    }

    private String extractUrlParam(String url, String key) {
        int qi = url.indexOf('?');
        if (qi < 0) return null;
        for (String pair : url.substring(qi + 1).split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && kv[0].equals(key)) {
                try { return URLDecoder.decode(kv[1], "UTF-8"); } catch (Exception e) { return kv[1]; }
            }
        }
        return null;
    }

    private Map<String, String> extractCookies(HttpURLConnection conn) {
        Map<String, String> cookies = new LinkedHashMap<>();
        List<String> cookieHeaders = conn.getHeaderFields().get("Set-Cookie");
        if (cookieHeaders != null) {
            for (String c : cookieHeaders) {
                String[] parts = c.split(";")[0].split("=", 2);
                if (parts.length == 2) cookies.put(parts[0].trim(), parts[1].trim());
            }
        }
        return cookies;
    }

    private String readResponse(HttpURLConnection conn) throws IOException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                conn.getResponseCode() < 400 ? conn.getInputStream() : conn.getErrorStream(), StandardCharsets.UTF_8))) {
            return br.lines().collect(Collectors.joining("\n"));
        }
    }

    // ==================== 内部类 ====================

    private static class RedirectResult {
        String token;
        String tokenSource; // token来源: url-hopN / body-hopN / header-hopN / cookie-XXX / post-body
        Map<String, String> cookies = new LinkedHashMap<>();
        int hops;
        String finalUrl;
        int finalStatus; // 最终响应的HTTP状态码
        String errorBody; // 诊断：最终响应的HTML片段（用于检测登录失败错误信息）
    }

    private static class HttpGetResult {
        final String body;
        final Map<String, String> cookies;

        HttpGetResult(String body, Map<String, String> cookies) {
            this.body = body;
            this.cookies = cookies;
        }
    }
}
