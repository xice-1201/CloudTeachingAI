package com.cloudteachingai.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateAdminAuditLogRequest {

    private Long actorId;

    @NotBlank(message = "action must not be blank")
    @Size(max = 80, message = "action length must be less than 80")
    private String action;

    @NotBlank(message = "targetType must not be blank")
    @Size(max = 80, message = "targetType length must be less than 80")
    private String targetType;

    private Long targetId;

    @Size(max = 255, message = "targetName length must be less than 255")
    private String targetName;

    private String detail;
}
