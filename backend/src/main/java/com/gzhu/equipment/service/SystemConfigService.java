package com.gzhu.equipment.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.gzhu.equipment.entity.SystemConfig;

import java.util.List;

/**
 * 系统配置服务 — 运行时可修改的键值对配置
 *
 * 优先级：数据库 > application.yml 默认值
 * 如果数据库无记录，回退到 application.yml 中 `borrow.max-days` 等默认值
 */
public interface SystemConfigService extends IService<SystemConfig> {

    /** 获取所有配置 */
    List<SystemConfig> listAll();

    /** 获取配置值（数据库优先，无则返回默认值） */
    String getValue(String key, String defaultValue);

    /** 获取 int 型配置值 */
    int getIntValue(String key, int defaultValue);

    /** 设置配置（存在则更新，不存在则新增） */
    void setValue(String key, String value, String description);

    /** 删除配置 */
    void deleteByKey(String key);
}
