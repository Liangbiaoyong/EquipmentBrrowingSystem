package com.gzhu.equipment.controller;

import com.gzhu.equipment.common.R;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 数据备份控制器
 *
 * GET  /admin/backup/export  → 导出SQL备份文件到服务器
 * GET  /admin/backup/status  → 查看备份状态
 */
@Slf4j @RestController @RequestMapping("/admin/backup") @RequiredArgsConstructor @Api(tags = "数据备份")
public class BackupController {

    @Value("${spring.datasource.url}")
    private String dbUrl;
    @Value("${spring.datasource.username}")
    private String dbUser;
    @Value("${spring.datasource.password}")
    private String dbPass;

    /** 备份导出目录（可通过环境变量 BACKUP_DIR 覆盖） */
    @Value("${backup.dir:#{systemProperties['java.io.tmpdir']}}")
    private String backupDir;

    @GetMapping("/export")
    @ApiOperation("导出数据库备份")
    @PreAuthorize("hasAuthority('admin:backup')")
    public R<String> exportBackup() {
        String dbName = extractDbName(dbUrl);
        if (dbName == null) return R.fail("无法解析数据库名");

        // 提取 MySQL 主机地址（Docker 环境下为容器名 dev-mysql）
        String dbHost = extractDbHost(dbUrl);
        int dbPort = extractDbPort(dbUrl);

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String fileName = "backup_" + dbName + "_" + timestamp + ".sql";

        // 确保备份目录存在
        File dir = new File(backupDir);
        if (!dir.exists()) dir.mkdirs();
        String filePath = backupDir + "/" + fileName;

        try {
            // 检测可用的 dump 工具：mysqldump > mariadb-dump > docker exec
            String dumpCmd = detectDumpTool();
            ProcessBuilder pb;
            if (dumpCmd.startsWith("docker")) {
                // docker exec dev-mysql mysqldump ...
                pb = buildDockerDumpCommand(dumpCmd, dbName, filePath);
            } else {
                // 直接调用 mysqldump / mariadb-dump
                pb = buildLocalDumpCommand(dumpCmd, dbName, dbHost, dbPort, filePath);
            }

            log.info("执行备份: {} -> {}", String.join(" ", pb.command().stream()
                    .filter(s -> !s.contains("-p")).toArray(String[]::new)), filePath);
            pb.redirectError(ProcessBuilder.Redirect.PIPE);
            Process p = pb.start();

            // 读取错误输出
            StringBuilder errOut = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) errOut.append(line).append("\n");
            }

            int exit = p.waitFor();
            if (exit != 0) {
                log.error("备份失败, exit={}, stderr={}", exit, errOut);
                return R.fail("备份执行失败，退出码: " + exit + "，错误: " + errOut);
            }

            long fileSize = new File(filePath).length();
            if (fileSize == 0) {
                new File(filePath).delete();
                return R.fail("备份文件为空，备份失败");
            }

            log.info("数据库备份完成: {} ({} bytes)", fileName, fileSize);
            return R.ok("备份完成: " + fileName + " (" + formatSize(fileSize) + ")");
        } catch (Exception e) {
            log.error("备份失败: {}", e.getMessage(), e);
            return R.fail("备份失败: " + e.getMessage());
        }
    }

    @GetMapping("/status")
    @ApiOperation("查看备份状态")
    @PreAuthorize("hasAuthority('admin:backup')")
    public R<String> status() {
        String tool = detectDumpTool();
        String toolInfo = tool.startsWith("docker") ? "Docker容器内 mysqldump" : tool;
        return R.ok("备份就绪。工具: " + toolInfo + "，目录: " + backupDir
                + "。使用 GET /admin/backup/export 导出备份文件。");
    }

    // ==================== 辅助方法 ====================

    /** 检测可用的备份工具 */
    private String detectDumpTool() {
        // 1. 尝试 mysqldump
        if (isCommandAvailable("mysqldump")) return "mysqldump";
        // 2. 尝试 mariadb-dump（Alpine 默认）
        if (isCommandAvailable("mariadb-dump")) return "mariadb-dump";
        // 3. 回退到 docker exec（容器环境）
        if (isCommandAvailable("docker")) return "docker";
        // 4. 返回 mysqldump（让它报错）
        return "mysqldump";
    }

    private boolean isCommandAvailable(String cmd) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd, "--version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private ProcessBuilder buildLocalDumpCommand(String dumpCmd, String dbName, String dbHost, int dbPort, String filePath) {
        ProcessBuilder pb = new ProcessBuilder(
                dumpCmd,
                "-h" + dbHost,
                "-P" + dbPort,
                "-u" + dbUser,
                "-p" + dbPass,
                "--single-transaction",
                "--routines",
                "--triggers",
                "--default-character-set=utf8mb4",
                dbName);
        pb.redirectOutput(new File(filePath));
        return pb;
    }

    private ProcessBuilder buildDockerDumpCommand(String dumpCmd, String dbName, String filePath) {
        // docker exec -i dev-mysql mysqldump -uroot -p${pass} ... > file
        ProcessBuilder pb = new ProcessBuilder(
                "sh", "-c",
                String.format("docker exec dev-mysql mysqldump -u%s -p%s --single-transaction --routines --triggers --default-character-set=utf8mb4 %s > %s",
                        shellEscape(dbUser), shellEscape(dbPass), shellEscape(dbName), shellEscape(filePath)));
        return pb;
    }

    private String shellEscape(String s) {
        if (s == null) return "";
        return s.replace("'", "'\\''");
    }

    private String extractDbName(String url) {
        if (url == null) return null;
        int q = url.indexOf('?');
        String base = q > 0 ? url.substring(0, q) : url;
        int lastSlash = base.lastIndexOf('/');
        return lastSlash > 0 ? base.substring(lastSlash + 1) : "device_borrow";
    }

    private String extractDbHost(String url) {
        if (url == null) return "mysql";
        // jdbc:mysql://host:port/db?params
        String host = "mysql";
        try {
            int start = url.indexOf("://") + 3;
            String sub = url.substring(start);
            int colon = sub.indexOf(':');
            int slash = sub.indexOf('/');
            int end = (colon > 0 && colon < slash) ? colon : slash;
            host = sub.substring(0, end > 0 ? end : sub.length());
        } catch (Exception ignored) {}
        return host;
    }

    private int extractDbPort(String url) {
        if (url == null) return 3306;
        try {
            int start = url.indexOf("://") + 3;
            String sub = url.substring(start);
            int colon = sub.indexOf(':');
            if (colon > 0) {
                int slash = sub.indexOf('/', colon);
                return Integer.parseInt(sub.substring(colon + 1, slash > 0 ? slash : sub.length()));
            }
        } catch (Exception ignored) {}
        return 3306;
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
