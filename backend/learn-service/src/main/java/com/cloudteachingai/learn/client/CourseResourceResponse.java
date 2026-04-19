package com.cloudteachingai.learn.client;

import lombok.Data;

@Data
public class CourseResourceResponse {
    private Long id;
    private Long chapterId;
    private String title;
    private String type;
    private String url;
    private String description;
    private String taggingStatus;
    private java.util.List<CourseResourceKnowledgePointResponse> knowledgePoints;
    private Integer duration;
    private Long size;
    private Integer orderIndex;
    private String createdAt;
}
