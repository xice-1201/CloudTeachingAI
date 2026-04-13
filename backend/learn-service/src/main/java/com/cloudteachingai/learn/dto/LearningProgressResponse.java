package com.cloudteachingai.learn.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LearningProgressResponse {
    private Long resourceId;
    private Double progress;
    private Integer lastPosition;
    private Boolean completed;
    private String lastAccessedAt;
}
