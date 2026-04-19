package com.cloudteachingai.course.event;

import lombok.Builder;

import java.util.List;

@Builder
public record ResourceTaggedEvent(
        Long resourceId,
        Long chapterId,
        Long courseId,
        String title,
        String taggingStatus,
        String taggingUpdatedAt,
        List<Long> knowledgePointIds
) {
}
