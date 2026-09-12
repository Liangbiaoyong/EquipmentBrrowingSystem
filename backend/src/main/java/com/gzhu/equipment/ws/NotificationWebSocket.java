package com.gzhu.equipment.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gzhu.equipment.security.JwtTokenProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 实时通知推送
 *
 * 连接方式：ws://localhost:8080/api/v1/ws/notification?token=&lt;JWT&gt;
 *
 * 服务端推送 JSON：{"type":"APPROVAL","title":"借用申请已通过","content":"..."}
 */
@Slf4j
@Component
@ServerEndpoint("/ws/notification")
public class NotificationWebSocket {

    /** userId → 当前活跃会话 */
    private static final Map<Long, Session> userSessions = new ConcurrentHashMap<>();

    /** Session 属性键：登录用户ID */
    private static final String ATTR_USER_ID = "wsUserId";

    private static JwtTokenProvider jwtTokenProvider;

    @Autowired
    public void setJwtTokenProvider(JwtTokenProvider provider) {
        NotificationWebSocket.jwtTokenProvider = provider;
    }

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @OnOpen
    public void onOpen(Session session) {
        String token = null;
        Map<String, List<String>> params = session.getRequestParameterMap();
        if (params != null && params.get("token") != null && !params.get("token").isEmpty()) {
            token = params.get("token").get(0);
        }
        if (token == null || token.isEmpty() || jwtTokenProvider == null || !jwtTokenProvider.validateToken(token)) {
            log.warn("WebSocket连接被拒绝: 缺少或无效的token");
            try {
                session.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, "unauthorized"));
            } catch (IOException ignored) { }
            return;
        }
        Long uid = jwtTokenProvider.getUserId(token);
        // 本类是 Spring 单例，会话状态必须挂在 Session 上，不能用实例字段
        session.getUserProperties().put(ATTR_USER_ID, uid);
        Session previous = userSessions.put(uid, session);
        if (previous != null && previous != session && previous.isOpen()) {
            try {
                previous.close(new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, "replaced"));
            } catch (IOException ignored) { }
        }
        log.info("WebSocket连接: userId={}", uid);
    }

    @OnClose
    public void onClose(Session session) {
        Object v = session.getUserProperties().get(ATTR_USER_ID);
        if (v instanceof Long uid) {
            // 两参 remove：仅当仍映射到本会话时才删除，避免误删同一用户后来的连接
            userSessions.remove(uid, session);
            log.info("WebSocket断开: userId={}", uid);
        }
    }

    @OnError
    public void onError(Throwable t) {
        log.warn("WebSocket异常: msg={}", t.getMessage());
    }

    /** 向指定用户推送通知 */
    public static void pushToUser(Long userId, String type, String title, String content) {
        Session s = userSessions.get(userId);
        if (s != null && s.isOpen()) {
            send(s, type, title, content);
        }
    }

    /** 广播给所有在线用户 */
    public static void broadcast(String type, String title, String content) {
        for (Session s : userSessions.values()) {
            if (s != null && s.isOpen()) {
                send(s, type, title, content);
            }
        }
    }

    private static void send(Session s, String type, String title, String content) {
        try {
            Map<String, String> msg = Map.of("type", type, "title", title, "content", content);
            String json = objectMapper.writeValueAsString(msg);
            // JSR-356 不允许并发调用 getBasicRemote()
            synchronized (s) {
                s.getBasicRemote().sendText(json);
            }
        } catch (IOException e) {
            log.warn("WebSocket推送失败: msg={}", e.getMessage());
        }
    }
}