package com.cloudteachingai.course.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class KnowledgeGraphResponse {
    Long rootId;
    String rootName;
    String rootPath;
    Integer totalKnowledgePoints;
    Integer totalResourceRelations;
    Integer coveredKnowledgePoints;
    List<KnowledgeGraphNodeResponse> nodes;
    List<KnowledgeGraphEdgeResponse> edges;
}
