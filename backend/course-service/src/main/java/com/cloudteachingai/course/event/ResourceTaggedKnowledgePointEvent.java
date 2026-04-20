package com.cloudteachingai.course.event;

import lombok.Builder;

@Builder
public record ResourceTaggedKnowledgePointEvent(
        Long knowledgePointId,
        Double confidence,
        String reason
) {
}
