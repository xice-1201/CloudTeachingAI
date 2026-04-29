package com.cloudteachingai.course.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResourceTagResponse {
    private Long id;
    private String label;
    private Double confidence;
    private String source;
    private Long knowledgePointId;
    private String knowledgePointPath;
}
