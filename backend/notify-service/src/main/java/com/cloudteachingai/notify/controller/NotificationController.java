package com.cloudteachingai.notify.controller;

import com.cloudteachingai.notify.dto.ApiResponse;
import com.cloudteachingai.notify.dto.CreateNotificationRequest;
import com.cloudteachingai.notify.dto.NotificationResponse;
import com.cloudteachingai.notify.dto.PageResponse;
import com.cloudteachingai.notify.dto.UnreadCountResponse;
import com.cloudteachingai.notify.exception.BusinessException;
import com.cloudteachingai.notify.service.NotificationService;
import com.cloudteachingai.notify.util.JwtUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final JwtUtil jwtUtil;

    @GetMapping("/notifications")
    public ApiResponse<PageResponse<NotificationResponse>> listNotifications(
            @RequestHeader("Authorization") String authorization,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Boolean read) {
        Long userId = extractUserId(authorization);
        return ApiResponse.success(notificationService.listNotifications(userId, page, pageSize, read));
    }

    @GetMapping("/notifications/unread-count")
    public ApiResponse<UnreadCountResponse> getUnreadCount(@RequestHeader("Authorization") String authorization) {
        Long userId = extractUserId(authorization);
        return ApiResponse.success(new UnreadCountResponse(notificationService.getUnreadCount(userId)));
    }

    @PutMapping("/notifications/{id}/read")
    public ApiResponse<Void> markAsRead(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long id) {
        Long userId = extractUserId(authorization);
        notificationService.markAsRead(userId, id);
        return ApiResponse.success(null);
    }

    @PutMapping("/notifications/read-all")
    public ApiResponse<Void> markAllAsRead(@RequestHeader("Authorization") String authorization) {
        Long userId = extractUserId(authorization);
        notificationService.markAllAsRead(userId);
        return ApiResponse.success(null);
    }

    @PostMapping("/internal/notifications")
    public ApiResponse<NotificationResponse> createNotification(@Valid @RequestBody CreateNotificationRequest request) {
        return ApiResponse.success(notificationService.createNotification(request));
    }

    @DeleteMapping("/internal/users/{userId}/notifications")
    public ApiResponse<Void> deleteNotificationsForUser(@PathVariable Long userId) {
        notificationService.deleteNotificationsForUser(userId);
        return ApiResponse.success(null);
    }

    private Long extractUserId(String authorization) {
        if (authorization == null || authorization.isBlank() || !authorization.startsWith("Bearer ")) {
            throw BusinessException.unauthorized("Missing or invalid Authorization header");
        }

        String token = authorization.substring(7);
        if (!jwtUtil.validateToken(token)) {
            throw BusinessException.unauthorized("Invalid token");
        }

        return jwtUtil.getUserIdFromToken(token);
    }
}
