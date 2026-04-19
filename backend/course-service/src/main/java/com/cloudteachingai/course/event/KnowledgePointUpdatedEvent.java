package com.cloudteachingai.course.event;

import lombok.Builder;

@Builder
public record KnowledgePointUpdatedEvent(
        Long knowledgePointId,
        Long parentId,
        String name,
        String nodeType,
        boolean active,
        String updatedAt
) {
}
