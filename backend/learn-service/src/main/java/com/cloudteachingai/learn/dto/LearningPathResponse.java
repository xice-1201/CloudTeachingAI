package com.cloudteachingai.learn.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class LearningPathResponse {
    Long studentId;
    String generatedAt;
    List<LearningPathFocusResponse> focusKnowledgePoints;
    List<LearningPathResourceResponse> resources;
}
