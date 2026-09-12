package com.gzhu.equipment.controller;

import com.gzhu.equipment.config.MinioConfig;
import com.gzhu.equipment.security.JwtTokenProvider;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.io.InputStream;

/**
 * MinIO 文件代理 — 前端通过此端点访问存储在 MinIO 中的图片
 *
 * GET /api/v1/files/** → 代理到 MinIO 读取文件
 *
 * 开发环境：后端代理（此Controller）
 * 生产环境：Nginx 直连 MinIO（见 nginx.conf 中的 /minio/ location）
 *
 * 前端统一使用 /api/v1/files/{objectPath} 访问图片
 * vite.config.js 中 /api 已代理到 localhost:8080，开发环境自动走此后端代理
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Api(tags = "文件访问")
public class FileProxyController {

    private final MinioClient minioClient;
    private final MinioConfig minioConfig;
    private final JwtTokenProvider jwtTokenProvider;

    @GetMapping("/files/**")
    @ApiOperation("访问 MinIO 文件（图片等）")
    public ResponseEntity<byte[]> getFile(HttpServletRequest request) {
        // 提取 /files/ 之后的路径 → MinIO object path
        String fullPath = request.getRequestURI();
        String objectPath = fullPath.substring(fullPath.indexOf("/files/") + 7);

        if (objectPath.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        // 设备图片对外公开；借用/归还照片等需要有效 JWT
        if (!objectPath.startsWith("device-images/")) {
            String token = resolveToken(request);
            if (token == null || !jwtTokenProvider.validateToken(token)) {
                return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED).build();
            }
        }

        try (InputStream stream = minioClient.getObject(GetObjectArgs.builder()
                .bucket(minioConfig.getBucket())
                .object(objectPath)
                .build())) {

            byte[] bytes = stream.readAllBytes();

            // 根据扩展名设置 Content-Type
            String contentType = "image/jpeg";
            String lower = objectPath.toLowerCase();
            if (lower.endsWith(".png")) contentType = "image/png";
            else if (lower.endsWith(".gif")) contentType = "image/gif";
            else if (lower.endsWith(".webp")) contentType = "image/webp";
            else if (lower.endsWith(".svg")) contentType = "image/svg+xml";
            else if (lower.endsWith(".pdf")) contentType = "application/pdf";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(contentType));
            // 缓存策略：设备图片永久缓存，借用图片缓存1天
            if (objectPath.startsWith("device-images/")) {
                headers.setCacheControl("public, max-age=31536000, immutable");
            } else {
                headers.setCacheControl("private, max-age=86400");
            }

            return ResponseEntity.ok().headers(headers).body(bytes);
        } catch (Exception e) {
            log.warn("MinIO文件读取失败: path={} msg={}", objectPath, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    /** 优先取 Authorization 头，其次取 ?token= 查询参数（<img> 无法设置请求头） */
    private String resolveToken(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) return auth.substring(7);
        String q = request.getParameter("token");
        return (q != null && !q.isEmpty()) ? q : null;
    }
}
