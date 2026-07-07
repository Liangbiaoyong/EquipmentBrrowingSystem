package com.gzhu.equipment.service;

import org.springframework.web.multipart.MultipartFile;
import java.io.InputStream;

public interface MinioFileService {

    /** 上传图片（自动压缩），返回 MinIO 中的 URL */
    String uploadImage(MultipartFile file, String bizType);

    /** 上传图片并指定文件名 */
    String uploadImage(MultipartFile file, String bizType, String customName);

    /** 从 MinIO 删除文件 */
    void deleteFile(String objectUrl);

    /** 确保 Bucket 存在 */
    void ensureBucket();
}
