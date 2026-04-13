package com.cloudteachingai.assign.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionReviewRequest {

    @NotNull
    @DecimalMin("0.0")
    private Double score;

    @NotBlank
    private String feedback;
}
