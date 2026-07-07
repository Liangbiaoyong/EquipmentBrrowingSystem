package com.gzhu.equipment.service.impl;

import com.gzhu.equipment.entity.Notification;
import com.gzhu.equipment.mapper.NotificationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock private NotificationMapper notificationMapper;

    private NotificationServiceImpl notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationServiceImpl(notificationMapper);
    }

    @Test @DisplayName("send → 插入通知")
    void send_shouldInsertNotification() {
        notificationService.send(1L, "标题", "内容", "SYSTEM");
        verify(notificationMapper).insert(any(Notification.class));
    }

    @Test @DisplayName("unreadCount → 返回未读数")
    void unreadCount_shouldReturnCount() {
        when(notificationMapper.selectCount(any())).thenReturn(5L);
        int count = notificationService.unreadCount(1L);
        assertThat(count).isEqualTo(5);
    }

    @Test @DisplayName("listByUser → 返回通知列表")
    void listByUser_shouldReturnList() {
        when(notificationMapper.selectList(any())).thenReturn(List.of(new Notification()));
        var list = notificationService.listByUser(1L, 1, 20);
        assertThat(list).hasSize(1);
    }

    @Test @DisplayName("markRead → 更新为已读")
    void markRead_shouldUpdate() {
        when(notificationMapper.selectById(1L)).thenReturn(new Notification());
        notificationService.markRead(1L);
        verify(notificationMapper).updateById(any());
    }

    @Test @DisplayName("markAllRead → 查询未读并逐条更新")
    void markAllRead_shouldUpdateAll() {
        Notification n = new Notification(); n.setId(1L);
        when(notificationMapper.selectList(any())).thenReturn(List.of(n));
        notificationService.markAllRead(1L);
        verify(notificationMapper).updateById(n);
    }
}
