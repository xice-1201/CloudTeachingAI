package com.cloudteachingai.course.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CourseUpsertRequest {
    @NotBlank
    private String title;

    @NotBlank
    private String description;

    private String coverImage;
}
