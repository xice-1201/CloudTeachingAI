package com.cloudteachingai.course.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class KnowledgeGraphEdgeResponse {
    Long source;
    Long target;
    String relation;
}
