package com.cloudteachingai.course.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResourceTagSuggestionResponse {
    private String label;
    private String kind;
    private Long knowledgePointId;
    private String knowledgePointName;
    private String path;
    private Long suggestedParentKnowledgePointId;
    private String suggestedParentKnowledgePointName;
    private String suggestedParentKnowledgePointPath;
    private String suggestedNodeType;
    private Double confidence;
    private String reason;
}
