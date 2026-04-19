package com.cloudteachingai.course.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DiscussionPostUpsertRequest {

    private Long resourceId;

    private Long parentId;

    @Size(max = 255)
    private String title;

    @NotBlank
    private String content;
}
