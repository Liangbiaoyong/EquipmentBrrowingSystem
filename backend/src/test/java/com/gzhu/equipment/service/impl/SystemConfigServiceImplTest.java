package com.gzhu.equipment.service.impl;

import com.gzhu.equipment.entity.SystemConfig;
import com.gzhu.equipment.mapper.SystemConfigMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * SystemConfigServiceImpl 单元测试
 *
 * 覆盖：DB优先配置解析、新增/更新、删除、列表查询
 */
@ExtendWith(MockitoExtension.class)
class SystemConfigServiceImplTest {

    @Mock
    private SystemConfigMapper configMapper;

    private SystemConfigServiceImpl configService;

    @BeforeEach
    void setUp() {
        configService = new SystemConfigServiceImpl(configMapper);
    }

    @Test
    @DisplayName("getValue（DB有值）→ 返回DB值")
    void getValue_dbHasValue_shouldReturnDbValue() {
        when(configMapper.getValueByKey("borrow.max_days")).thenReturn("14");

        String val = configService.getValue("borrow.max_days", "7");

        assertThat(val).isEqualTo("14");
    }

    @Test
    @DisplayName("getValue（DB无值）→ 返回默认值")
    void getValue_dbEmpty_shouldReturnDefault() {
        when(configMapper.getValueByKey("borrow.max_days")).thenReturn(null);

        String val = configService.getValue("borrow.max_days", "7");

        assertThat(val).isEqualTo("7");
    }

    @Test
    @DisplayName("getIntValue（DB有值且为整数）→ 返回解析后的整数")
    void getIntValue_validInt_shouldReturnParsed() {
        when(configMapper.getValueByKey("borrow.max_days")).thenReturn("14");

        int val = configService.getIntValue("borrow.max_days", 7);

        assertThat(val).isEqualTo(14);
    }

    @Test
    @DisplayName("getIntValue（DB无值）→ 返回默认整数")
    void getIntValue_noValue_shouldReturnDefault() {
        when(configMapper.getValueByKey("borrow.max_days")).thenReturn(null);

        int val = configService.getIntValue("borrow.max_days", 7);

        assertThat(val).isEqualTo(7);
    }

    @Test
    @DisplayName("getIntValue（DB值非数字）→ 返回默认整数")
    void getIntValue_invalidFormat_shouldReturnDefault() {
        when(configMapper.getValueByKey("borrow.max_days")).thenReturn("abc");

        int val = configService.getIntValue("borrow.max_days", 7);

        assertThat(val).isEqualTo(7);
    }

    @Test
    @DisplayName("setValue（DB已存在）→ 更新")
    void setValue_existing_shouldUpdate() {
        SystemConfig existing = new SystemConfig();
        existing.setConfigKey("borrow.max_days");
        existing.setConfigValue("7");
        when(configMapper.selectOne(any())).thenReturn(existing);

        configService.setValue("borrow.max_days", "14", null);

        assertThat(existing.getConfigValue()).isEqualTo("14");
        verify(configMapper).updateById(existing);
        verify(configMapper, never()).insert(any());
    }

    @Test
    @DisplayName("setValue（DB不存在）→ 新增")
    void setValue_new_shouldInsert() {
        when(configMapper.selectOne(any())).thenReturn(null);

        configService.setValue("new.key", "value1", "测试配置");

        verify(configMapper).insert(any(SystemConfig.class));
    }

    @Test
    @DisplayName("deleteByKey → 删除配置")
    void deleteByKey_shouldDelete() {
        configService.deleteByKey("borrow.max_days");

        verify(configMapper).delete(any());
    }

    @Test
    @DisplayName("listAll → 返回所有配置")
    void listAll_shouldReturnAll() {
        when(configMapper.selectList(any())).thenReturn(List.of(new SystemConfig()));

        List<SystemConfig> list = configService.listAll();

        assertThat(list).hasSize(1);
    }
}
