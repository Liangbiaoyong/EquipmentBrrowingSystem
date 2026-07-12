package com.gzhu.equipment.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gzhu.equipment.entity.SysUser;
import com.gzhu.equipment.mapper.SysUserMapper;
import com.gzhu.equipment.service.SysUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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
            // 同步CAS密码（BCrypt已加密），支持本地登录
            if (casUser.getPassword() != null && !casUser.getPassword().isEmpty()) {
                existing.setPassword(casUser.getPassword());
            }
            // 如果现有用户是CAS用户，保持userType不变（除非手动提升为管理员）
            // 首次CAS登录时根据ident自动设置userType
            if (existing.getUserType() == null || existing.getUserType() == 0 || existing.getUserType() == 1) {
                existing.setUserType(casUser.getUserType());
            }
            sysUserMapper.updateById(existing);
            log.info("CAS用户信息已更新: username={}, realName={}", existing.getUsername(), existing.getRealName());
            return existing;
        } else {
            // 新建用户 — 捕获并发插入导致的唯一键冲突，降级为更新
            casUser.setAuthSource("C");
            // 保存CAS密码用于本地登录（AuthServiceImpl.casCredentialLogin 已BCrypt加密）
            if (casUser.getPassword() == null) casUser.setPassword("");
            casUser.setStatus(1);
            casUser.setLastCasLogin(LocalDateTime.now());
            try {
                sysUserMapper.insert(casUser);
                log.info("CAS新用户已创建: username={}, realName={}, userType={}",
                        casUser.getUsername(), casUser.getRealName(), casUser.getUserType());
                return casUser;
            } catch (DuplicateKeyException e) {
                // 并发场景：另一线程已插入相同 username/cas_uuid，重新查询并更新
                log.warn("CAS用户并发插入冲突，降级为更新: username={}", casUser.getUsername());
                SysUser concurrent = null;
                if (casUser.getCasUuid() != null) {
                    concurrent = sysUserMapper.selectByCasUuid(casUser.getCasUuid());
                }
                if (concurrent == null && casUser.getUsername() != null) {
                    concurrent = sysUserMapper.selectByUsername(casUser.getUsername());
                }
                if (concurrent != null) {
                    concurrent.setRealName(casUser.getRealName());
                    concurrent.setDepartment(casUser.getDepartment());
                    concurrent.setClassName(casUser.getClassName());
                    concurrent.setClassId(casUser.getClassId());
                    concurrent.setDeptId(casUser.getDeptId());
                    concurrent.setIdent(casUser.getIdent());
                    concurrent.setCardNo(casUser.getCardNo());
                    concurrent.setCasUuid(casUser.getCasUuid());
                    concurrent.setAccNo(casUser.getAccNo());
                    concurrent.setEmail(casUser.getEmail());
                    concurrent.setPhone(casUser.getPhone());
                    concurrent.setSex(casUser.getSex());
                    concurrent.setExpiredDate(casUser.getExpiredDate());
                    concurrent.setLastCasLogin(LocalDateTime.now());
                    concurrent.setAuthSource("C");
                    if (concurrent.getUserType() == null || concurrent.getUserType() == 0 || concurrent.getUserType() == 1) {
                        concurrent.setUserType(casUser.getUserType());
                    }
                    sysUserMapper.updateById(concurrent);
                    log.info("CAS用户并发处理后已更新: username={}", concurrent.getUsername());
                    return concurrent;
                }
                // 极端情况：重查也找不到 → 重新抛出
                throw e;
            }
        }
    }

    @Override
    @Transactional
    public SysUser createLocalUser(String username, String realName, Integer userType,
                                   String department, String email, String phone, String password) {
        // 参数校验
        if (!StringUtils.hasText(username) || username.trim().length() < 3) {
            throw new IllegalArgumentException("用户名至少需要3个字符");
        }
        if (!StringUtils.hasText(password) || password.length() < 8) {
            throw new IllegalArgumentException("密码长度至少为8位");
        }

        // 检查用户名是否已存在
        SysUser existing = sysUserMapper.selectByUsername(username);
        if (existing != null) {
            throw new IllegalArgumentException("用户名已存在: " + username);
        }

        SysUser user = new SysUser();
        user.setUsername(username.trim());
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

    @Override
    public String encodePassword(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }
}
