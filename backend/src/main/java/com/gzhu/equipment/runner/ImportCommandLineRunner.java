package com.gzhu.equipment.runner;

import com.gzhu.equipment.dto.ImportResultDTO;
import com.gzhu.equipment.service.DeviceImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 命令行导入模式 — 无需启动 HTTP 服务，直接导入文件后退出
 *
 * 激活方式（两种选一）：
 *
 * 方式1 — Maven（开发环境）：
 *   mvn spring-boot:run \
 *     -Dspring-boot.run.profiles=dev,import \
 *     -Dspring-boot.run.arguments="--import.file=../project_description/20260707-在账资产查询.csv"
 *
 * 方式2 — JAR（部署环境）：
 *   java -jar equipment-borrow.jar \
 *     --spring.profiles.active=dev,import \
 *     --import.file=/data/assets/20260707-在账资产查询.csv
 *
 * 方式3 — 环境变量：
 *   export IMPORT_FILE=/data/assets/20260707-在账资产查询.csv
 *   mvn spring-boot:run -Dspring-boot.run.profiles=dev,import
 *
 * 方式4 — XLSX格式：
 *   java -jar equipment-borrow.jar \
 *     --spring.profiles.active=dev,import \
 *     --import.file=/data/assets/20260707-在账资产查询.xlsx
 *
 * 注意：
 * - 需要有效的数据库连接（application-dev.yml 或 application-prod.yml）
 * - 导入完成后 Spring 上下文自动关闭（exit code 0 = 成功，1 = 失败）
 */
@Slf4j
@Component
@Profile("import")
@RequiredArgsConstructor
public class ImportCommandLineRunner implements CommandLineRunner {

    private final DeviceImportService deviceImportService;

    @Override
    public void run(String... args) throws Exception {
        // 确定文件路径：命令行参数 > 环境变量 > 默认路径
        String filePath = null;

        for (String arg : args) {
            if (arg.startsWith("--import.file=")) {
                filePath = arg.substring("--import.file=".length());
                break;
            }
        }
        if (filePath == null) {
            filePath = System.getenv("IMPORT_FILE");
        }
        if (filePath == null) {
            // 默认路径：项目根目录下的 project_description 文件夹
            filePath = "../project_description/20260707-在账资产查询.csv";
            log.info("未指定 --import.file，使用默认路径: {}", filePath);
        }

        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            log.error("文件不存在: {}", path.toAbsolutePath());
            System.exit(1);
            return;
        }

        String fileName = path.getFileName().toString();
        log.info("============================================");
        log.info("  命令行批量导入模式");
        log.info("  文件: {} ({} KB)", path.toAbsolutePath(), Files.size(path) / 1024);
        log.info("============================================");

        long start = System.currentTimeMillis();
        ImportResultDTO result;

        try (InputStream is = new FileInputStream(path.toFile())) {
            result = deviceImportService.importFromStream(is, fileName, 1L);
        }

        long duration = (System.currentTimeMillis() - start) / 1000;

        log.info("");
        log.info("============================================");
        log.info("  导入完毕（耗时 {}s）", duration);
        log.info("  ─────────────────────");
        log.info("  总行数:     {}", result.getTotalRows());
        log.info("  新增:       {}", result.getSuccessCount());
        log.info("  更新:       {}", result.getUpdateCount());
        log.info("  删除:       {}", result.getDeleteCount());
        log.info("  失败:       {}", result.getFailCount());
        log.info("  自动分类:   {}", result.getAutoCategoryCount());
        log.info("  未分类:     {}", result.getUncategorizedCount());
        log.info("  批次号:     {}", result.getBatchId());
        log.info("============================================");

        if (result.getFailCount() > 0) {
            log.warn("存在 {} 条导入错误:", result.getErrors().size());
            for (ImportResultDTO.ImportError e : result.getErrors()) {
                log.warn("  行{}: [{}] {} — {}",
                        e.getRow(), e.getAssetNo(), e.getName(), e.getReason());
            }
        }

        // 有错误 → exit 1；全部成功 → exit 0
        int exitCode = result.getFailCount() > 0 ? 1 : 0;
        log.info("退出码: {}", exitCode);

        // Spring Boot 应用会在 CommandLineRunner 完成后自动关闭（非 web profile）
        // 如果同时激活了 web profile，需要显式退出
        System.exit(exitCode);
    }
}
