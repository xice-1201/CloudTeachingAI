package com.cloudteachingai.user.dto;

import com.cloudteachingai.user.entity.AdminAuditLog;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminAuditLogResponse {

    private Long id;
    private Long actorId;
    private String actorName;
    private String action;
    private String targetType;
    private Long targetId;
    private String targetName;
    private String detail;
    private LocalDateTime createdAt;

    public static AdminAuditLogResponse from(AdminAuditLog log) {
        return AdminAuditLogResponse.builder()
                .id(log.getId())
                .actorId(log.getActorId())
                .actorName(log.getActorName())
                .action(log.getAction())
                .targetType(log.getTargetType())
                .targetId(log.getTargetId())
                .targetName(log.getTargetName())
                .detail(log.getDetail())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
