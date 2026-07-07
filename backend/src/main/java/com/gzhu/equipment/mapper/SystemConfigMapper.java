package com.gzhu.equipment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gzhu.equipment.entity.SystemConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SystemConfigMapper extends BaseMapper<SystemConfig> {

    @Select("SELECT config_value FROM system_config WHERE config_key = #{key} LIMIT 1")
    String getValueByKey(@Param("key") String key);
}
