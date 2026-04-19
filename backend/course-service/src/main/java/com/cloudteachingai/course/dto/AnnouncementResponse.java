package com.cloudteachingai.course.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AnnouncementResponse {
    private Long id;
    private Long courseId;
    private Long authorId;
    private String authorName;
    private String title;
    private String content;
    private Boolean pinned;
    private String publishedAt;
    private String createdAt;
    private String updatedAt;
}
