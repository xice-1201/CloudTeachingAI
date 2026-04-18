package com.cloudteachingai.course.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgePointNodeResponse {
    private Long id;
    private Long parentId;
    private String name;
    private String description;
    private String keywords;
    private String nodeType;
    private Boolean active;
    private Integer orderIndex;
    private String path;
    private List<KnowledgePointNodeResponse> children;
}
