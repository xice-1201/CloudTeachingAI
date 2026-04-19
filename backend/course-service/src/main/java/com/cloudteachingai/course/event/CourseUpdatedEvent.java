package com.cloudteachingai.course.event;

import lombok.Builder;

@Builder
public record CourseUpdatedEvent(
        Long courseId,
        Long teacherId,
        String title,
        String status,
        String visibilityType,
        String changeType,
        String updatedAt
) {
}
