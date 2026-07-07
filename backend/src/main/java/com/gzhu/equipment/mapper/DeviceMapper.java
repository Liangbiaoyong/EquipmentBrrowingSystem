package com.gzhu.equipment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gzhu.equipment.entity.Device;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 设备表 Mapper
 */
@Mapper
public interface DeviceMapper extends BaseMapper<Device> {

    /** 按资产编号查找 */
    @Select("SELECT * FROM device WHERE asset_no = #{assetNo}")
    Device selectByAssetNo(@Param("assetNo") String assetNo);
}
