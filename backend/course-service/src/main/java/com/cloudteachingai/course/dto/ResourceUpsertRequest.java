package com.cloudteachingai.course.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ResourceUpsertRequest {
    @NotBlank
    private String title;

    @NotBlank
    private String type;

    private String url;

    private String description;
    private Integer duration;
    private Long size;
    private Integer orderIndex;
}
