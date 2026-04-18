package com.cloudteachingai.course.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class CourseUpsertRequest {
    @NotBlank
    private String title;

    @NotBlank
    private String description;

    private String coverImage;

    private String visibilityType;

    private List<Long> visibleStudentIds;
}
