package com.cloudteachingai.learn.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AbilityTestQuestionOptionResponse {
    String key;
    String text;
}
