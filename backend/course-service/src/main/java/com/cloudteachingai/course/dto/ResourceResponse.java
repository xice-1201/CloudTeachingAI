package com.cloudteachingai.course.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResourceResponse {
    private Long id;
    private Long chapterId;
    private String title;
    private String type;
    private String url;
    private String sourceUrl;
    private String description;
    private Boolean managedFile;
    private Integer duration;
    private Long size;
    private Integer orderIndex;
    private String createdAt;
}
