package com.cloudteachingai.learn.event;

import lombok.Builder;

@Builder
public record NotificationSendEvent(
        Long userId,
        String type,
        String title,
        String content
) {
}
