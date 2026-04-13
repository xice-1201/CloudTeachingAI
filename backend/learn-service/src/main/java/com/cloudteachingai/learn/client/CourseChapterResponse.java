package com.cloudteachingai.learn.client;

import lombok.Data;

@Data
public class CourseChapterResponse {
    private Long id;
    private Long courseId;
    private String title;
    private String description;
    private Integer orderIndex;
    private String createdAt;
}
