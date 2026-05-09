package com.cloudteachingai.user.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CreateNotificationRequest {
    private Long userId;
    private String type;
    private String title;
    private String content;
    private String targetType;
    private Long targetId;
    private String targetUrl;

    public CreateNotificationRequest(Long userId, String type, String title, String content) {
        this.userId = userId;
        this.type = type;
        this.title = title;
        this.content = content;
    }

    public CreateNotificationRequest(
            Long userId,
            String type,
            String title,
            String content,
            String targetType,
            Long targetId,
            String targetUrl) {
        this(userId, type, title, content);
        this.targetType = targetType;
        this.targetId = targetId;
        this.targetUrl = targetUrl;
    }
}
