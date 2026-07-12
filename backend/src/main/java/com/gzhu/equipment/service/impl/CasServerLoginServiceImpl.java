package com.gzhu.equipment.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gzhu.equipment.service.CasServerLoginService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * CAS 服务端无感登录 — 使用 java.net.http.HttpClient + Node.js DES加密
 * 完整复刻 cas_login.java 登录流程
 */
@Slf4j
@Service
public class CasServerLoginServiceImpl implements CasServerLoginService {

    private static final String CAS_LOGIN_URL = "https://newcas.gzhu.edu.cn/cas/login";
    private static final String SERVICE_URL = "http://libbooking.gzhu.edu.cn/authcenter/doAuth/4edbd40b8d1b4ef8970355950765d41f";
    private static final String AUTHCENTER_TO_LOGIN = "http://libbooking.gzhu.edu.cn/authcenter/toLoginPage";
    private static final String DES_JS_URL = "https://newcas.gzhu.edu.cn/cas/comm/js/des.js";
    private static final String USERINFO_API = "https://libbooking.gzhu.edu.cn/ic-web/auth/userInfo";
    private static final String AUTH_ADDRESS_API =
            "https://libbooking.gzhu.edu.cn/ic-web/auth/address"
            + "?finalAddress=https:%2F%2Flibbooking.gzhu.edu.cn"
            + "&errPageUrl=https:%2F%2Flibbooking.gzhu.edu.cn%2F%23%2Ferror"
            + "&manager=false&consoleType=16";
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36";

    @Value("${cas.request-timeout:30}") private int timeout;

    private final ObjectMapper mapper = new ObjectMapper();
    private String cachedDesJs;

    @Override
    public JsonNode login(String username, String password) throws Exception {
        log.info("CAS登录: username={}", username);

        CookieManager cm = new CookieManager(); cm.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeout))
                .followRedirects(HttpClient.Redirect.NEVER)
                .cookieHandler(cm).build();

        // Step 0: 发现登录URL
        String toLoginUrl = discoverLoginUrl(client);
        log.info("CAS: 引导URL={}", toLoginUrl);

        // Step 1: Bootstrap GET toLoginUrl → CAS login page
        HttpRequest bootReq = HttpRequest.newBuilder().uri(URI.create(toLoginUrl))
                .header("User-Agent", UA).GET().build();
        HttpResponse<String> bootResp = client.send(bootReq, HttpResponse.BodyHandlers.ofString());
        String bootLoc = bootResp.headers().firstValue("Location").orElse("");
        int bootStatus = bootResp.statusCode();
        log.info("CAS: bootstrap status={} location={}", bootStatus, bootLoc);

        String loginUrl;
        if (Set.of(301,302,303,307,308).contains(bootStatus) && !bootLoc.isEmpty()) {
            loginUrl = resolveUrl(toLoginUrl, bootLoc);
        } else {
            loginUrl = CAS_LOGIN_URL + "?service=" + URLEncoder.encode(SERVICE_URL, StandardCharsets.UTF_8);
        }
        log.info("CAS: 登录页URL={}", loginUrl);

        // Step 2: GET login page, extract lt/execution
        HttpRequest pageReq = HttpRequest.newBuilder().uri(URI.create(loginUrl))
                .header("User-Agent", UA).GET().build();
        HttpResponse<String> pageResp = client.send(pageReq, HttpResponse.BodyHandlers.ofString());
        if (pageResp.statusCode() != 200)
            throw new RuntimeException("CAS登录页获取失败: status=" + pageResp.statusCode());

        String html = pageResp.body();
        Map<String,String> formFields = extractFormInputs(html);
        String lt = formFields.get("lt"), execution = formFields.get("execution");
        if (lt == null || execution == null)
            throw new RuntimeException("CAS登录页缺少lt/execution, HTML前500: " + html.substring(0, Math.min(500, html.length())));

        String eventId = formFields.getOrDefault("_eventId", "submit");
        if (eventId.isEmpty()) eventId = "submit";
        log.info("CAS: lt={}... execution={}... eventId={}", lt.substring(0,Math.min(10,lt.length())),
                execution.substring(0,Math.min(10,execution.length())), eventId);

        // Step 3: DES加密（Node.js）
        String rsa = computeRsa(username + password + lt);
        log.info("CAS: rsa={}...", rsa.substring(0,Math.min(20,rsa.length())));

        // Step 4: POST login
        Map<String,String> payload = new LinkedHashMap<>();
        payload.put("rsa", rsa); payload.put("ul", String.valueOf(username.length()));
        payload.put("pl", String.valueOf(password.length())); payload.put("lt", lt);
        payload.put("execution", execution); payload.put("_eventId", eventId);
        for (Map.Entry<String,String> e : formFields.entrySet()) {
            String k = e.getKey();
            if (!payload.containsKey(k) && !Set.of("un","pd","username","password").contains(k) && !e.getValue().isEmpty())
                payload.put(k, e.getValue());
        }

        String body = payload.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));

        HttpRequest postReq = HttpRequest.newBuilder().uri(URI.create(loginUrl))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Origin", "https://newcas.gzhu.edu.cn")
                .header("Referer", loginUrl).header("User-Agent", UA)
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();

        HttpResponse<String> postResp = client.send(postReq, HttpResponse.BodyHandlers.ofString());
        log.info("CAS: POST status={} location={}", postResp.statusCode(),
                postResp.headers().firstValue("Location").orElse(""));

        // Step 5: Follow redirects, collect token
        List<String> tokenCandidates = new ArrayList<>();
        String finalBody = followAndCollect(client, postResp, loginUrl, tokenCandidates);
        if (tokenCandidates.isEmpty()) {
            // Try JSON body
            try {
                JsonNode n = mapper.readTree(finalBody);
                for (String k : Arrays.asList("token","uniToken","accessToken","access_token")) {
                    String v = n.path("data").path(k).asText();
                    if (!v.isEmpty()) tokenCandidates.add(0, v);
                }
            } catch (Exception ignored) {}
            Matcher m = Pattern.compile("\"(?:token|accessToken|access_token)\"\\s*:\\s*\"([^\"]+)\"").matcher(finalBody);
            while (m.find()) tokenCandidates.add(0, m.group(1));
        }
        tokenCandidates = new ArrayList<>(new LinkedHashSet<>(tokenCandidates));
        log.info("CAS: {} 个候选token", tokenCandidates.size());

        if (tokenCandidates.isEmpty()) throw new RuntimeException("CAS登录: 未能提取token");

        // Step 6: Validate userinfo
        JsonNode userData = validateUserinfo(client, tokenCandidates);
        if (userData == null) throw new RuntimeException("CAS登录: userInfo验证失败");
        return userData;
    }

    // ==================== 辅助 ====================

    private String discoverLoginUrl(HttpClient client) throws Exception {
        // 1. GET userinfo API
        HttpRequest uiReq = HttpRequest.newBuilder().uri(URI.create(USERINFO_API))
                .header("Accept", "application/json").header("lan","1").header("User-Agent",UA)
                .GET().timeout(Duration.ofSeconds(timeout)).build();
        client.send(uiReq, HttpResponse.BodyHandlers.discarding());

        // 2. GET auth/address API
        HttpRequest authReq = HttpRequest.newBuilder().uri(URI.create(AUTH_ADDRESS_API))
                .header("Accept", "application/json").header("lan","1").header("User-Agent",UA).GET().build();
        HttpResponse<String> authResp = client.send(authReq, HttpResponse.BodyHandlers.ofString());
        if (authResp.statusCode() == 200) {
            try {
                JsonNode node = mapper.readTree(authResp.body());
                String data = node.path("data").asText();
                if (data.startsWith("http")) return data;
            } catch (Exception ignored) {}
        }
        return AUTHCENTER_TO_LOGIN;
    }

    private Map<String,String> extractFormInputs(String html) {
        Map<String,String> vals = new LinkedHashMap<>();
        // Find <form id="loginForm"> or first <form>
        Pattern formP = Pattern.compile("<form[^>]*>", Pattern.CASE_INSENSITIVE);
        Matcher fm = formP.matcher(html);
        int formEnd = html.length();
        if (fm.find()) {
            // Find </form>
            int start = fm.start();
            int close = html.indexOf("</form>", start);
            formEnd = close > 0 ? close + 7 : html.length();
            html = html.substring(start, formEnd);
        }
        Pattern p = Pattern.compile("<input[^>]*name\\s*=\\s*\"([^\"]+)\"[^>]*(?:value\\s*=\\s*\"([^\"]*)\")?[^>]*>", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(html);
        while (m.find()) {
            String name = m.group(1);
            String value = m.group(2) != null ? m.group(2) : "";
            vals.put(name, value);
        }
        log.info("CAS: 提取{}个表单字段: {}", vals.size(), vals.keySet());
        return vals;
    }

    private String computeRsa(String plainText) throws Exception {
        if (cachedDesJs == null) {
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(DES_JS_URL))
                    .header("User-Agent", UA).GET().build();
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            cachedDesJs = resp.body();
            log.info("CAS: des.js加载完成 length={}", cachedDesJs.length());
        }

        Path script = Files.createTempFile("cas_des", ".js");
        String wrapper = cachedDesJs + "\n" +
                "const args = process.argv.slice(2);\n" +
                "if (typeof strEnc !== 'function') { console.error('des.js missing strEnc'); process.exit(2); }\n" +
                "process.stdout.write(String(strEnc(args[0], args[1], args[2], args[3])));";
        Files.writeString(script, wrapper);
        try {
            ProcessBuilder pb = new ProcessBuilder("node", script.toString(), plainText, "1", "2", "3");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int rc = p.waitFor();
            if (rc != 0) throw new RuntimeException("Node DES计算失败: rc=" + rc);
            output = output.trim();
            if (output.isEmpty() || !output.matches("[0-9A-Fa-f]+"))
                throw new RuntimeException("DES输出异常: " + output.substring(0, Math.min(80, output.length())));
            return output.toUpperCase();
        } finally {
            Files.deleteIfExists(script);
        }
    }

    private String followAndCollect(HttpClient client, HttpResponse<String> firstResp,
                                     String referer, List<String> candidates) throws Exception {
        HttpResponse<String> current = firstResp;
        String currentUrl = firstResp.uri().toString();
        String body = "";

        for (int hop = 0; hop < 12; hop++) {
            int status = current.statusCode();
            String location = current.headers().firstValue("Location").orElse("");

            // Collect from URL
            collectFromUrl(currentUrl, candidates);
            if (location != null && !location.isEmpty()) collectFromUrl(location, candidates);

            if (!Set.of(301,302,303,307,308).contains(status) || location.isEmpty()) {
                body = current.body();
                // Collect from response body JSON
                try {
                    String b = body;
                    if (b != null) {
                        for (String k : Arrays.asList("token","uniToken","accessToken","access_token")) {
                            String v = mapper.readTree(b).path("data").path(k).asText();
                            if (!v.isEmpty()) candidates.add(0, v);
                        }
                    }
                } catch (Exception ignored) {}
                // Collect from cookies
                for (String cookie : current.headers().allValues("Set-Cookie")) {
                    String c = cookie.split(";")[0];
                    if (c.toLowerCase().contains("token") || c.toLowerCase().contains("auth"))
                        candidates.add(c.split("=", 2)[1].trim());
                }
                break;
            }

            String nextUrl = resolveUrl(currentUrl, location);
            String method = Set.of(301,302,303).contains(status) ? "GET" : current.request().method();

            HttpRequest.Builder rb = HttpRequest.newBuilder().uri(URI.create(nextUrl))
                    .header("User-Agent", "Mozilla/5.0 HeadlessChrome/145.0 Safari/537.36")
                    .header("Origin", "https://newcas.gzhu.edu.cn").header("Referer", referer)
                    .header("Accept", "text/html,application/xhtml+xml,*/*")
                    .timeout(Duration.ofSeconds(timeout));
            if ("GET".equalsIgnoreCase(method)) rb.GET(); else rb.method(method, HttpRequest.BodyPublishers.noBody());

            current = client.send(rb.build(), HttpResponse.BodyHandlers.ofString());
            currentUrl = nextUrl;
        }
        return body;
    }

    private void collectFromUrl(String url, List<String> candidates) {
        for (String key : Arrays.asList("token","uniToken","access_token","accessToken","ticket")) {
            String val = extractParam(url, key);
            if (val != null && !val.isEmpty()) candidates.add(val);
        }
    }

    private JsonNode validateUserinfo(HttpClient client, List<String> tokens) throws Exception {
        for (String token : tokens) {
            try {
                HttpRequest req = HttpRequest.newBuilder().uri(URI.create(USERINFO_API))
                        .header("Accept", "application/json").header("lan","1")
                        .header("token", token).header("User-Agent", UA)
                        .GET().timeout(Duration.ofSeconds(timeout)).build();
                HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                JsonNode root = mapper.readTree(resp.body());
                if ("0".equals(root.path("code").asText())) {
                    log.info("CAS: token验证成功");
                    return root.path("data");
                }
            } catch (Exception e) { log.warn("CAS token验证异常: {}", e.getMessage()); }
        }
        return null;
    }

    private String resolveUrl(String base, String relative) {
        if (relative.startsWith("http")) return relative;
        try { return URI.create(base).resolve(relative).toString(); }
        catch (Exception e) { return base + (relative.startsWith("/") ? relative : "/" + relative); }
    }

    private String extractParam(String url, String key) {
        int qi = url.indexOf('?'); if (qi < 0) return null;
        for (String pair : url.substring(qi+1).split("&")) {
            String[] kv = pair.split("=",2);
            if (kv.length==2 && kv[0].equals(key)) {
                try { return java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8); }
                catch (Exception e) { return kv[1]; }
            }
        }
        return null;
    }
}
