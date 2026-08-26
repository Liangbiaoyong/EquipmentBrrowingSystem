package com.gzhu.equipment.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gzhu.equipment.entity.ApprovalLog;
import com.gzhu.equipment.entity.Attachment;
import com.gzhu.equipment.entity.BorrowRecord;
import com.gzhu.equipment.entity.Device;
import com.gzhu.equipment.entity.Notification;
import com.gzhu.equipment.mapper.*;
import com.gzhu.equipment.service.MinioFileService;
import com.gzhu.equipment.service.NotificationService;
import com.gzhu.equipment.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 定时清理任务 — 每天凌晨3点执行
 *
 * 清理策略（可通过 system_config 表运行时修改）：
 * ┌─────────────────────┬──────────┬────────────────────────────┐
 * │ 数据类型            │ 保留周期 │ 说明                       │
 * ├─────────────────────┼──────────┼────────────────────────────┤
 * │ 已读通知            │ 180天    │ notification.read_cleanup_days │
 * │ 未读通知            │ -1(永久) │ notification.unread_cleanup_days │
 * │ 操作日志(sys_log)   │  15天    │ 审计日志，占用小           │
 * │ 审批记录(approval)  │  15天    │ 审批历史，占用小（注：借用│
 * │                     │          │ 单本身及其approve_flow_def │
 * │                     │          │ JSON快照永久保留）         │
 * │ 附件(attachment)    │  30天    │ 借用/归还照片，占用大     │
 * │ 借用归还图片        │  30天    │ 同上（通过expire_time控制）│
 * └─────────────────────┴──────────┴────────────────────────────┘
 *
 * 此外还执行：
 * - 逾期检测：BORROWING且endTime已过 → 标记OVERDUE + 通知
 * - 归还提醒：明天到期的借用 → 提前通知
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledCleanupTask {

    private final NotificationMapper notificationMapper;
    private final SysLogMapper sysLogMapper;
    private final ApprovalLogMapper approvalLogMapper;
    private final AttachmentMapper attachmentMapper;
    private final BorrowRecordMapper borrowMapper;
    private final com.gzhu.equipment.mapper.DeviceMapper deviceMapper;
    private final NotificationService notificationService;
    private final SystemConfigService configService;
    private final MinioFileService minioFileService;

    // 默认值（system_config 表中有则优先）
    private static final int DEFAULT_SMALL_RECORD_DAYS = 15;
    private static final int DEFAULT_LARGE_FILE_DAYS = 30;
    private static final int DEFAULT_NOTIFICATION_READ_DAYS = 180;
    private static final int DEFAULT_NOTIFICATION_UNREAD_DAYS = -1;

    @Scheduled(cron = "0 0 3 * * ?")
    public void execute() {
        log.info("========== 定时清理任务开始 ==========");

        int smallDays = configService.getIntValue("cleanup.small_record_days", DEFAULT_SMALL_RECORD_DAYS);
        int largeDays = configService.getIntValue("cleanup.large_file_days", DEFAULT_LARGE_FILE_DAYS);
        int notificationReadDays = configService.getIntValue("notification.read_cleanup_days", DEFAULT_NOTIFICATION_READ_DAYS);
        int notificationUnreadDays = configService.getIntValue("notification.unread_cleanup_days", DEFAULT_NOTIFICATION_UNREAD_DAYS);

        log.info("清理参数: smallRecord={}天, largeFile={}天, 已读通知={}天, 未读通知={}天",
                smallDays, largeDays, notificationReadDays, notificationUnreadDays);

        // ─── 1. 小记录清理（15天）───
        int deleted = 0;

        // 1a. 通知（已读/未读分别按各自保留天数清理，-1 表示永久保留）
        int notificationDeleted = 0;
        if (notificationReadDays >= 0) {
            LocalDateTime readThreshold = LocalDateTime.now().minusDays(notificationReadDays);
            notificationDeleted += notificationMapper.delete(
                    new LambdaQueryWrapper<Notification>()
                            .eq(Notification::getIsRead, 1)
                            .lt(Notification::getCreateTime, readThreshold));
        }
        if (notificationUnreadDays >= 0) {
            LocalDateTime unreadThreshold = LocalDateTime.now().minusDays(notificationUnreadDays);
            notificationDeleted += notificationMapper.delete(
                    new LambdaQueryWrapper<Notification>()
                            .eq(Notification::getIsRead, 0)
                            .lt(Notification::getCreateTime, unreadThreshold));
        }
        deleted += notificationDeleted;
        log.info("清理通知: {} 条（已读{}天/未读{}天）", notificationDeleted,
                notificationReadDays < 0 ? "永久" : notificationReadDays + "天",
                notificationUnreadDays < 0 ? "永久" : notificationUnreadDays + "天");

        // 1b. 操作日志
        LocalDateTime smallThreshold = LocalDateTime.now().minusDays(smallDays);
        int logDeleted = sysLogMapper.delete(
                new LambdaQueryWrapper<com.gzhu.equipment.entity.SysLog>()
                        .lt(com.gzhu.equipment.entity.SysLog::getCreateTime, smallThreshold));
        log.info("清理操作日志: {} 条（{}天前）", logDeleted, smallDays);

        // 1c. 审批记录（仅清理已完成的PENDING不清理）
        int approvalDeleted = approvalLogMapper.delete(
                new LambdaQueryWrapper<ApprovalLog>()
                        .in(ApprovalLog::getResult, "APPROVED", "REJECTED")
                        .lt(ApprovalLog::getOperateTime, smallThreshold));
        log.info("清理审批记录: {} 条（{}天前）", approvalDeleted, smallDays);

        // ─── 2. 大型临时文件清理（30天）───
        LocalDateTime largeThreshold = LocalDateTime.now().minusDays(largeDays);

        // 2a. 过期附件（先删MinIO文件，再删DB记录）
        var expiredAtts = attachmentMapper.selectList(
                new LambdaQueryWrapper<Attachment>()
                        .and(w -> w
                                .lt(Attachment::getExpireTime, LocalDateTime.now())
                                .or()
                                .lt(Attachment::getUploadTime, largeThreshold)));
        int attDeleted = 0;
        for (Attachment att : expiredAtts) {
            minioFileService.deleteFile(att.getFileUrl());
            attachmentMapper.deleteById(att.getId());
            attDeleted++;
        }
        log.info("清理附件(含MinIO): {} 条（{}天前或已过期）", attDeleted, largeDays);

        // ─── 3. 逾期检测 ───
        int overdueCount = 0;
        var borrowing = borrowMapper.selectList(
                new LambdaQueryWrapper<BorrowRecord>()
                        .eq(BorrowRecord::getStatus, "BORROWING")
                        .lt(BorrowRecord::getEndTime, LocalDateTime.now()));
        for (BorrowRecord br : borrowing) {
            long days = java.time.Duration.between(br.getEndTime(), LocalDateTime.now()).toDays();
            br.setStatus("OVERDUE");
            br.setOverdueDays((int) days);
            borrowMapper.updateById(br);
            // V3: 同步更新设备借还状态为逾期
            Device device = deviceMapper.selectById(br.getDeviceId());
            if (device != null && device.getBorrowStatus() != null && device.getBorrowStatus() == 2) {
                device.setBorrowStatus(4); // 逾期
                deviceMapper.updateById(device);
            }
            notificationService.notifyOverdue(br.getUserId(), "设备#" + br.getDeviceId(), br.getId(), (int) days);
            overdueCount++;
        }
        log.info("逾期处理: {} 条", overdueCount);

        // ─── 4. 归还提醒 ───
        int remindCount = 0;
        LocalDateTime tomorrowStart = LocalDateTime.now().plusDays(1).withHour(0).withMinute(0).withSecond(0);
        LocalDateTime tomorrowEnd = tomorrowStart.plusDays(1);
        var dueSoon = borrowMapper.selectList(
                new LambdaQueryWrapper<BorrowRecord>()
                        .eq(BorrowRecord::getStatus, "BORROWING")
                        .ge(BorrowRecord::getEndTime, tomorrowStart)
                        .lt(BorrowRecord::getEndTime, tomorrowEnd));
        for (BorrowRecord br : dueSoon) {
            notificationService.notifyReturnReminder(br.getUserId(), "设备#" + br.getDeviceId(), br.getId());
            remindCount++;
        }
        log.info("归还提醒: {} 条", remindCount);

        log.info("========== 定时清理任务结束（共清理 {} 条记录 + {} 个附件）==========",
                deleted + logDeleted + approvalDeleted, attDeleted);
    }
}
