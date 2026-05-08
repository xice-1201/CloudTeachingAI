package com.cloudteachingai.learn.client;

import lombok.Data;

@Data
public class CourseResourceTagResponse {
    private Long id;
    private String label;
    private Double confidence;
    private String source;
    private Long knowledgePointId;
    private String knowledgePointPath;
}
