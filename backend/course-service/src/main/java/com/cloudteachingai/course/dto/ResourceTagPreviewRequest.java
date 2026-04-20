package com.cloudteachingai.course.dto;

import lombok.Data;

@Data
public class ResourceTagPreviewRequest {
    private String title;
    private String description;
    private String type;
    private String sourceUrl;
    private String fileName;
}
