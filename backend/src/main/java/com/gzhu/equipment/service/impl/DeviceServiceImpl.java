package com.gzhu.equipment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gzhu.equipment.dto.BatchInfoDTO;
import com.gzhu.equipment.entity.Device;
import com.gzhu.equipment.mapper.DeviceMapper;
import com.gzhu.equipment.service.DeviceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceServiceImpl extends ServiceImpl<DeviceMapper, Device> implements DeviceService {

    private final DeviceMapper deviceMapper;

    @Override
    public IPage<Device> pageQuery(int page, int size,
                                   String keyword, String assetNo, String name, String model,
                                   Long categoryId,
                                   Integer borrowStatus, Integer deviceStatus,
                                   String location, String gbCategoryName,
                                   Integer borrowType, Long laboratoryId) {
        LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<>();

        // 分离字段精确搜索（优先于keyword）
        if (StringUtils.hasText(assetNo)) {
            wrapper.like(Device::getAssetNo, assetNo);
        }
        if (StringUtils.hasText(name)) {
            wrapper.like(Device::getName, name);
        }
        if (StringUtils.hasText(model)) {
            wrapper.like(Device::getModel, model);
        }
        // 关键词：同时匹配名称、资产编号、型号、ID
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> {
                w.like(Device::getName, keyword)
                 .or().like(Device::getAssetNo, keyword)
                 .or().like(Device::getModel, keyword)
                 .or().like(Device::getLocation, keyword)
                 .or().like(Device::getGbCategoryName, keyword);
                try { w.or().eq(Device::getId, Long.parseLong(keyword)); } catch (NumberFormatException ignored) {}
            });
        }
        if (categoryId != null) {
            wrapper.eq(Device::getCategoryId, categoryId);
        }
        if (borrowStatus != null) {
            wrapper.eq(Device::getBorrowStatus, borrowStatus);
        }
        if (deviceStatus != null) {
            wrapper.eq(Device::getDeviceStatus, deviceStatus);
        }
        if (borrowType != null) {
            wrapper.eq(Device::getBorrowType, borrowType);
        }
        if (laboratoryId != null) {
            wrapper.eq(Device::getLaboratoryId, laboratoryId);
        }
        if (StringUtils.hasText(location)) {
            wrapper.like(Device::getLocation, location);
        }
        if (StringUtils.hasText(gbCategoryName)) {
            wrapper.like(Device::getGbCategoryName, gbCategoryName);
        }

        wrapper.orderByDesc(Device::getCreateTime);
        return deviceMapper.selectPage(new Page<>(page, size), wrapper);
    }

    @Override
    public List<Long> countByCategory() {
        return Collections.emptyList(); // 待实现聚合统计
    }

    @Override
    public Device getByAssetNo(String assetNo) {
        return deviceMapper.selectByAssetNo(assetNo);
    }

    @Override
    @Transactional
    public void deleteDevice(Long id) {
        deviceMapper.deleteById(id);
        log.info("删除设备: id={}", id);
    }

    @Override
    @Transactional
    public int deleteByBatchId(String batchId) {
        LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Device::getImportBatchId, batchId);
        int count = deviceMapper.delete(wrapper);
        log.info("按批次清除设备: batchId={} count={}", batchId, count);
        return count;
    }

    @Override
    public List<BatchInfoDTO> listBatches() {
        // 查询批次聚合信息：批次号、最早创建时间、成功/更新/失败/删除行数
        QueryWrapper<Device> wrapper = new QueryWrapper<>();
        wrapper.select("import_batch_id, COUNT(*) AS total_count, " +
                        "MIN(create_time) AS create_time, " +
                        "SUM(CASE WHEN import_batch_id IS NOT NULL THEN 1 ELSE 0 END) AS device_count")
                .isNotNull("import_batch_id")
                .groupBy("import_batch_id")
                .orderByDesc("MIN(create_time)");
        List<Map<String, Object>> maps = deviceMapper.selectMaps(wrapper);
        List<BatchInfoDTO> result = new java.util.ArrayList<>();
        for (Map<String, Object> m : maps) {
            BatchInfoDTO dto = BatchInfoDTO.builder()
                    .batchId((String) m.get("import_batch_id"))
                    .createTime(m.get("create_time") != null ? m.get("create_time").toString() : "")
                    .successCount(((Number) m.getOrDefault("device_count", 0)).intValue())
                    .build();
            result.add(dto);
        }
        return result;
    }

    @Override
    public List<Device> listByBatchId(String batchId) {
        LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Device::getImportBatchId, batchId)
                .orderByAsc(Device::getCreateTime);
        // 使用 lambda 表达式时确保 MyBatis-Plus 注解处理器已启用
        // 若在纯 Mockito 测试中报错，改用 QueryWrapper
        return deviceMapper.selectList(wrapper);
    }
}
