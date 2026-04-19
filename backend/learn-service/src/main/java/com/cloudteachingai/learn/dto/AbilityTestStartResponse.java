package com.cloudteachingai.learn.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AbilityTestStartResponse {
    Long sessionId;
    String rootKnowledgePointName;
    Integer totalQuestions;
    AbilityTestQuestionResponse question;
}
