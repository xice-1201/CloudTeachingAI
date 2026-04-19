package com.cloudteachingai.learn.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AbilityTestAnswerRequest {

    @NotNull
    private Long questionId;

    @NotBlank
    private String answer;
}
