package com.gzhu.equipment.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 当前登录用户信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInfoVO {

    /** 用户ID */
    private Long id;

    /** 用户名（学工号/登录名） */
    private String username;

    /** 真实姓名 */
    private String realName;

    /** 用户类型: 0学生 1教师 2实验室管理员 3系统管理员 */
    private Integer userType;

    /** 用户类型中文名 */
    private String userTypeName;

    /** 学院/部门 */
    private String department;

    /** 班级名称（仅学生有值） */
    private String className;

    /** 邮箱 */
    private String email;

    /** 手机号 */
    private String phone;

    /** 认证来源: C:CAS L:本地 */
    private String authSource;

    /** 角色标识列表，用于前端路由守卫 */
    private List<String> roles;

    /** 权限标识列表，用于前端按钮级权限 */
    private List<String> permissions;
}
