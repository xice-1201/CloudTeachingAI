package com.cloudteachingai.learn.client;

import lombok.Data;

@Data
public class CourseApiResponse<T> {
    private Integer code;
    private String message;
    private T data;
}
