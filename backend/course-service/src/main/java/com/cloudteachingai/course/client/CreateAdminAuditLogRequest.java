package com.cloudteachingai.course.client;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CreateAdminAuditLogRequest {
    private Long actorId;
    private String action;
    private String targetType;
    private Long targetId;
    private String targetName;
    private String detail;
}
