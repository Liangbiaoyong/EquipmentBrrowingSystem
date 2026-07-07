package com.gzhu.equipment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
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
                                   String keyword, Long categoryId,
                                   Integer status, String location,
                                   String gbCategoryName) {
        LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<>();

        // 关键词：匹配名称、资产编号、型号
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w
                    .like(Device::getName, keyword)
                    .or().like(Device::getAssetNo, keyword)
                    .or().like(Device::getModel, keyword)
            );
        }
        if (categoryId != null) {
            wrapper.eq(Device::getCategoryId, categoryId);
        }
        if (status != null) {
            wrapper.eq(Device::getStatus, status);
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
    public List<String> listBatches() {
        // 查询所有不同的导入批次 — 使用字符串列名避免LambdaQueryWrapper在无Spring上下文中报错
        QueryWrapper<Device> wrapper = new QueryWrapper<>();
        wrapper.select("DISTINCT import_batch_id")
                .isNotNull("import_batch_id")
                .orderByDesc("import_batch_id");
        return deviceMapper.selectMaps(wrapper).stream()
                .map(m -> (String) m.get("import_batch_id"))
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
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
