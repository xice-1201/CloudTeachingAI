package com.cloudteachingai.learn.client;

import lombok.Data;

@Data
public class CourseSummaryResponse {
    private Long id;
    private String title;
    private String description;
    private String coverImage;
    private Long teacherId;
    private String teacherName;
    private String status;
    private String visibilityType;
    private String createdAt;
    private String updatedAt;
}
