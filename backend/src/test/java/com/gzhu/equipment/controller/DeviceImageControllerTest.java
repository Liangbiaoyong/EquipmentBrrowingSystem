package com.gzhu.equipment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gzhu.equipment.entity.Device;
import com.gzhu.equipment.entity.DeviceImage;
import com.gzhu.equipment.mapper.DeviceImageMapper;
import com.gzhu.equipment.mapper.DeviceMapper;
import com.gzhu.equipment.security.JwtTokenProvider;
import com.gzhu.equipment.security.LoginRateLimiter;
import com.gzhu.equipment.security.TokenBlacklist;
import com.gzhu.equipment.service.MinioFileService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DeviceImageController REST 接口测试
 */
@WebMvcTest(DeviceImageController.class)
@AutoConfigureMockMvc(addFilters = false)
class DeviceImageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DeviceImageMapper imageMapper;

    @MockBean
    private DeviceMapper deviceMapper;

    @MockBean
    private MinioFileService minioFileService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private LoginRateLimiter loginRateLimiter;

    @MockBean
    private TokenBlacklist tokenBlacklist;

    @Test
    @DisplayName("GET /devices/1/images → 设备图片列表")
    void listImages_shouldReturnList() throws Exception {
        when(imageMapper.selectList(any())).thenReturn(List.of(new DeviceImage()));

        mockMvc.perform(get("/devices/1/images"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("POST /devices/1/images/upload → 上传设备图片")
    void uploadImage_shouldSucceed() throws Exception {
        Device device = new Device();
        device.setId(1L);
        when(deviceMapper.selectById(1L)).thenReturn(device);
        when(minioFileService.uploadImage(any(), anyString())).thenReturn("device-images/test.jpg");

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.jpg", "image/jpeg", "test-image-data".getBytes());

        mockMvc.perform(multipart("/devices/1/images/upload").file(file))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("POST /devices/1/images/upload（设备不存在）→ 404")
    void uploadImage_deviceNotFound_shouldReturn404() throws Exception {
        when(deviceMapper.selectById(999L)).thenReturn(null);

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.jpg", "image/jpeg", "data".getBytes());

        mockMvc.perform(multipart("/devices/999/images/upload").file(file))
                .andDo(print())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("POST /devices/1/images/upload（空文件）→ 400")
    void uploadImage_emptyFile_shouldReturn400() throws Exception {
        when(deviceMapper.selectById(1L)).thenReturn(new Device());

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.jpg", "image/jpeg", new byte[0]);

        mockMvc.perform(multipart("/devices/1/images/upload").file(file))
                .andDo(print())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("POST /devices/1/images → 添加图片URL")
    void addImageByUrl_shouldSucceed() throws Exception {
        Device device = new Device();
        device.setId(1L);
        when(deviceMapper.selectById(1L)).thenReturn(device);

        mockMvc.perform(post("/devices/1/images")
                        .param("imageUrl", "device-images/test.jpg"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("DELETE /devices/images/1 → 删除图片")
    void deleteImage_shouldSucceed() throws Exception {
        DeviceImage img = new DeviceImage();
        img.setId(1L);
        img.setDeviceId(1L);
        img.setImageUrl("device-images/test.jpg");
        when(imageMapper.selectById(1L)).thenReturn(img);
        when(deviceMapper.selectById(1L)).thenReturn(new Device());

        mockMvc.perform(delete("/devices/images/1"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("GET /devices/missing-images → 缺图设备列表")
    void listMissingImages_shouldReturnList() throws Exception {
        when(imageMapper.selectList(any())).thenReturn(List.of());
        Device device = new Device();
        device.setId(1L);
        when(deviceMapper.selectList(any())).thenReturn(List.of(device));

        mockMvc.perform(get("/devices/missing-images"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}
