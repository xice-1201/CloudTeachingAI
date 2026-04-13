package com.cloudteachingai.assign.client;

import lombok.Data;

import java.util.List;

@Data
public class CoursePageResponse<T> {
    private List<T> items;
    private int total;
    private int page;
    private int pageSize;
}
