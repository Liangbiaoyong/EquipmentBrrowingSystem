package com.gzhu.equipment.controller;

import com.gzhu.equipment.common.R;
import com.gzhu.equipment.entity.Notification;
import com.gzhu.equipment.security.JwtUserPrincipal;
import com.gzhu.equipment.service.NotificationService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
@Api(tags = "消息通知")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @ApiOperation("我的通知列表")
    @PreAuthorize("hasAuthority('notification:view')")
    public R<List<Notification>> list(@RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        return R.ok(notificationService.listByUser(getUserId(), page, size));
    }

    @GetMapping("/unread-count")
    @ApiOperation("未读通知数")
    @PreAuthorize("hasAuthority('notification:view')")
    public R<Map<String, Integer>> unreadCount() {
        return R.ok(Map.of("unreadCount", notificationService.unreadCount(getUserId())));
    }

    @PutMapping("/{id}/read")
    @ApiOperation("标记单条已读")
    @PreAuthorize("hasAuthority('notification:view')")
    public R<Void> markRead(@PathVariable Long id) {
        notificationService.markRead(id);
        return R.ok();
    }

    @PutMapping("/read-all")
    @ApiOperation("全部标为已读")
    @PreAuthorize("hasAuthority('notification:view')")
    public R<Void> markAllRead() {
        notificationService.markAllRead(getUserId());
        return R.ok();
    }

    private Long getUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof JwtUserPrincipal p) return p.getUserId();
        throw new IllegalStateException("未登录");
    }
}
