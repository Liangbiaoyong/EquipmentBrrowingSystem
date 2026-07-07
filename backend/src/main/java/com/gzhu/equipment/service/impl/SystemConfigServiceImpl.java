package com.gzhu.equipment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gzhu.equipment.entity.SystemConfig;
import com.gzhu.equipment.mapper.SystemConfigMapper;
import com.gzhu.equipment.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemConfigServiceImpl extends ServiceImpl<SystemConfigMapper, SystemConfig>
        implements SystemConfigService {

    private final SystemConfigMapper configMapper;

    @Override
    public List<SystemConfig> listAll() {
        return configMapper.selectList(
                new LambdaQueryWrapper<SystemConfig>().orderByAsc(SystemConfig::getConfigKey));
    }

    @Override
    public String getValue(String key, String defaultValue) {
        String val = configMapper.getValueByKey(key);
        return (val != null && !val.isEmpty()) ? val : defaultValue;
    }

    @Override
    public int getIntValue(String key, int defaultValue) {
        String val = getValue(key, null);
        try {
            return val != null ? Integer.parseInt(val) : defaultValue;
        } catch (NumberFormatException e) {
            log.warn("配置值不是整数: key={} value={}", key, val);
            return defaultValue;
        }
    }

    @Override
    @Transactional
    public void setValue(String key, String value, String description) {
        SystemConfig existing = configMapper.selectOne(
                new LambdaQueryWrapper<SystemConfig>().eq(SystemConfig::getConfigKey, key));
        if (existing != null) {
            existing.setConfigValue(value);
            if (description != null) existing.setDescription(description);
            configMapper.updateById(existing);
            log.info("更新系统配置: key={} value={}", key, value);
        } else {
            SystemConfig config = new SystemConfig();
            config.setConfigKey(key);
            config.setConfigValue(value);
            config.setDescription(description);
            configMapper.insert(config);
            log.info("新增系统配置: key={} value={}", key, value);
        }
    }

    @Override
    @Transactional
    public void deleteByKey(String key) {
        configMapper.delete(new LambdaQueryWrapper<SystemConfig>().eq(SystemConfig::getConfigKey, key));
    }
}
