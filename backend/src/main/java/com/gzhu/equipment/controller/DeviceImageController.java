package com.gzhu.equipment.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gzhu.equipment.common.R;
import com.gzhu.equipment.entity.Device;
import com.gzhu.equipment.entity.DeviceImage;
import com.gzhu.equipment.mapper.DeviceImageMapper;
import com.gzhu.equipment.mapper.DeviceMapper;
import com.gzhu.equipment.service.MinioFileService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/devices")
@RequiredArgsConstructor
@Api(tags = "设备图片管理")
public class DeviceImageController {

    private final DeviceImageMapper imageMapper;
    private final DeviceMapper deviceMapper;
    private final MinioFileService minioFileService;

    @GetMapping("/{deviceId}/images")
    @ApiOperation("获取设备图片列表")
    public R<List<DeviceImage>> listImages(@PathVariable Long deviceId) {
        return R.ok(imageMapper.selectList(
                new LambdaQueryWrapper<DeviceImage>()
                        .eq(DeviceImage::getDeviceId, deviceId)
                        .orderByAsc(DeviceImage::getSort)));
    }

    @PostMapping("/{deviceId}/images/upload")
    @ApiOperation("上传设备图片（MultipartFile，自动压缩）")
    @PreAuthorize("hasAuthority('device:manage')")
    public R<DeviceImage> uploadImage(@PathVariable Long deviceId,
                                       @RequestParam("file") MultipartFile file,
                                       @RequestParam(defaultValue = "0") int sort) {
        Device device = deviceMapper.selectById(deviceId);
        if (device == null) return R.fail(404, "设备不存在");
        if (file.isEmpty()) return R.fail(400, "请选择图片文件");

        try {
            // MinIO 上传（自动压缩）
            String objectPath = minioFileService.uploadImage(file, "DEVICE");

            // 保存设备图片记录
            DeviceImage img = new DeviceImage();
            img.setDeviceId(deviceId);
            img.setImageUrl(objectPath);
            img.setSort(sort);
            imageMapper.insert(img);

            // 无封面时自动设为封面
            if (device.getCoverImage() == null || device.getCoverImage().isEmpty()) {
                device.setCoverImage(objectPath);
                deviceMapper.updateById(device);
            }

            return R.ok(img);
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        } catch (Exception e) {
            return R.fail("图片上传失败: " + e.getMessage());
        }
    }

    @PostMapping("/{deviceId}/images")
    @ApiOperation("添加设备图片（URL方式，兼容旧接口）")
    @PreAuthorize("hasAuthority('device:manage')")
    public R<DeviceImage> addImageByUrl(@PathVariable Long deviceId,
                                         @RequestParam String imageUrl,
                                         @RequestParam(defaultValue = "0") int sort) {
        Device device = deviceMapper.selectById(deviceId);
        if (device == null) return R.fail(404, "设备不存在");

        DeviceImage img = new DeviceImage();
        img.setDeviceId(deviceId);
        img.setImageUrl(imageUrl);
        img.setSort(sort);
        imageMapper.insert(img);

        if (device.getCoverImage() == null || device.getCoverImage().isEmpty()) {
            device.setCoverImage(imageUrl);
            deviceMapper.updateById(device);
        }
        return R.ok(img);
    }

    @DeleteMapping("/images/{id}")
    @ApiOperation("删除设备图片（含MinIO文件）")
    @PreAuthorize("hasAuthority('device:manage')")
    public R<Void> deleteImage(@PathVariable Long id) {
        DeviceImage img = imageMapper.selectById(id);
        if (img != null) {
            minioFileService.deleteFile(img.getImageUrl());
            imageMapper.deleteById(id);

            // 如果删除的是封面图，用下一张替代
            Device device = deviceMapper.selectById(img.getDeviceId());
            if (device != null && img.getImageUrl().equals(device.getCoverImage())) {
                DeviceImage next = imageMapper.selectOne(
                        new LambdaQueryWrapper<DeviceImage>()
                                .eq(DeviceImage::getDeviceId, img.getDeviceId())
                                .orderByAsc(DeviceImage::getSort)
                                .last("LIMIT 1"));
                device.setCoverImage(next != null ? next.getImageUrl() : null);
                deviceMapper.updateById(device);
            }
        }
        return R.ok();
    }

    @GetMapping("/missing-images")
    @ApiOperation("查询缺少图片的设备")
    @PreAuthorize("hasAuthority('device:manage')")
    public R<List<Device>> listMissingImages(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size) {
        List<Long> idsWithImages = imageMapper.selectList(null).stream()
                .map(DeviceImage::getDeviceId).distinct().collect(Collectors.toList());

        LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Device::getDeviceStatus, 1); // 仅设备正常状态的设备
        if (!idsWithImages.isEmpty()) wrapper.notIn(Device::getId, idsWithImages);
        wrapper.last("LIMIT " + ((page - 1) * size) + "," + size);
        return R.ok(deviceMapper.selectList(wrapper));
    }
}
