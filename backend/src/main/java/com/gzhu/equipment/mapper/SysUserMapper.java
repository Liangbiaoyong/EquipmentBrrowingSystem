package com.gzhu.equipment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gzhu.equipment.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 用户表 Mapper
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /**
     * 根据用户名查找用户
     */
    @Select("SELECT * FROM sys_user WHERE username = #{username}")
    SysUser selectByUsername(@Param("username") String username);

    /**
     * 根据CAS UUID查找用户
     */
    @Select("SELECT * FROM sys_user WHERE cas_uuid = #{casUuid}")
    SysUser selectByCasUuid(@Param("casUuid") String casUuid);
}
