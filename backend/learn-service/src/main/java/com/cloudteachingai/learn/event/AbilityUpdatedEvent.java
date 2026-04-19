package com.cloudteachingai.learn.event;

import lombok.Builder;

import java.util.List;

@Builder
public record AbilityUpdatedEvent(
        Long studentId,
        Long sessionId,
        List<Long> knowledgePointIds,
        String updatedAt
) {
}
