package com.gzhu.equipment.controller;

import com.gzhu.equipment.common.R;
import com.gzhu.equipment.dto.ImportResultDTO;
import com.gzhu.equipment.security.JwtUserPrincipal;
import com.gzhu.equipment.service.DeviceImportService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 设备批量导入控制器
 *
 * POST /devices/import → 上传 CSV 或 XLSX 文件批量导入设备
 */
@Slf4j
@RestController
@RequestMapping("/devices")
@RequiredArgsConstructor
@Api(tags = "设备管理")
public class DeviceImportController {

    private final DeviceImportService deviceImportService;

    /**
     * 批量导入设备资产
     *
     * 支持格式：.csv（UTF-8编码）、.xlsx
     * 自动识别国标分类名 → 匹配业务分类
     * 资产编号已存在则更新，不存在则新增
     */
    @PostMapping("/import")
    @ApiOperation("批量导入设备资产（CSV/XLSX）")
    @PreAuthorize("hasAnyRole('LAB_ADMIN', 'SYSTEM_ADMIN')")
    public R<ImportResultDTO> importDevices(@RequestParam("file") MultipartFile file) {
        // 参数校验
        if (file.isEmpty()) {
            return R.fail(400, "请选择文件");
        }

        String fileName = file.getOriginalFilename();
        if (fileName == null ||
                !(fileName.toLowerCase().endsWith(".csv") || fileName.toLowerCase().endsWith(".xlsx"))) {
            return R.fail(400, "仅支持 .csv 或 .xlsx 格式文件");
        }

        // 获取当前用户ID
        Long userId = getCurrentUserId();

        try {
            ImportResultDTO result = deviceImportService.importFromStream(
                    file.getInputStream(), fileName, userId);

            if (result.getFailCount() > 0) {
                return R.ok("导入完成（含错误）", result);
            }
            return R.ok("导入成功", result);

        } catch (IOException e) {
            log.error("文件读取失败: {}", e.getMessage(), e);
            return R.fail("文件读取失败: " + e.getMessage());
        }
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof JwtUserPrincipal) {
            return ((JwtUserPrincipal) auth.getPrincipal()).getUserId();
        }
        return 1L; // fallback for system-level operations
    }
}
