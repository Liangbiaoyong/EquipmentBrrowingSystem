package com.gzhu.equipment.controller;

import com.gzhu.equipment.common.R;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j @RestController @RequestMapping("/admin/backup")
@Api(tags = "数据备份")
public class BackupController {

    @Value("${spring.datasource.url}") private String dbUrl;
    @Value("${spring.datasource.username}") private String dbUser;
    @Value("${spring.datasource.password}") private String dbPass;
    @Value("${backup.dir:#{systemProperties['java.io.tmpdir']}}") private String backupDir;

    // ==================== 导出备份 ====================

    @GetMapping("/export")
    @ApiOperation("导出数据库备份")
    @PreAuthorize("hasAuthority('admin:backup')")
    public R<Map<String,Object>> exportBackup() {
        String dbName = extractDbName(dbUrl);
        if (dbName == null) return R.fail("无法解析数据库名");
        String dbHost = extractDbHost(dbUrl);
        int dbPort = extractDbPort(dbUrl);
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String fileName = "backup_" + dbName + "_" + timestamp + ".sql";
        File dir = new File(backupDir);
        if (!dir.exists()) dir.mkdirs();
        String filePath = backupDir + File.separator + fileName;

        try {
            // 优先使用 docker exec 在 MySQL 容器内执行 mysqldump（规避 auth 插件问题）
            ProcessBuilder pb;
            if (isCmdOk("docker")) {
                pb = buildDockerDumpCommand(dbName, filePath);
            } else {
                String cmd = isCmdOk("mariadb-dump") ? "mariadb-dump" : "mysqldump";
                pb = buildLocalDumpCommand(cmd, dbName, dbHost, dbPort, filePath);
            }

            log.info("备份命令: {}", String.join(" ", pb.command()).replaceAll("-p\\S+", "-p***"));
            pb.redirectError(ProcessBuilder.Redirect.PIPE);
            Process p = pb.start();

            StringBuilder errOut = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getErrorStream()))) {
                String line; while ((line = reader.readLine()) != null) errOut.append(line).append("\n");
            }
            int exit = p.waitFor();

            long fileSize = new File(filePath).length();
            // 退出码非0但文件已生成且stderr仅为deprecation→视为成功
            if (exit != 0) {
                boolean hasData = fileSize > 1024;
                boolean deprecationOnly = errOut.toString().contains("Deprecated") || errOut.toString().contains("mariadb-dump");
                if (hasData && deprecationOnly) {
                    log.warn("备份有警告: exit={}", exit);
                } else {
                    log.error("备份失败 exit={} stderr={}", exit, errOut);
                    if (hasData) new File(filePath).delete();
                    return R.fail("备份失败, 退出码: " + exit + ", 错误: " + errOut.toString().trim());
                }
            }
            if (fileSize == 0) { new File(filePath).delete(); return R.fail("备份文件为空"); }

            Map<String,Object> result = new LinkedHashMap<>();
            result.put("fileName", fileName);
            result.put("fileSize", formatSize(fileSize));
            result.put("filePath", filePath);
            log.info("备份完成: {} ({})", fileName, formatSize(fileSize));
            return R.ok(result);
        } catch (Exception e) {
            log.error("备份失败: {}", e.getMessage(), e);
            return R.fail("备份失败: " + e.getMessage());
        }
    }

    // ==================== 备份文件管理 ====================

    @GetMapping("/list")
    @ApiOperation("列出服务器上的备份文件")
    @PreAuthorize("hasAuthority('admin:backup')")
    public R<List<Map<String,Object>>> listBackups() {
        File dir = new File(backupDir);
        File[] files = dir.listFiles(f -> f.isFile() && f.getName().startsWith("backup_") && (f.getName().endsWith(".sql") || f.getName().endsWith(".gz")));
        if (files == null) return R.ok(Collections.emptyList());

        List<Map<String,Object>> list = Arrays.stream(files)
                .sorted((a,b) -> Long.compare(b.lastModified(), a.lastModified()))
                .map(f -> {
                    Map<String,Object> m = new LinkedHashMap<>();
                    m.put("fileName", f.getName());
                    m.put("fileSize", formatSize(f.length()));
                    m.put("fileSizeBytes", f.length());
                    m.put("lastModified", new java.util.Date(f.lastModified()).toString());
                    return m;
                }).collect(Collectors.toList());
        return R.ok(list);
    }

    @GetMapping("/download/{fileName}")
    @ApiOperation("下载备份文件")
    @PreAuthorize("hasAuthority('admin:backup')")
    public ResponseEntity<Resource> downloadBackup(@PathVariable String fileName) {
        // 防路径穿越
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\"))
            return ResponseEntity.badRequest().build();

        File file = new File(backupDir, fileName);
        if (!file.exists()) return ResponseEntity.notFound().build();

        Resource resource = new FileSystemResource(file);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/octet-stream"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentLength(file.length())
                .body(resource);
    }

    @DeleteMapping("/{fileName}")
    @ApiOperation("删除备份文件")
    @PreAuthorize("hasAuthority('admin:backup')")
    public R<String> deleteBackup(@PathVariable String fileName) {
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\"))
            return R.fail("非法文件名");
        File file = new File(backupDir, fileName);
        if (!file.exists()) return R.fail(404, "文件不存在");
        if (file.delete()) {
            log.warn("备份文件已删除: {}", fileName);
            return R.ok("已删除: " + fileName);
        }
        return R.fail("删除失败");
    }

    // ==================== 恢复备份 ====================

    @PostMapping("/restore/{fileName}")
    @ApiOperation("从备份文件恢复数据库")
    @PreAuthorize("hasAuthority('admin:backup')")
    public R<String> restoreBackup(@PathVariable String fileName) {
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\"))
            return R.fail("非法文件名");
        File file = new File(backupDir, fileName);
        if (!file.exists()) return R.fail(404, "备份文件不存在");
        if (file.length() == 0) return R.fail("备份文件为空");

        String dbName = extractDbName(dbUrl);
        String dbHost = extractDbHost(dbUrl);
        int dbPort = extractDbPort(dbUrl);

        try {
            String mysqlCmd = detectMysqlCmd();
            ProcessBuilder pb;
            if (mysqlCmd.startsWith("docker")) {
                pb = buildDockerRestoreCommand(file.getAbsolutePath(), dbName);
            } else {
                pb = buildLocalRestoreCommand(mysqlCmd, dbName, dbHost, dbPort, file.getAbsolutePath());
            }

            log.warn("恢复数据库: {} ← {}", dbName, fileName);
            pb.redirectError(ProcessBuilder.Redirect.PIPE);
            Process p = pb.start();

            StringBuilder errOut = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getErrorStream()))) {
                String line; while ((line = reader.readLine()) != null) errOut.append(line).append("\n");
            }
            int exit = p.waitFor();
            if (exit != 0) {
                log.error("恢复失败 exit={} stderr={}", exit, errOut);
                return R.fail("恢复失败, 退出码: " + exit + ", 错误: " + errOut.toString().trim());
            }
            return R.ok("数据库恢复成功: " + fileName);
        } catch (Exception e) {
            log.error("恢复失败: {}", e.getMessage(), e);
            return R.fail("恢复失败: " + e.getMessage());
        }
    }

    @PostMapping("/import")
    @ApiOperation("上传SQL文件恢复数据库")
    @PreAuthorize("hasAuthority('admin:backup')")
    public R<String> importAndRestore(@RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        if (file.isEmpty()) return R.fail("请选择文件");
        String origName = file.getOriginalFilename();
        if (origName == null || (!origName.endsWith(".sql") && !origName.endsWith(".gz")))
            return R.fail("仅支持 .sql / .gz 文件");

        // 保存上传文件
        String tempName = "upload_restore_" + System.currentTimeMillis() + ".sql";
        File tempFile = new File(backupDir, tempName);
        try {
            file.transferTo(tempFile);
        } catch (IOException e) {
            return R.fail("文件保存失败: " + e.getMessage());
        }

        // 执行恢复
        String dbName = extractDbName(dbUrl);
        String dbHost = extractDbHost(dbUrl);
        int dbPort = extractDbPort(dbUrl);

        try {
            String mysqlCmd = detectMysqlCmd();
            ProcessBuilder pb;
            if (mysqlCmd.startsWith("docker")) {
                pb = buildDockerRestoreCommand(tempFile.getAbsolutePath(), dbName);
            } else {
                pb = buildLocalRestoreCommand(mysqlCmd, dbName, dbHost, dbPort, tempFile.getAbsolutePath());
            }

            log.warn("导入恢复数据库: {} ← {}", dbName, origName);
            pb.redirectError(ProcessBuilder.Redirect.PIPE);
            Process p = pb.start();

            StringBuilder errOut = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getErrorStream()))) {
                String line; while ((line = reader.readLine()) != null) errOut.append(line).append("\n");
            }
            int exit = p.waitFor();
            tempFile.delete(); // 清理临时文件
            if (exit != 0) {
                log.error("导入恢复失败 exit={} stderr={}", exit, errOut);
                return R.fail("恢复失败, 退出码: " + exit + ", 错误: " + errOut.toString().trim());
            }
            return R.ok("导入恢复成功: " + origName);
        } catch (Exception e) {
            tempFile.delete();
            log.error("导入恢复失败: {}", e.getMessage(), e);
            return R.fail("恢复失败: " + e.getMessage());
        }
    }

    @GetMapping("/status")
    @ApiOperation("查看备份状态")
    @PreAuthorize("hasAuthority('admin:backup')")
    public R<Map<String,Object>> status() {
        File dir = new File(backupDir);
        File[] files = dir.listFiles(f -> f.isFile() && f.getName().startsWith("backup_") && f.getName().endsWith(".sql"));
        long totalSize = 0;
        if (files != null) for (File f : files) totalSize += f.length();

        Map<String,Object> s = new LinkedHashMap<>();
        s.put("tool", detectDumpTool());
        s.put("backupDir", backupDir);
        s.put("dbName", extractDbName(dbUrl));
        s.put("fileCount", files == null ? 0 : files.length);
        s.put("totalSize", formatSize(totalSize));
        return R.ok(s);
    }

    // ==================== 辅助 ====================

    private String detectDumpTool() {
        if (isCmdOk("mysqldump")) return "mysqldump";
        if (isCmdOk("mariadb-dump")) return "mariadb-dump";
        if (isCmdOk("docker")) return "docker";
        return "mysqldump";
    }

    private String detectMysqlCmd() {
        if (isCmdOk("mysql")) return "mysql";
        if (isCmdOk("mariadb")) return "mariadb";
        if (isCmdOk("docker")) return "docker";
        return "mysql";
    }

    private boolean isCmdOk(String cmd) {
        try {
            Process p = new ProcessBuilder(cmd, "--version").redirectErrorStream(true).start();
            return p.waitFor() == 0;
        } catch (Exception e) { return false; }
    }

    private ProcessBuilder buildLocalDumpCommand(String dumpCmd, String dbName, String dbHost, int dbPort, String filePath) {
        ProcessBuilder pb = new ProcessBuilder(
                dumpCmd, "-h" + dbHost, "-P" + dbPort, "-u" + dbUser, "-p" + dbPass,
                "--default-auth=mysql_native_password",
                "--single-transaction", "--routines", "--triggers",
                "--default-character-set=utf8mb4",
                "--ssl=0", dbName);
        pb.redirectOutput(new File(filePath));
        return pb;
    }

    private ProcessBuilder buildDockerDumpCommand(String dbName, String filePath) {
        return new ProcessBuilder("sh", "-c",
                String.format("docker exec dev-mysql mysqldump -u%s -p%s --default-auth=mysql_native_password --single-transaction --routines --triggers --default-character-set=utf8mb4 %s 2>/dev/null > %s",
                        shellEscape(dbUser), shellEscape(dbPass), shellEscape(dbName), shellEscape(filePath)));
    }

    private ProcessBuilder buildLocalRestoreCommand(String mysqlCmd, String dbName, String dbHost, int dbPort, String filePath) {
        return new ProcessBuilder(mysqlCmd, "-h" + dbHost, "-P" + dbPort, "-u" + dbUser, "-p" + dbPass, "--default-auth=mysql_native_password", "--ssl=0", dbName)
                .redirectInput(new File(filePath));
    }

    private ProcessBuilder buildDockerRestoreCommand(String filePath, String dbName) {
        return new ProcessBuilder("sh", "-c",
                String.format("docker exec -i dev-mysql mysql -u%s -p%s --default-auth=mysql_native_password %s < %s",
                        shellEscape(dbUser), shellEscape(dbPass), shellEscape(dbName), shellEscape(filePath)));
    }

    private String shellEscape(String s) {
        if (s == null) return ""; return s.replace("'", "'\\''");
    }

    private String extractDbName(String url) {
        if (url == null) return "device_borrow";
        int q = url.indexOf('?'); String base = q > 0 ? url.substring(0, q) : url;
        int lastSlash = base.lastIndexOf('/');
        return lastSlash > 0 ? base.substring(lastSlash + 1) : "device_borrow";
    }

    private String extractDbHost(String url) {
        if (url == null) return "mysql";
        try {
            int start = url.indexOf("://") + 3; String sub = url.substring(start);
            int end = sub.indexOf(':'); if (end < 0) end = sub.indexOf('/');
            return end > 0 ? sub.substring(0, end) : sub;
        } catch (Exception e) { return "mysql"; }
    }

    private int extractDbPort(String url) {
        if (url == null) return 3306;
        try {
            int start = url.indexOf("://") + 3; String sub = url.substring(start);
            int colon = sub.indexOf(':'); if (colon < 0) return 3306;
            int slash = sub.indexOf('/', colon);
            return Integer.parseInt(slash > 0 ? sub.substring(colon + 1, slash) : sub.substring(colon + 1));
        } catch (Exception e) { return 3306; }
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
