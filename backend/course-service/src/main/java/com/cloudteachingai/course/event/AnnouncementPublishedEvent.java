package com.cloudteachingai.course.event;

import lombok.Builder;

@Builder
public record AnnouncementPublishedEvent(
        Long announcementId,
        Long courseId,
        Long authorId,
        String courseTitle,
        String title,
        boolean pinned,
        String publishedAt
) {
}
