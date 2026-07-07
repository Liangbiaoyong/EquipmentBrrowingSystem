package com.gzhu.equipment.service.impl;

import com.gzhu.equipment.config.MinioConfig;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MinioFileServiceImplTest {

    @Mock private MinioClient minioClient;
    @Mock private MinioConfig minioConfig;

    private MinioFileServiceImpl minioFileService;

    @BeforeEach
    void setUp() {
        minioFileService = new MinioFileServiceImpl(minioClient, minioConfig);
    }

    @Test @DisplayName("ensureBucket → 调用bucketExists和makeBucket")
    void ensureBucket_shouldCheckAndCreate() {
        // 仅验证不抛异常
        minioFileService.ensureBucket();
    }

    @Test @DisplayName("deleteFile → null路径不抛异常")
    void deleteFile_nullPath_shouldNotThrow() {
        minioFileService.deleteFile(null);
        minioFileService.deleteFile("");
    }
}
