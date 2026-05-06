package com.cloudteachingai.user.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ServiceHealthResponse {
    private String key;
    private String name;
    private String endpoint;
    private String status;
    private Integer httpStatus;
    private Long responseTimeMs;
    private String checkedAt;
    private String message;
}
