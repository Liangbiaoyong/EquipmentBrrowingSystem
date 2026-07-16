package com.gzhu.equipment.security;

import java.util.*;

/**
 * 权限常量定义 + 用户类型 → 权限列表映射
 *
 * 权限标识采用 module:action 命名，与前端路由守卫的 meta.permissions 对应。
 * 后端 Controller 使用 @PreAuthorize("hasAuthority('device:manage')") 校验。
 */
public final class PermissionConstants {

    private PermissionConstants() {}

    // ==================== 权限标识定义 ====================

    public static final String DASHBOARD_VIEW    = "dashboard:view";
    public static final String NOTIFICATION_VIEW = "notification:view";
    public static final String PROFILE_VIEW      = "profile:view";

    public static final String DEVICE_VIEW       = "device:view";
    public static final String DEVICE_MANAGE     = "device:manage";

    public static final String BORROW_CREATE     = "borrow:create";
    public static final String BORROW_MY         = "borrow:my";
    public static final String BORROW_VIEW       = "borrow:view";
    public static final String BORROW_RETURN     = "borrow:return";

    public static final String APPROVAL_FIRST    = "approval:first";
    public static final String APPROVAL_SECOND   = "approval:second";

    public static final String RETURN_MANAGE     = "return:manage";
    public static final String REPAIR_MANAGE     = "repair:manage";

    public static final String STATISTICS_VIEW   = "statistics:view";

    public static final String ADMIN_USER        = "admin:user";
    public static final String ADMIN_CONFIG      = "admin:config";
    public static final String ADMIN_LOG         = "admin:log";
    public static final String ADMIN_BACKUP      = "admin:backup";

    public static final String LAB_VIEW          = "laboratory:view";
    public static final String LAB_MANAGE        = "laboratory:manage";

    // ==================== 用户类型 → 权限映射 ====================

    /** 学生权限 */
    private static final List<String> STUDENT_PERMS = Collections.unmodifiableList(Arrays.asList(
            DASHBOARD_VIEW, NOTIFICATION_VIEW, PROFILE_VIEW,
            DEVICE_VIEW, LAB_VIEW,
            BORROW_CREATE, BORROW_MY, BORROW_VIEW, BORROW_RETURN,
            STATISTICS_VIEW
    ));

    /** 教师权限 = 学生权限 + 一级审批 + 统计 */
    private static final List<String> TEACHER_PERMS = Collections.unmodifiableList(buildTeacherPerms());

    /** 实验室管理员 */
    private static final List<String> LAB_ADMIN_PERMS = Collections.unmodifiableList(Arrays.asList(
            DASHBOARD_VIEW, NOTIFICATION_VIEW, PROFILE_VIEW,
            DEVICE_VIEW, DEVICE_MANAGE,
            LAB_VIEW, LAB_MANAGE,
            BORROW_VIEW, BORROW_RETURN,
            APPROVAL_FIRST, APPROVAL_SECOND,  // 管理员可处理初审和终审
            RETURN_MANAGE, REPAIR_MANAGE,
            STATISTICS_VIEW
    ));

    /** 系统管理员 */
    private static final List<String> SYSTEM_ADMIN_PERMS = Collections.unmodifiableList(Arrays.asList(
            DASHBOARD_VIEW, NOTIFICATION_VIEW, PROFILE_VIEW,
            DEVICE_VIEW, DEVICE_MANAGE,
            LAB_VIEW, LAB_MANAGE,
            BORROW_CREATE, BORROW_MY, BORROW_VIEW, BORROW_RETURN,
            APPROVAL_FIRST, APPROVAL_SECOND,
            RETURN_MANAGE, REPAIR_MANAGE,
            STATISTICS_VIEW,
            ADMIN_USER, ADMIN_CONFIG, ADMIN_LOG, ADMIN_BACKUP
    ));

    private static List<String> buildTeacherPerms() {
        List<String> perms = new ArrayList<>(STUDENT_PERMS);
        perms.add(APPROVAL_FIRST);
        perms.add(DEVICE_MANAGE);   // 教师可管理个人名下设备
        perms.add(STATISTICS_VIEW);
        return perms;
    }

    /**
     * 根据用户类型获取权限列表
     */
    public static List<String> getPermissionsByUserType(Integer userType) {
        if (userType == null) return Collections.emptyList();
        switch (userType) {
            case 0: return STUDENT_PERMS;
            case 1: return TEACHER_PERMS;
            case 2: {
                // 实验室管理员 = 基础权限（含初审+终审）
                return LAB_ADMIN_PERMS;
            }
            case 3: return SYSTEM_ADMIN_PERMS;
            default: return Collections.emptyList();
        }
    }

    /**
     * 将权限列表转为 Spring Security GrantedAuthority 格式（hasAuthority 可识别）
     */
    public static List<String> toAuthorityStrings(Integer userType) {
        return new ArrayList<>(getPermissionsByUserType(userType));
    }
}
