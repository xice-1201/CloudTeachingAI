package com.cloudteachingai.learn.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateLearningProgressRequest {

    @NotNull
    private Long courseId;

    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private Double progress;

    private Integer lastPosition;
}
