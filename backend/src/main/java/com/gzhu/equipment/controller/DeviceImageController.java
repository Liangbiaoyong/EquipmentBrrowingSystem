package com.gzhu.equipment.controller;

import com.gzhu.equipment.common.R;
import com.gzhu.equipment.entity.Device;
import com.gzhu.equipment.entity.DeviceImage;
import com.gzhu.equipment.mapper.DeviceImageMapper;
import com.gzhu.equipment.mapper.DeviceMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 设备图片管理
 *
 * POST   /devices/{deviceId}/images      → 添加图片
 * DELETE /devices/{deviceId}/images/{id} → 删除图片
 * GET    /devices/{deviceId}/images      → 设备图片列表
 * GET    /devices/missing-images         → 缺少图片的设备列表
 */
@RestController
@RequestMapping("/devices")
@RequiredArgsConstructor
@Api(tags = "设备图片管理")
public class DeviceImageController {

    private final DeviceImageMapper imageMapper;
    private final DeviceMapper deviceMapper;

    @GetMapping("/{deviceId}/images")
    @ApiOperation("获取设备图片列表")
    public R<List<DeviceImage>> listImages(@PathVariable Long deviceId) {
        List<DeviceImage> images = imageMapper.selectList(
                new LambdaQueryWrapper<DeviceImage>()
                        .eq(DeviceImage::getDeviceId, deviceId)
                        .orderByAsc(DeviceImage::getSort));
        return R.ok(images);
    }

    @PostMapping("/{deviceId}/images")
    @ApiOperation("添加设备图片（URL方式）")
    @PreAuthorize("hasAuthority('device:manage')")
    public R<DeviceImage> addImage(@PathVariable Long deviceId,
                                    @RequestParam String imageUrl,
                                    @RequestParam(defaultValue = "0") int sort) {
        Device device = deviceMapper.selectById(deviceId);
        if (device == null) return R.fail(404, "设备不存在");

        DeviceImage img = new DeviceImage();
        img.setDeviceId(deviceId);
        img.setImageUrl(imageUrl);
        img.setSort(sort);
        imageMapper.insert(img);

        // 自动设为封面（如果还没有封面图）
        if (device.getCoverImage() == null || device.getCoverImage().isEmpty()) {
            device.setCoverImage(imageUrl);
            deviceMapper.updateById(device);
        }

        return R.ok(img);
    }

    @DeleteMapping("/images/{id}")
    @ApiOperation("删除设备图片")
    @PreAuthorize("hasAuthority('device:manage')")
    public R<Void> deleteImage(@PathVariable Long id) {
        DeviceImage img = imageMapper.selectById(id);
        if (img != null) {
            imageMapper.deleteById(id);
            // 如果删除的是封面图，尝试用下一张替代
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
        // 查询没有图片的设备
        List<Long> deviceIdsWithImages = imageMapper.selectList(null).stream()
                .map(DeviceImage::getDeviceId)
                .distinct()
                .collect(Collectors.toList());

        LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Device::getStatus, 1);
        if (!deviceIdsWithImages.isEmpty()) {
            wrapper.notIn(Device::getId, deviceIdsWithImages);
        }
        wrapper.last("LIMIT " + ((page - 1) * size) + "," + size);

        return R.ok(deviceMapper.selectList(wrapper));
    }
}
