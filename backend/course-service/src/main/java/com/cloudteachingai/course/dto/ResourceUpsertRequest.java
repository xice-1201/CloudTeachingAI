package com.cloudteachingai.course.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class ResourceUpsertRequest {
    @NotBlank
    private String title;

    @NotBlank
    private String type;

    private String url;

    private String description;
    private List<Long> knowledgePointIds;
    private List<String> tagLabels;
    private Integer duration;
    private Long size;
    private Integer orderIndex;
    private List<ExerciseQuestionRequest> exerciseQuestions;

    @Data
    public static class ExerciseQuestionRequest {
        private String id;
        private String stem;
        private List<ExerciseOptionRequest> options;
        private String answer;
        private String explanation;
    }

    @Data
    public static class ExerciseOptionRequest {
        private String id;
        private String text;
    }
}
