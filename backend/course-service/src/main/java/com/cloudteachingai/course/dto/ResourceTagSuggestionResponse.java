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
    private Long knowledgePointId;
    private String knowledgePointName;
    private String path;
    private Double confidence;
    private String reason;
}
