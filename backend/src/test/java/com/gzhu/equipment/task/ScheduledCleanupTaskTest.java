package com.gzhu.equipment.task;

import com.gzhu.equipment.mapper.ApprovalLogMapper;
import com.gzhu.equipment.mapper.AttachmentMapper;
import com.gzhu.equipment.mapper.BorrowRecordMapper;
import com.gzhu.equipment.mapper.DeviceMapper;
import com.gzhu.equipment.mapper.NotificationMapper;
import com.gzhu.equipment.mapper.SysLogMapper;
import com.gzhu.equipment.service.MinioFileService;
import com.gzhu.equipment.service.NotificationService;
import com.gzhu.equipment.service.SystemConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduledCleanupTaskTest {

    @Mock private NotificationMapper notificationMapper;
    @Mock private SysLogMapper sysLogMapper;
    @Mock private ApprovalLogMapper approvalLogMapper;
    @Mock private AttachmentMapper attachmentMapper;
    @Mock private BorrowRecordMapper borrowRecordMapper;
    @Mock private DeviceMapper deviceMapper;
    @Mock private NotificationService notificationService;
    @Mock private SystemConfigService configService;
    @Mock private MinioFileService minioFileService;

    private ScheduledCleanupTask task;

    @BeforeEach
    void setUp() {
        task = new ScheduledCleanupTask(
                notificationMapper,
                sysLogMapper,
                approvalLogMapper,
                attachmentMapper,
                borrowRecordMapper,
                deviceMapper,
                notificationService,
                configService,
                minioFileService);
    }

    private void stubCommonConfigAndEmptyData() {
        when(configService.getIntValue("cleanup.small_record_days", 15)).thenReturn(15);
        when(configService.getIntValue("cleanup.large_file_days", 30)).thenReturn(30);
        when(attachmentMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(borrowRecordMapper.selectList(any())).thenReturn(Collections.emptyList());
    }

    @Test
    @DisplayName("默认配置：只清理超过180天的已读通知，未读通知永久保留")
    void execute_withDefaultConfig_shouldDeleteReadOnly() {
        stubCommonConfigAndEmptyData();
        when(configService.getIntValue("notification.read_cleanup_days", 180)).thenReturn(180);
        when(configService.getIntValue("notification.unread_cleanup_days", -1)).thenReturn(-1);

        task.execute();

        verify(notificationMapper, times(1)).delete(any());
    }

    @Test
    @DisplayName("已读/未读都设置为永久保留时，不清理任何通知")
    void execute_whenBothNotificationRetentionIsForever_shouldNotDeleteNotifications() {
        stubCommonConfigAndEmptyData();
        when(configService.getIntValue("notification.read_cleanup_days", 180)).thenReturn(-1);
        when(configService.getIntValue("notification.unread_cleanup_days", -1)).thenReturn(-1);

        task.execute();

        verify(notificationMapper, never()).delete(any());
    }

    @Test
    @DisplayName("已读保留180天，未读保留30天时，分别执行两次通知清理")
    void execute_whenUnreadRetentionConfigured_shouldDeleteReadAndUnread() {
        stubCommonConfigAndEmptyData();
        when(configService.getIntValue("notification.read_cleanup_days", 180)).thenReturn(180);
        when(configService.getIntValue("notification.unread_cleanup_days", -1)).thenReturn(30);

        task.execute();

        verify(notificationMapper, times(2)).delete(any());
    }
}
