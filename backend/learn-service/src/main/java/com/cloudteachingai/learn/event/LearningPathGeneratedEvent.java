package com.cloudteachingai.learn.event;

import lombok.Builder;

import java.util.List;

@Builder
public record LearningPathGeneratedEvent(
        Long studentId,
        String generatedAt,
        List<Long> focusKnowledgePointIds,
        List<Long> resourceIds
) {
}
