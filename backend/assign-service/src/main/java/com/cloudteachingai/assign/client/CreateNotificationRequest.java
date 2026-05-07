package com.cloudteachingai.assign.client;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CreateNotificationRequest {
    private Long userId;
    private String type;
    private String title;
    private String content;
    private String targetType;
    private Long targetId;
    private String targetUrl;
}
