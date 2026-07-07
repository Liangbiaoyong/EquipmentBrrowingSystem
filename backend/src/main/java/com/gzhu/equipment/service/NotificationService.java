package com.gzhu.equipment.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.gzhu.equipment.entity.Notification;
import java.util.List;

public interface NotificationService extends IService<Notification> {

    /** 发送通知 */
    void send(Long userId, String title, String content, String type);

    /** 借用提交通知（通知审批人） */
    void notifyBorrowSubmitted(Long userId, String deviceName, Long borrowId);

    /** 审批结果通知 */
    void notifyApprovalResult(Long userId, String deviceName, Long borrowId, boolean approved, String comment);

    /** 归还提醒（提前1天） */
    void notifyReturnReminder(Long userId, String deviceName, Long borrowId);

    /** 逾期警告 */
    void notifyOverdue(Long userId, String deviceName, Long borrowId, int overdueDays);

    /** 用户未读通知数 */
    int unreadCount(Long userId);

    /** 用户通知列表（分页），标记已读 */
    List<Notification> listByUser(Long userId, int page, int size);

    /** 标记单条已读 */
    void markRead(Long notificationId);

    /** 全部标为已读 */
    void markAllRead(Long userId);
}
