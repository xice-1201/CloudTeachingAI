package com.cloudteachingai.learn.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TeacherKnowledgePointAnalyticsResponse {
    Long knowledgePointId;
    String knowledgePointName;
    String knowledgePointPath;
    Double averageProgress;
    Integer activeStudents;
    Integer relatedResources;
}
