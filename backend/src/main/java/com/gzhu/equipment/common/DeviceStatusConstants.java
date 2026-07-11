package com.gzhu.equipment.common;

/**
 * 设备状态常量 — V2：借还状态 与 设备物理状态 分离
 */
public final class DeviceStatusConstants {

    private DeviceStatusConstants() {}

    // ==================== 借还状态 ====================
    /** 可借用 */
    public static final int BORROW_AVAILABLE = 1;
    /** 借用中 */
    public static final int BORROW_BORROWING = 2;
    /** 不可借（维修/报废中） */
    public static final int BORROW_UNAVAILABLE = 3;
    /** 逾期（设备当前借用已超期） */
    public static final int BORROW_OVERDUE = 4;

    // ==================== 设备物理状态 ====================
    /** 正常 */
    public static final int DEVICE_NORMAL = 1;
    /** 待维修（已报修未开始） */
    public static final int DEVICE_PENDING_REPAIR = 2;
    /** 维修中 */
    public static final int DEVICE_REPAIRING = 3;
    /** 待报废 */
    public static final int DEVICE_PENDING_SCRAP = 4;
    /** 已报废 */
    public static final int DEVICE_SCRAPPED = 5;

    // ==================== 工具方法 ====================

    public static String borrowStatusName(int status) {
        switch (status) {
            case BORROW_AVAILABLE: return "可借用";
            case BORROW_BORROWING: return "借用中";
            case BORROW_UNAVAILABLE: return "不可借";
            case BORROW_OVERDUE: return "逾期";
            default: return "未知";
        }
    }

    public static String deviceStatusName(int status) {
        switch (status) {
            case DEVICE_NORMAL: return "正常";
            case DEVICE_PENDING_REPAIR: return "待维修";
            case DEVICE_REPAIRING: return "维修中";
            case DEVICE_PENDING_SCRAP: return "待报废";
            case DEVICE_SCRAPPED: return "已报废";
            default: return "未知";
        }
    }

    /** 设备处于不可借的物理状态时，借用状态自动为不可借 */
    public static boolean isDeviceUnborrowable(int deviceStatus) {
        return deviceStatus == DEVICE_PENDING_REPAIR
                || deviceStatus == DEVICE_REPAIRING
                || deviceStatus == DEVICE_PENDING_SCRAP
                || deviceStatus == DEVICE_SCRAPPED;
    }

    /** 旧status(1-4) → 新 borrow_status + device_status */
    public static int legacyToBorrowStatus(int legacy) {
        switch (legacy) {
            case 1: return BORROW_AVAILABLE;
            case 2: return BORROW_BORROWING;
            case 3: return BORROW_UNAVAILABLE;
            case 4: return BORROW_UNAVAILABLE;
            default: return BORROW_AVAILABLE;
        }
    }

    public static int legacyToDeviceStatus(int legacy) {
        switch (legacy) {
            case 1: return DEVICE_NORMAL;
            case 2: return DEVICE_NORMAL;
            case 3: return DEVICE_REPAIRING;
            case 4: return DEVICE_PENDING_SCRAP;
            default: return DEVICE_NORMAL;
        }
    }
}
