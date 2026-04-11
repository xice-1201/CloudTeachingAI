package com.cloudteachingai.course.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChapterUpsertRequest {
    @NotBlank
    private String title;

    private String description;

    private Integer orderIndex;
}
