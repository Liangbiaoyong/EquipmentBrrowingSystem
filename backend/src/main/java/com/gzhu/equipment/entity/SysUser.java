package com.gzhu.equipment.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 系统用户表 — 支持 CAS 统一认证与本地账户并存
 *
 * 角色判断逻辑（用户类型 user_type）：
 * - 0 = STUDENT   — CAS登录且 ident=257
 * - 1 = TEACHER   — CAS登录且 ident=259
 * - 2 = LAB_ADMIN — 本地创建或CAS指定（系统管理员手动授予）
 * - 3 = SYSTEM_ADMIN — 本地独立账户
 *
 * CAS 身份标识（ident 字段）：
 * - 257 = 学生
 * - 259 = 教师
 * 教师账户的特征：dept_name == class_name（均为部门/学院名）
 * 学生账户的特征：dept_name != class_name（分别为学院名和班级名）
 */
@Data
@TableName("sys_user")
public class SysUser implements Serializable {

    private static final long serialVersionUID = 1L;

    // ==================== 核心业务字段 ====================

    @TableId(type = IdType.AUTO)
    private Long id;

    /** CAS学工号或本地登录名（唯一） */
    private String username;

    /** 真实姓名 */
    private String realName;

    /**
     * 0=学生  1=教师  2=实验室管理员  3=系统管理员
     */
    private Integer userType;

    /** 学院/部门名称（CAS deptName） */
    private String department;

    /** 邮箱 */
    private String email;

    /** 手机号 */
    private String phone;

    /** BCrypt加密密码（仅本地用户 auth_source=L） */
    private String password;

    /**
     * 认证来源: C=CAS统一认证  L=本地账户
     */
    private String authSource;

    /** 1=正常  0=禁用 */
    private Integer status;

    // ==================== CAS 系统字段 ====================

    /** CAS系统全局唯一UUID */
    private String casUuid;

    /** CAS账号编号(accNo) */
    private Integer accNo;

    /** 班级名称（学生: className; 教师: 同deptName） */
    private String className;

    /** 班级ID(classId) */
    private Long classId;

    /** 学院/部门ID(deptId) */
    private Long deptId;

    /** CAS身份标识: 257=学生  259=教师 */
    private Integer ident;

    /** 一卡通号(cardNo) */
    private String cardNo;

    /** 性别: 0=未知  1=男  2=女 */
    private Integer sex;

    /** CAS账号过期日期（yyyyMMdd格式） */
    private Integer expiredDate;

    /** 最近一次CAS登录时间 */
    private LocalDateTime lastCasLogin;

    // ==================== 审计字段 ====================

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
