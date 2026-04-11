package com.cloudteachingai.course.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ResourceUpsertRequest {
    @NotBlank
    private String title;

    @NotBlank
    private String type;

    @NotBlank
    private String url;

    private Integer duration;
    private Long size;
    private Integer orderIndex;
}
