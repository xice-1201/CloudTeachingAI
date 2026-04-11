package com.cloudteachingai.notify.websocket;

import com.cloudteachingai.notify.service.NotificationPushService;
import com.cloudteachingai.notify.util.JwtUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationWebSocketHandler extends TextWebSocketHandler {

    private final JwtUtil jwtUtil;
    private final NotificationPushService notificationPushService;
    private final ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Optional<String> tokenOptional = extractToken(session.getUri());
        if (tokenOptional.isEmpty()) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("Missing token"));
            return;
        }
        String token = tokenOptional.get();

        if (!jwtUtil.validateToken(token)) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("Invalid token"));
            return;
        }

        Long userId = jwtUtil.getUserIdFromToken(token);
        notificationPushService.register(userId, session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode json = objectMapper.readTree(message.getPayload());
        if ("ping".equalsIgnoreCase(json.path("type").asText())) {
            notificationPushService.sendPong(session);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.warn("WebSocket transport error: sessionId={}", session.getId(), exception);
        notificationPushService.unregister(session);
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        notificationPushService.unregister(session);
        log.info("WebSocket closed: sessionId={}, status={}", session.getId(), status);
    }

    private Optional<String> extractToken(URI uri) {
        if (uri == null || uri.getQuery() == null || uri.getQuery().isBlank()) {
            return Optional.empty();
        }

        return Arrays.stream(uri.getQuery().split("&"))
                .map(part -> part.split("=", 2))
                .filter(parts -> parts.length == 2)
                .filter(parts -> "token".equals(parts[0]))
                .map(parts -> URLDecoder.decode(parts[1], StandardCharsets.UTF_8))
                .findFirst();
    }
}
