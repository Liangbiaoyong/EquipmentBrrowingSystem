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
import java.util.concurrent.CopyOnWriteArraySet;

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

    private static final CopyOnWriteArraySet<NotificationWebSocket> connections = new CopyOnWriteArraySet<>();
    private static final Map<Long, Session> userSessions = new ConcurrentHashMap<>();
    private Session session;
    private Long userId;

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
        this.session = session;
        this.userId = jwtTokenProvider.getUserId(token);
        connections.add(this);
        userSessions.put(userId, session);
        log.info("WebSocket连接: userId={}", userId);
    }

    @OnClose
    public void onClose() {
        connections.remove(this);
        if (userId != null) userSessions.remove(userId);
        log.info("WebSocket断开: userId={}", userId);
    }

    @OnError
    public void onError(Throwable t) {
        log.warn("WebSocket异常: userId={} msg={}", userId, t.getMessage());
    }

    /**
     * 向指定用户推送通知
     */
    public static void pushToUser(Long userId, String type, String title, String content) {
        Session s = userSessions.get(userId);
        if (s != null && s.isOpen()) {
            try {
                Map<String, String> msg = Map.of("type", type, "title", title, "content", content);
                s.getBasicRemote().sendText(objectMapper.writeValueAsString(msg));
            } catch (IOException e) {
                log.warn("WebSocket推送失败: userId={}", userId);
            }
        }
    }

    /**
     * 广播给所有在线用户
     */
    public static void broadcast(String type, String title, String content) {
        for (NotificationWebSocket ws : connections) {
            if (ws.session != null && ws.session.isOpen()) {
                try {
                    Map<String, String> msg = Map.of("type", type, "title", title, "content", content);
                    ws.session.getBasicRemote().sendText(objectMapper.writeValueAsString(msg));
                } catch (IOException e) {
                    log.warn("WebSocket广播失败");
                }
            }
        }
    }
}
