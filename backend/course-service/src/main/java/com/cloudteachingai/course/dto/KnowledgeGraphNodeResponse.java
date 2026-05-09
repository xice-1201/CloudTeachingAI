package com.cloudteachingai.course.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class KnowledgeGraphNodeResponse {
    Long id;
    Long parentId;
    String name;
    String path;
    String nodeType;
    Boolean active;
    Integer depth;
    Integer directResourceCount;
    Integer resourceCount;
    String coverageLevel;
    String color;
}
