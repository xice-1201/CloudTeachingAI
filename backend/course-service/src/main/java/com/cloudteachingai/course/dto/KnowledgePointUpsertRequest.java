package com.cloudteachingai.course.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class KnowledgePointUpsertRequest {

    private Long parentId;

    @NotBlank
    private String name;

    private String description;

    private String keywords;

    @NotBlank
    private String nodeType;

    private Boolean active;

    @NotNull
    private Integer orderIndex;
}
