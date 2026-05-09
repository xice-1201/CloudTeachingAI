package com.cloudteachingai.course.dto;

import lombok.Data;

import java.util.List;

@Data
public class ExerciseGenerateRequest {
    private String title;
    private String description;
    private List<String> tagLabels;
    private Integer questionCount;
}
