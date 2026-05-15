package com.cloudteachingai.learn.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AbilityTestStartRequest {

    @NotNull
    private Long knowledgePointId;

    @Min(1)
    @Max(20)
    private Integer questionLimit;
}
