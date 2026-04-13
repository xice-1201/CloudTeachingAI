package com.cloudteachingai.learn.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseProgressResponse {
    private Long courseId;
    private Double progress;
    private Integer totalResources;
    private Integer completedResources;
    private String lastLearnedAt;
}
