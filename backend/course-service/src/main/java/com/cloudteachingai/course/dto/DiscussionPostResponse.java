package com.cloudteachingai.course.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DiscussionPostResponse {
    private Long id;
    private Long courseId;
    private Long resourceId;
    private Long parentId;
    private Long authorId;
    private String authorName;
    private String title;
    private String content;
    private String createdAt;
    private String updatedAt;
    private List<DiscussionPostResponse> replies;
}
