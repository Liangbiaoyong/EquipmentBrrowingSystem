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

        // Step 1: GET 登录页 → 提取 lt, execution
        String loginPageHtml = httpGet(loginPageUrl, null);
        log.info("CAS: 登录页HTML长度={}", loginPageHtml.length());

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
        // 使用已发现的登录URL（内含已在CAS注册的合法service参数）
        // 构造POST body — 与Python一致，提交登录页所有字段 + rsa/ul/pl覆盖密码
        StringBuilder sb = new StringBuilder();
        sb.append("rsa=").append(URLEncoder.encode(rsa, "UTF-8"));
        sb.append("&ul=").append(username.length());
        sb.append("&pl=").append(password.length());
        for (Map.Entry<String, String> f : formFields.entrySet()) {
            String k = f.getKey();
            // 跳过已被rsa/ul/pl替代的字段
            if ("rsa".equals(k) || "ul".equals(k) || "pl".equals(k)) continue;
            if ("un".equals(k) || "pd".equals(k) || "username".equals(k) || "password".equals(k)) continue;
            if (f.getValue() != null && !f.getValue().isEmpty()) {
                sb.append("&").append(k).append("=").append(URLEncoder.encode(f.getValue(), "UTF-8"));
            }
        }
        String postBody = sb.toString();
        String postUrl = loginPageUrl;

        RedirectResult redirectResult = httpPostWithRedirects(postUrl, postBody, loginPageUrl);
        if (redirectResult.token == null) {
            log.warn("CAS跳转链状态: hops={} cookies={} finalUrl={}",
                redirectResult.hops, redirectResult.cookies.keySet(),
                redirectResult.finalUrl);
            if (redirectResult.errorBody != null && redirectResult.errorBody.contains("账号")) {
                log.warn("CAS错误诊断(检测到'账号'): {}",
                    redirectResult.errorBody.substring(0, Math.min(300, redirectResult.errorBody.length())));
            } else if (redirectResult.errorBody != null && redirectResult.errorBody.contains("验证码")) {
                log.warn("CAS错误诊断(检测到'验证码'): {}",
                    redirectResult.errorBody.substring(0, Math.min(300, redirectResult.errorBody.length())));
            } else if (redirectResult.errorBody != null) {
                log.warn("CAS最终响应片段: {}",
                    redirectResult.errorBody.substring(0, Math.min(200, redirectResult.errorBody.length())));
            }
            throw new RuntimeException("CAS登录失败：未能从跳转中提取token（可能账号密码错误或验证码）");
        }
        log.info("CAS: token提取成功 preview={}", redirectResult.token.substring(0, Math.min(30, redirectResult.token.length())));

        // Step 4: 用token调userInfo API
        String cookies = redirectResult.cookies != null
                ? redirectResult.cookies.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining("; "))
                : "";

        log.info("CAS: 调userInfo API token={} cookieKeys={}",
            redirectResult.token.substring(0, Math.min(20, redirectResult.token.length())),
            redirectResult.cookies.keySet());
        String userInfoBody = httpGet(casUserInfoUrl, Map.of("token", redirectResult.token, "cookie", cookies));
        log.info("CAS: userInfo响应(前200)={}", userInfoBody.substring(0, Math.min(200, userInfoBody.length())));
        JsonNode root = objectMapper.readTree(userInfoBody);
        String code = root.path("code").asText();
        if (!"0".equals(code)) {
            throw new RuntimeException("CAS userInfo API返回失败: code=" + code);
        }
        return root.path("data");
    }

    // ==================== CAS登录URL发现 ====================

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
                        // 相对路径：尝试拼接当前URL的origin
                        String resolved = redirectFullUrl.getProtocol() + "://" + redirectFullUrl.getHost() + location;
                        log.info("CAS引导: 跟随相对跳转到 {}", resolved);
                        return resolved;
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

    private RedirectResult httpPostWithRedirects(String url, String body, String referer) throws IOException {
        // Step A: POST 登录
        URL currentUrl = new URL(url);
        HttpURLConnection conn = (HttpURLConnection) currentUrl.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(requestTimeout);
        conn.setReadTimeout(requestTimeout);
        conn.setInstanceFollowRedirects(false);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setRequestProperty("Origin", casServerUrl);
        conn.setRequestProperty("Referer", referer);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        // 诊断：检测POST响应状态码
        int postStatus = conn.getResponseCode();
        log.info("CAS POST响应: status={} location={}", postStatus, conn.getHeaderField("Location"));

        // Step B: 跟随302跳转链（最多15次），提取token
        Map<String, String> cookies = extractCookies(conn);
        String token = null;
        int hops = 0;
        Set<String> visited = new HashSet<>();
        String location = conn.getHeaderField("Location");
        String errorBody = null;

        while (location != null && hops < 15 && !visited.contains(location)) {
            hops++;
            visited.add(location);
            log.info("CAS跳转[{}]: status={} location={}", hops,
                hops == 1 ? postStatus : -1, location);

            // 从URL提取token
            token = extractTokenFromUrl(location);
            if (token != null) {
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
            next.setRequestProperty("User-Agent", "Mozilla/5.0");
            if (!cookies.isEmpty()) {
                next.setRequestProperty("Cookie", cookies.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining("; ")));
            }

            int respStatus = next.getResponseCode();
            location = next.getHeaderField("Location");
            log.info("CAS跳转[{}]响应: status={} nextLocation={}", hops, respStatus, location);

            // 从响应体JSON提取token
            if (location == null) {
                String respBody = readResponse(next);
                token = extractTokenFromBody(respBody);
                if (token != null) {
                    log.info("CAS: 从响应体JSON提取到token (hop={})", hops);
                    break;
                }
                // 诊断：保存最终响应片段（用于判断是登录失败还是其他错误）
                if (respBody.length() < 5000) {
                    errorBody = respBody;
                } else {
                    errorBody = respBody.substring(0, 500);
                }
            }

            // 从cookie中提取token（像Python代码一样兜底）
            Map<String, String> hopCookies = extractCookies(next);
            if (token == null) {
                for (Map.Entry<String, String> c : hopCookies.entrySet()) {
                    String ck = c.getKey().toLowerCase();
                    if (ck.contains("token") || ck.contains("auth")) {
                        token = c.getValue();
                        log.info("CAS: 从cookie提取到token key={}", c.getKey());
                        break;
                    }
                }
            }
            cookies.putAll(hopCookies);
        }

        RedirectResult result = new RedirectResult();
        result.token = token;
        result.cookies = cookies;
        result.hops = hops;
        result.finalUrl = currentUrl.toString();
        result.errorBody = errorBody;
        return result;
    }

    // ==================== HTML/JSON 解析 ====================

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

    private String extractTokenFromUrl(String url) {
        for (String key : Arrays.asList("token", "uniToken", "access_token")) {
            String val = extractUrlParam(url, key);
            if (val != null) return val;
        }
        return null;
    }

    private String extractTokenFromBody(String body) {
        if (body == null || body.isEmpty()) return null;
        try {
            JsonNode node = objectMapper.readTree(body);
            for (String key : Arrays.asList("token", "accessToken", "data")) {
                JsonNode val = node.get(key);
                if (val != null && val.isTextual()) return val.asText();
                if (val != null && val.has("token")) return val.get("token").asText();
            }
        } catch (Exception ignored) {}
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
        Map<String, String> cookies = new LinkedHashMap<>();
        int hops;
        String finalUrl;
        String errorBody; // 诊断：最终响应的HTML片段（用于检测登录失败错误信息）
    }
}
