package com.gzhu.equipment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gzhu.equipment.entity.SysUser;
import com.gzhu.equipment.mapper.SysUserMapper;
import com.gzhu.equipment.service.SysUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 用户服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public SysUser getByUsername(String username) {
        return sysUserMapper.selectByUsername(username);
    }

    @Override
    public SysUser getByCasUuid(String casUuid) {
        return sysUserMapper.selectByCasUuid(casUuid);
    }

    @Override
    @Transactional
    public SysUser createOrUpdateCasUser(SysUser casUser) {
        // 优先按 casUuid 查找，其次按 username
        SysUser existing = null;
        if (casUser.getCasUuid() != null) {
            existing = sysUserMapper.selectByCasUuid(casUser.getCasUuid());
        }
        if (existing == null && casUser.getUsername() != null) {
            existing = sysUserMapper.selectByUsername(casUser.getUsername());
        }

        if (existing != null) {
            // 更新已有用户信息
            existing.setRealName(casUser.getRealName());
            existing.setDepartment(casUser.getDepartment());
            existing.setClassName(casUser.getClassName());
            existing.setClassId(casUser.getClassId());
            existing.setDeptId(casUser.getDeptId());
            existing.setIdent(casUser.getIdent());
            existing.setCardNo(casUser.getCardNo());
            existing.setCasUuid(casUser.getCasUuid());
            existing.setAccNo(casUser.getAccNo());
            existing.setEmail(casUser.getEmail());
            existing.setPhone(casUser.getPhone());
            existing.setSex(casUser.getSex());
            existing.setExpiredDate(casUser.getExpiredDate());
            existing.setLastCasLogin(LocalDateTime.now());
            // CAS用户始终保持 auth_source = C
            existing.setAuthSource("C");
            // 如果现有用户是CAS用户，保持userType不变（除非手动提升为管理员）
            // 首次CAS登录时根据ident自动设置userType
            if (existing.getUserType() == null || existing.getUserType() == 0 || existing.getUserType() == 1) {
                existing.setUserType(casUser.getUserType());
            }
            sysUserMapper.updateById(existing);
            log.info("CAS用户信息已更新: username={}, realName={}", existing.getUsername(), existing.getRealName());
            return existing;
        } else {
            // 新建用户
            casUser.setAuthSource("C");
            casUser.setPassword("");
            casUser.setStatus(1);
            casUser.setLastCasLogin(LocalDateTime.now());
            sysUserMapper.insert(casUser);
            log.info("CAS新用户已创建: username={}, realName={}, userType={}",
                    casUser.getUsername(), casUser.getRealName(), casUser.getUserType());
            return casUser;
        }
    }

    @Override
    @Transactional
    public SysUser createLocalUser(String username, String realName, Integer userType,
                                   String department, String email, String phone, String password) {
        // 检查用户名是否已存在
        SysUser existing = sysUserMapper.selectByUsername(username);
        if (existing != null) {
            throw new IllegalArgumentException("用户名已存在: " + username);
        }

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setRealName(realName);
        user.setUserType(userType);
        user.setDepartment(department);
        user.setEmail(email);
        user.setPhone(phone);
        user.setPassword(passwordEncoder.encode(password));
        user.setAuthSource("L");
        user.setStatus(1);

        sysUserMapper.insert(user);
        log.info("本地用户已创建: username={}, userType={}", username, userType);
        return user;
    }
}
