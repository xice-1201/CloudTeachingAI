package com.cloudteachingai.assign.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignmentUpsertRequest {

    @NotBlank
    private String title;

    @NotBlank
    private String description;

    private String gradingCriteria;

    private String submitType;

    @NotNull
    @DecimalMin("0.0")
    private Double maxScore;

    @NotNull
    @Future
    private OffsetDateTime dueDate;
}
