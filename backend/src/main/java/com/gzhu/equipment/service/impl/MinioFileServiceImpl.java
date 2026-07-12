package com.gzhu.equipment.service.impl;

import com.gzhu.equipment.config.MinioConfig;
import com.gzhu.equipment.service.MinioFileService;
import io.minio.*;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * MinIO 文件服务 — 上传压缩 + 删除
 *
 * 存储路径规则：
 * - 设备图片：device-images/{uuid}.jpg（永久）
 * - 借用归还图片：borrow-images/{yyyy-MM}/{uuid}.jpg（保留30天，定时清理）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MinioFileServiceImpl implements MinioFileService {

    private final MinioClient minioClient;
    private final MinioConfig minioConfig;

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    @Override
    public String uploadImage(MultipartFile file, String bizType) {
        String ext = getExtension(file.getOriginalFilename());
        String name = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        return uploadImage(file, bizType, name);
    }

    @Override
    public String uploadImage(MultipartFile file, String bizType, String customName) {
        try {
            ensureBucket();

            // MIME 校验
            String contentType = file.getContentType();
            if (contentType == null || (!contentType.startsWith("image/"))) {
                throw new IllegalArgumentException("仅支持图片格式（png/jpg/jpeg）");
            }

            // Thumbnailator 压缩
            byte[] compressed = compressImage(file);

            // 构建存储路径
            String objectPath;
            if ("DEVICE".equals(bizType)) {
                objectPath = "device-images/" + customName;
            } else {
                String month = LocalDate.now().format(MONTH_FMT);
                objectPath = "borrow-images/" + month + "/" + customName;
            }

            // 上传到 MinIO
            try {
                ByteArrayInputStream is = new ByteArrayInputStream(compressed);
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(minioConfig.getBucket())
                        .object(objectPath)
                        .stream(is, compressed.length, -1)
                        .contentType("image/jpeg")
                        .build());
            } catch (Exception e) {
                throw new RuntimeException("MinIO上传请求失败: " + e.getMessage(), e);
            }

            log.info("图片上传成功: path={} size={}KB", objectPath, compressed.length / 1024);
            return objectPath;

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("图片上传失败: {}", e.getMessage(), e);
            throw new RuntimeException("图片上传失败: " + e.getMessage());
        }
    }

    @Override
    public void deleteFile(String objectUrl) {
        if (objectUrl == null || objectUrl.isEmpty()) return;
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(minioConfig.getBucket())
                    .object(objectUrl)
                    .build());
            log.info("MinIO文件已删除: {}", objectUrl);
        } catch (Exception e) {
            log.warn("MinIO文件删除失败: {} → {}", objectUrl, e.getMessage());
        }
    }

    @Override
    public void ensureBucket() {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder()
                    .bucket(minioConfig.getBucket()).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder()
                        .bucket(minioConfig.getBucket()).build());
                log.info("MinIO Bucket 已创建: {}", minioConfig.getBucket());
            }
        } catch (Exception e) {
            log.error("MinIO Bucket 初始化失败: {}", e.getMessage(), e);
        }
    }

    // ==================== 压缩 ====================

    private byte[] compressImage(MultipartFile file) throws Exception {
        BufferedImage original = ImageIO.read(file.getInputStream());
        if (original == null) {
            throw new IllegalArgumentException("无法解析图片文件");
        }

        int width = original.getWidth();
        int height = original.getHeight();
        int maxWidth = minioConfig.getImageMaxWidth();
        double quality = minioConfig.getImageQuality();
        long maxSize = minioConfig.getImageMaxSize();

        // 限制最大宽度
        int targetWidth = Math.min(width, maxWidth);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        // 渐进式压缩：从默认质量开始，不够就降低质量直到 1MB 以下
        double[] qualities = {quality, 0.5, 0.35, 0.25};
        for (double q : qualities) {
            bos.reset();
            Thumbnails.of(original)
                    .width(targetWidth)
                    .outputFormat("jpg")
                    .outputQuality(q)
                    .toOutputStream(bos);
            if (bos.size() <= maxSize) break;

            // 质量已经很低了，进一步缩小尺寸
            if (q == qualities[qualities.length - 1] && bos.size() > maxSize) {
                bos.reset();
                Thumbnails.of(original)
                        .width(Math.min(targetWidth, 1280))
                        .outputFormat("jpg")
                        .outputQuality(0.3)
                        .toOutputStream(bos);
            }
        }

        log.debug("图片压缩完成: {}x{} → {}KB", width, height, bos.size() / 1024);
        return bos.toByteArray();
    }

    private String getExtension(String filename) {
        if (filename == null) return "jpg";
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(dot + 1).toLowerCase() : "jpg";
    }
}
