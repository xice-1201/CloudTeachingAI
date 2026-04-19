package com.cloudteachingai.learn.client;

import lombok.Data;

import java.util.List;

@Data
public class CourseKnowledgePointNodeResponse {
    private Long id;
    private Long parentId;
    private String name;
    private String description;
    private String keywords;
    private String nodeType;
    private Boolean active;
    private Integer orderIndex;
    private String path;
    private List<CourseKnowledgePointNodeResponse> children;
}
