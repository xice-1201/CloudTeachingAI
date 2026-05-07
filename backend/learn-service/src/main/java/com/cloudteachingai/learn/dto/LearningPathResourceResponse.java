package com.cloudteachingai.learn.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class LearningPathResourceResponse {
    Long resourceId;
    Long courseId;
    Long chapterId;
    String resourceTitle;
    String chapterTitle;
    String courseTitle;
    String reason;
    Integer orderIndex;
    Double currentProgress;
    String learningStatus;
    String statusLabel;
    String actionLabel;
    Double recommendationScore;
    Long focusKnowledgePointId;
    String focusKnowledgePointName;
}
