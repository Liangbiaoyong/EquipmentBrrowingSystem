package com.gzhu.equipment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gzhu.equipment.entity.Notification;
import com.gzhu.equipment.mapper.NotificationMapper;
import com.gzhu.equipment.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl extends ServiceImpl<NotificationMapper, Notification>
        implements NotificationService {

    private final NotificationMapper notificationMapper;

    @Override
    @Transactional
    public void send(Long userId, String title, String content, String type) {
        Notification n = new Notification();
        n.setUserId(userId);
        n.setTitle(title);
        n.setContent(content);
        n.setType(type);
        n.setIsRead(0);
        notificationMapper.insert(n);
        log.info("通知已发送: userId={} title={}", userId, title);
    }

    @Override
    public void notifyBorrowSubmitted(Long userId, String deviceName, Long borrowId) {
        send(userId, "新借用申请待审批",
                "设备「" + deviceName + "」的借用申请已提交，请及时审批。",
                "APPROVAL");
    }

    @Override
    public void notifyApprovalResult(Long userId, String deviceName, Long borrowId, boolean approved, String comment) {
        String title = approved ? "借用申请已通过" : "借用申请被驳回";
        String content = "设备「" + deviceName + "」的借用申请已" + (approved ? "通过" : "驳回") + "。";
        if (comment != null && !comment.isEmpty()) content += " 审批意见：" + comment;
        send(userId, title, content, "APPROVAL");
    }

    @Override
    public void notifyReturnReminder(Long userId, String deviceName, Long borrowId) {
        send(userId, "归还提醒",
                "设备「" + deviceName + "」即将到期，请按时归还。",
                "REMIND");
    }

    @Override
    public void notifyOverdue(Long userId, String deviceName, Long borrowId, int overdueDays) {
        send(userId, "逾期警告",
                "设备「" + deviceName + "」已逾期 " + overdueDays + " 天，请尽快归还！",
                "REMIND");
    }

    @Override
    public int unreadCount(Long userId) {
        Long count = notificationMapper.selectCount(
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .eq(Notification::getIsRead, 0));
        return count != null ? count.intValue() : 0;
    }

    @Override
    public List<Notification> listByUser(Long userId, int page, int size) {
        return notificationMapper.selectList(
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .orderByDesc(Notification::getCreateTime)
                        .last("LIMIT " + ((page - 1) * size) + "," + size));
    }

    @Override
    @Transactional
    public void markRead(Long notificationId) {
        Notification n = notificationMapper.selectById(notificationId);
        if (n != null) {
            n.setIsRead(1);
            notificationMapper.updateById(n);
        }
    }

    @Override
    @Transactional
    public void markAllRead(Long userId) {
        List<Notification> unread = notificationMapper.selectList(
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .eq(Notification::getIsRead, 0));
        unread.forEach(n -> n.setIsRead(1));
        for (Notification n : unread) notificationMapper.updateById(n);
    }
}
