package com.cloudteachingai.course.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InternalResourceTaggingContextResponse {

    private Long resourceId;
    private Long chapterId;
    private Long courseId;
    private Long teacherId;
    private String title;
    private String description;
    private String type;
    private String storageKey;
}
