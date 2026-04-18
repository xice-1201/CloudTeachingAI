package com.cloudteachingai.course.dto;

import lombok.Data;

import java.util.List;

@Data
public class ResourceTagConfirmRequest {
    private List<Long> knowledgePointIds;
}
