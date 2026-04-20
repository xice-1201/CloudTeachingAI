package com.cloudteachingai.course.event;

import lombok.Builder;

@Builder
public record ResourceUploadedEvent(
        Long resourceId,
        Long chapterId,
        Long courseId,
        Long teacherId,
        String title,
        String description,
        String type,
        String storageKey
) {
}
