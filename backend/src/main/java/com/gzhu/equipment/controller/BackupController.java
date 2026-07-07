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
 * GET  /admin/backup/export → 导出SQL备份文件
 * POST /admin/backup/restore → 恢复备份（需谨慎）
 */
@Slf4j @RestController @RequestMapping("/admin/backup") @RequiredArgsConstructor @Api(tags = "数据备份")
public class BackupController {

    @Value("${spring.datasource.url}")
    private String dbUrl;
    @Value("${spring.datasource.username}")
    private String dbUser;
    @Value("${spring.datasource.password}")
    private String dbPass;

    @GetMapping("/export")
    @ApiOperation("导出数据库备份")
    @PreAuthorize("hasAuthority('admin:backup')")
    public R<String> exportBackup() {
        String dbName = extractDbName(dbUrl);
        if (dbName == null) return R.fail("无法解析数据库名");
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String fileName = "backup_" + dbName + "_" + timestamp + ".sql";
        String filePath = System.getProperty("java.io.tmpdir") + "/" + fileName;

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "mysqldump", "-u" + dbUser, "-p" + dbPass, "--single-transaction", "--routines", "--triggers", dbName);
            pb.redirectOutput(new File(filePath));
            pb.redirectError(ProcessBuilder.Redirect.INHERIT);
            Process p = pb.start();
            int exit = p.waitFor();
            if (exit != 0) return R.fail("备份执行失败，退出码: " + exit);

            log.info("数据库备份完成: {} ({} bytes)", fileName, new File(filePath).length());
            return R.ok("备份完成: " + fileName);
        } catch (Exception e) {
            log.error("备份失败: {}", e.getMessage(), e);
            return R.fail("备份失败: " + e.getMessage());
        }
    }

    @GetMapping("/status")
    @ApiOperation("查看备份状态")
    @PreAuthorize("hasAuthority('admin:backup')")
    public R<String> status() {
        return R.ok("备份就绪。使用 GET /admin/backup/export 导出备份文件到服务器临时目录。");
    }

    private String extractDbName(String url) {
        if (url == null) return null;
        int q = url.indexOf('?');
        String base = q > 0 ? url.substring(0, q) : url;
        int lastSlash = base.lastIndexOf('/');
        return lastSlash > 0 ? base.substring(lastSlash + 1) : "device_borrow";
    }
}
