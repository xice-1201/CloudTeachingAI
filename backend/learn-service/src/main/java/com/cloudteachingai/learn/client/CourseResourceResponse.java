package com.cloudteachingai.learn.client;

import lombok.Data;

@Data
public class CourseResourceResponse {
    private Long id;
    private Long chapterId;
    private String title;
    private String type;
    private String url;
    private Integer duration;
    private Long size;
    private Integer orderIndex;
    private String createdAt;
}
