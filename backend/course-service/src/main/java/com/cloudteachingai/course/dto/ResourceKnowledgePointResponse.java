package com.cloudteachingai.course.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResourceKnowledgePointResponse {
    private Long id;
    private String name;
    private String nodeType;
    private String path;
    private Double confidence;
    private String source;
}
