package com.cloudteachingai.learn.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class AbilityTestAnswerResponse {
    Long sessionId;
    Integer answeredCount;
    Integer totalQuestions;
    Boolean completed;
    AbilityTestQuestionResponse nextQuestion;
    List<AbilityMapResponse> abilityMap;
}
