package com.cloudteachingai.learn.client;

import lombok.Data;

@Data
public class CourseResourceKnowledgePointResponse {
    private Long id;
    private String name;
    private String nodeType;
    private String path;
    private Double confidence;
    private String source;
}
