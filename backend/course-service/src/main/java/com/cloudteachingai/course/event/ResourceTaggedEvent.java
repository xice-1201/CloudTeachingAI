package com.cloudteachingai.course.event;

import lombok.Builder;

import java.util.List;

@Builder
public record ResourceTaggedEvent(
        Long resourceId,
        Long chapterId,
        Long courseId,
        Long teacherId,
        String title,
        String storageKey,
        String taggingStatus,
        String taggingUpdatedAt,
        List<ResourceTaggedKnowledgePointEvent> knowledgePoints
) {
}
