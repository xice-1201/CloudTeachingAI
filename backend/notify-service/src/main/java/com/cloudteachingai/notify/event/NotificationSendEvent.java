package com.cloudteachingai.notify.event;

import com.cloudteachingai.notify.entity.Notification;
import lombok.Data;

@Data
public class NotificationSendEvent {

    private Long userId;
    private Notification.NotificationType type;
    private String title;
    private String content;
    private String targetType;
    private Long targetId;
    private String targetUrl;
}
