package com.cloudteachingai.user.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CreateNotificationRequest {
    private Long userId;
    private String type;
    private String title;
    private String content;
}
