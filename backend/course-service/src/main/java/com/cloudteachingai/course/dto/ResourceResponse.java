package com.cloudteachingai.course.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResourceResponse {
    private Long id;
    private Long chapterId;
    private String title;
    private String type;
    private String url;
    private String sourceUrl;
    private String description;
    private Boolean managedFile;
    private String taggingStatus;
    private String taggingUpdatedAt;
    private java.util.List<ResourceKnowledgePointResponse> knowledgePoints;
    private java.util.List<ResourceTagResponse> tags;
    private Integer duration;
    private Long size;
    private Integer orderIndex;
    private String createdAt;
    private java.util.List<ExerciseQuestionResponse> exerciseQuestions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExerciseQuestionResponse {
        private String id;
        private String stem;
        private java.util.List<ExerciseOptionResponse> options;
        private String answer;
        private String explanation;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExerciseOptionResponse {
        private String id;
        private String text;
    }
}
