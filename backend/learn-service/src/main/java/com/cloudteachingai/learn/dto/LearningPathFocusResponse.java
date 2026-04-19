package com.cloudteachingai.learn.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class LearningPathFocusResponse {
    Long knowledgePointId;
    String knowledgePointName;
    String knowledgePointPath;
    Double masteryLevel;
}
