package com.gzhu.equipment.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gzhu.equipment.service.CasServerLoginService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * CAS 登录 — 调用已验证的外置 cas_login 程序
 *
 * 程序路径: TEMP/cas_login.java (编译后 classpath: TEMP/;TEMP/lib/*)
 * 依赖: Jackson, Jsoup, commons-cli (位于 TEMP/lib/)
 */
@Slf4j
@Service
public class CasServerLoginServiceImpl implements CasServerLoginService {

    @Value("${cas.cli-path:TEMP}") private String cliPath;
    @Value("${cas.cli-timeout:60}") private int cliTimeout;

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public JsonNode login(String username, String password) throws Exception {
        log.info("CAS外置程序登录: username={}", username);

        // 临时输出文件
        Path tmpFile = Files.createTempFile("cas_result_", ".json");
        try {
            // 构建命令
            String javaHome = System.getProperty("java.home");
            String javaBin = javaHome + File.separator + "bin" + File.separator + "java";
            String classpath = cliPath + File.separator + "*" + File.pathSeparator
                    + cliPath + File.separator + "lib" + File.pathSeparator + "*";
            String[] cmd = {
                javaBin, "-cp", classpath, "cas_login",
                "-u", username, "-p", password,
                "--output", tmpFile.toAbsolutePath().toString(),
                "--timeout", String.valueOf(cliTimeout)
            };

            log.info("CAS命令: {} -cp ... cas_login -u {} -p *** --output {}", javaBin, username, tmpFile);

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(new File(cliPath));
            pb.redirectErrorStream(true);
            Process p = pb.start();

            // 读取标准输出（调试用）
            String stdout = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            int exitCode = p.waitFor();

            log.info("CAS外置程序退出: exit={} stdout_len={}", exitCode, stdout.length());

            // 读取JSON输出
            if (!Files.exists(tmpFile) || Files.size(tmpFile) == 0) {
                String err = stdout.length() > 500 ? stdout.substring(0, 500) : stdout;
                throw new RuntimeException("CAS登录失败(exit=" + exitCode + "): " + err);
            }

            String json = Files.readString(tmpFile);
            JsonNode root = mapper.readTree(json);

            // 检查ok字段
            Boolean ok = root.path("ok").asBoolean(false);
            if (!ok) {
                String error = root.path("error").asText("未知错误");
                String htmlError = root.path("final_html_error").asText("");
                if (!htmlError.isEmpty()) error += " [" + htmlError + "]";
                throw new RuntimeException("CAS登录失败: " + error);
            }

            // 提取 userinfo.data 节点
            JsonNode userinfo = root.path("userinfo");
            JsonNode data = userinfo.path("data");
            if (data.isMissingNode() || data.isNull()) {
                // 兜底：尝试data.data
                data = root.path("data");
                if (data.isMissingNode()) {
                    String token = root.path("token").asText("");
                    if (!token.isEmpty()) {
                        // 有token但无userinfo，构造最小data节点
                        log.warn("CAS有token但缺少userinfo: token={}", token.substring(0, Math.min(20, token.length())));
                        return mapper.createObjectNode();
                    }
                    throw new RuntimeException("CAS返回的用户信息为空");
                }
            }

            log.info("CAS登录成功: data fields={}", data.fieldNames());
            return data;

        } finally {
            try { Files.deleteIfExists(tmpFile); } catch (Exception ignored) {}
        }
    }
}
