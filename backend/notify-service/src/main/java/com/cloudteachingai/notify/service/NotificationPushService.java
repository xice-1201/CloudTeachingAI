package com.cloudteachingai.notify.service;

import com.cloudteachingai.notify.dto.NotificationResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationPushService {

    private final ObjectMapper objectMapper;
    private final Map<Long, Set<WebSocketSession>> sessionsByUserId = new ConcurrentHashMap<>();
    private final Map<String, Long> sessionUserIds = new ConcurrentHashMap<>();

    public void register(Long userId, WebSocketSession session) {
        sessionsByUserId.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet()).add(session);
        sessionUserIds.put(session.getId(), userId);
        log.info("WebSocket session registered: userId={}, sessionId={}", userId, session.getId());
    }

    public void unregister(WebSocketSession session) {
        Long userId = sessionUserIds.remove(session.getId());
        if (userId == null) {
            return;
        }

        Set<WebSocketSession> sessions = sessionsByUserId.get(userId);
        if (sessions == null) {
            return;
        }

        sessions.remove(session);
        if (sessions.isEmpty()) {
            sessionsByUserId.remove(userId);
        }
        log.info("WebSocket session removed: userId={}, sessionId={}", userId, session.getId());
    }

    public void sendPong(WebSocketSession session) throws IOException {
        session.sendMessage(new TextMessage("{\"type\":\"pong\"}"));
    }

    public void push(NotificationResponse notification) {
        Set<WebSocketSession> sessions = sessionsByUserId.get(notification.getUserId());
        if (sessions == null || sessions.isEmpty()) {
            return;
        }

        String payload;
        try {
            payload = objectMapper.writeValueAsString(notification);
        } catch (Exception e) {
            log.error("Failed to serialize notification {}", notification.getId(), e);
            return;
        }

        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                unregister(session);
                continue;
            }
            try {
                session.sendMessage(new TextMessage(payload));
            } catch (IOException e) {
                log.warn("Failed to push notification {} to session {}", notification.getId(), session.getId(), e);
                unregister(session);
            }
        }
    }
}
