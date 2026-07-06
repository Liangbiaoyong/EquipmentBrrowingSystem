package com.gzhu.equipment.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.gzhu.equipment.entity.SysUser;

/**
 * 用户服务接口
 */
public interface SysUserService extends IService<SysUser> {

    /**
     * 根据用户名查找用户
     */
    SysUser getByUsername(String username);

    /**
     * 根据CAS UUID查找用户
     */
    SysUser getByCasUuid(String casUuid);

    /**
     * CAS用户：创建或更新（CAS登录时调用）
     *
     * 逻辑：
     * - 如果 casUuid 或 username 查不到 → 新建用户
     * - 如果查到 → 更新CAS返回的最新信息（姓名、班级、部门等）
     *
     * @return 持久化后的用户对象
     */
    SysUser createOrUpdateCasUser(SysUser casUser);

    /**
     * 创建本地账户（仅系统管理员可用）
     */
    SysUser createLocalUser(String username, String realName, Integer userType,
                            String department, String email, String phone, String password);
}
