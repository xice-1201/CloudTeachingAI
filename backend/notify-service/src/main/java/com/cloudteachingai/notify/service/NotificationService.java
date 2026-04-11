package com.cloudteachingai.notify.service;

import com.cloudteachingai.notify.dto.CreateNotificationRequest;
import com.cloudteachingai.notify.dto.NotificationResponse;
import com.cloudteachingai.notify.dto.PageResponse;
import com.cloudteachingai.notify.entity.Notification;
import com.cloudteachingai.notify.exception.BusinessException;
import com.cloudteachingai.notify.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationPushService notificationPushService;

    public PageResponse<NotificationResponse> listNotifications(Long userId, int page, int pageSize, Boolean read) {
        Pageable pageable = PageRequest.of(page - 1, pageSize);
        Page<Notification> result = read == null
                ? notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                : notificationRepository.findByUserIdAndReadOrderByCreatedAtDesc(userId, read, pageable);

        List<NotificationResponse> items = result.getContent().stream()
                .map(NotificationResponse::from)
                .toList();

        return new PageResponse<>(items, (int) result.getTotalElements(), page, pageSize);
    }

    public long getUnreadCount(Long userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    @Transactional
    public void markAsRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> BusinessException.notFound("Notification not found"));

        if (!notification.isRead()) {
            notification.setRead(true);
            notificationRepository.save(notification);
        }
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        notificationRepository.markAllAsRead(userId);
    }

    @Transactional
    public NotificationResponse createNotification(CreateNotificationRequest request) {
        Notification notification = Notification.builder()
                .userId(request.getUserId())
                .type(request.getType())
                .title(request.getTitle())
                .content(request.getContent())
                .read(false)
                .build();

        Notification saved = notificationRepository.save(notification);
        NotificationResponse response = NotificationResponse.from(saved);
        notificationPushService.push(response);

        log.info("Notification created: id={}, userId={}, type={}", saved.getId(), saved.getUserId(), saved.getType());
        return response;
    }
}
