package com.cloudteachingai.learn.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AbilityMapResponse {
    Long knowledgePointId;
    String knowledgePointName;
    String knowledgePointPath;
    Double masteryLevel;
    Double confidence;
    Double testScore;
    Double progressScore;
    Integer resourceCount;
    String source;
    String lastTestedAt;
}
