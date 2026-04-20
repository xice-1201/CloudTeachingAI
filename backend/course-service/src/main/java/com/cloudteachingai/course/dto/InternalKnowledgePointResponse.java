package com.cloudteachingai.course.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InternalKnowledgePointResponse {

    private Long id;
    private Long parentId;
    private String name;
    private String description;
    private String keywords;
    private String nodeType;
    private String path;
}
