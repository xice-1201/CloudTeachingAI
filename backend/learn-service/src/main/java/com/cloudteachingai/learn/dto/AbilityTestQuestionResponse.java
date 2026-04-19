package com.cloudteachingai.learn.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class AbilityTestQuestionResponse {
    Long id;
    Long knowledgePointId;
    String knowledgePointName;
    Integer orderIndex;
    Integer totalQuestions;
    String content;
    List<AbilityTestQuestionOptionResponse> options;
}
