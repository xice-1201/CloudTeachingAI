package com.cloudteachingai.learn.client;

import lombok.Data;

import java.util.List;

@Data
public class PageResponse<T> {
    private List<T> items;
    private Long total;
    private Integer page;
    private Integer pageSize;
}
